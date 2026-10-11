package greycos.solver.core.api.score.profile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import greycos.solver.core.api.score.stream.ConstraintRef;

import org.jspecify.annotations.Nullable;

/** Validation occurs only while immutable results are constructed, never on the event path. */
final class ConstraintProfileValidation {

  static final Comparator<ConstraintProfileBucket> BUCKET_ORDER =
      Comparator.comparingLong(ConstraintProfileBucket::exclusiveNanos)
          .reversed()
          .thenComparing(
              ConstraintProfileBucket::owners, ConstraintProfileValidation::compareOwners);

  private ConstraintProfileValidation() {}

  static void nonnegative(String name, long value) {
    if (value < 0L) {
      throw new IllegalArgumentException("The " + name + " (" + value + ") must be nonnegative.");
    }
  }

  static void label(String name, String value) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException("The " + name + " (" + value + ") must not be blank.");
    }
  }

  static void counts(long eventCount, long failureCount) {
    nonnegative("eventCount", eventCount);
    nonnegative("failureCount", failureCount);
    if (failureCount > eventCount) {
      throw new IllegalArgumentException(
          "The failureCount ("
              + failureCount
              + ") must not exceed eventCount ("
              + eventCount
              + ").");
    }
  }

  static void measurement(long nanos, long events, long failures, boolean partial) {
    nonnegative("exclusiveNanos", nanos);
    counts(events, failures);
    if (nanos > 0L && events == 0L && !partial) {
      throw new IllegalArgumentException(
          "The exclusiveNanos ("
              + nanos
              + ") with eventCount ("
              + events
              + ") requires partialInvocations (true).");
    }
  }

  static @Nullable Double ratio(long numerator, long denominator) {
    return denominator == 0L ? null : (double) numerator / denominator;
  }

  static void fraction(String name, @Nullable Double value) {
    if (value != null && (!Double.isFinite(value) || value < 0.0 || value > 1.0)) {
      throw new IllegalArgumentException(
          "The " + name + " (" + value + ") must be a finite fraction between 0 and 1, or null.");
    }
  }

  static void derived(String name, @Nullable Double actual, @Nullable Double expected) {
    if (!Objects.equals(actual, expected)) {
      throw new IllegalArgumentException(
          "The " + name + " (" + actual + ") must equal its derived value (" + expected + ").");
    }
  }

  static <T> List<T> sortedCopy(String name, List<T> values, Comparator<? super T> comparator) {
    Objects.requireNonNull(values, name);
    var copy = new ArrayList<T>(values.size());
    for (var value : values) {
      copy.add(Objects.requireNonNull(value, name + " element"));
    }
    copy.sort(comparator);
    return List.copyOf(copy);
  }

  static List<ConstraintRef> owners(List<ConstraintRef> owners) {
    return sortedCopy("owners", owners, Comparator.naturalOrder()).stream().distinct().toList();
  }

  static <T, K> void unique(String name, List<T> values, Function<T, K> keyFunction) {
    var keys = new HashSet<K>();
    for (var value : values) {
      var key = keyFunction.apply(value);
      if (!keys.add(key)) {
        throw new IllegalArgumentException("The " + name + " (" + key + ") must be unique.");
      }
    }
  }

  static void section(
      long measuredNanos,
      long eventCount,
      long failureCount,
      List<ConstraintProfileBucket> buckets) {
    unique("bucket owners", buckets, ConstraintProfileBucket::owners);
    var total = new Totals();
    for (var bucket : buckets) {
      total.add(
          bucket.exclusiveNanos(),
          bucket.eventCount(),
          bucket.failureCount(),
          bucket.partialInvocations());
      derived(
          "measuredShare", bucket.measuredShare(), ratio(bucket.exclusiveNanos(), measuredNanos));
    }
    total.requireCounters("section", measuredNanos, eventCount, failureCount);
  }

  static void unit(List<ConstraintProfileMeasurement> measurements) {
    unique(
        "measurement key",
        measurements,
        row -> new MeasurementKey(row.stage(), row.ownershipEpoch(), row.operation()));
    var ownershipByEpoch = new HashMap<Integer, Ownership>();
    for (var row : measurements) {
      var ownership = new Ownership(row.ownershipState(), row.owners());
      var previous = ownershipByEpoch.putIfAbsent(row.ownershipEpoch(), ownership);
      if (previous != null && !previous.equals(ownership)) {
        throw new IllegalArgumentException(
            "The ownershipEpoch ("
                + row.ownershipEpoch()
                + ") has inconsistent ownership ("
                + previous
                + ") and ("
                + ownership
                + ") within the same unit.");
      }
    }
  }

  static void profile(
      ConstraintProfileSection initialization,
      ConstraintProfileSection search,
      ConstraintProfileSection combined,
      List<ConstraintProfileGeneration> generations,
      List<ConstraintProfileUnit> units) {
    unique("generationId", generations, ConstraintProfileGeneration::generationId);
    unique("unitId", units, ConstraintProfileUnit::unitId);
    var generationById = new HashMap<Long, GenerationIndex>();
    for (var generation : generations) {
      var epochs = new HashMap<Integer, ConstraintProfile.OwnershipState>();
      for (var epoch : generation.ownershipEpochs()) {
        epochs.put(epoch.ownershipEpoch(), epoch.ownershipState());
      }
      var activities = new HashMap<ConstraintRef, ConstraintProfile.Activity>();
      for (var constraint : generation.constraints()) {
        activities.put(constraint.constraintRef(), constraint.activity());
      }
      generationById.put(generation.generationId(), new GenerationIndex(epochs, activities));
    }
    var rowsByStage =
        new EnumMap<ConstraintProfile.Stage, Map<List<ConstraintRef>, Totals>>(
            ConstraintProfile.Stage.class);
    for (var stage : ConstraintProfile.Stage.values()) {
      rowsByStage.put(stage, new HashMap<>());
    }
    for (var unit : units) {
      var generation = generationById.get(unit.generationId());
      if (generation == null) {
        throw new IllegalArgumentException(
            "The unitId ("
                + unit.unitId()
                + ") references missing generationId ("
                + unit.generationId()
                + ").");
      }
      var completeRows = new HashMap<InvocationKey, Totals>();
      for (var row : unit.measurements()) {
        validateReference(unit, row, generation);
        add(
            rowsByStage.get(row.stage()),
            row.owners(),
            row.exclusiveNanos(),
            row.eventCount(),
            row.failureCount(),
            row.partialInvocations());
        completeRows
            .computeIfAbsent(
                new InvocationKey(row.ownershipEpoch(), row.operation()), ignored -> new Totals())
            .add(row.exclusiveNanos(), row.eventCount(), row.failureCount(), false);
      }
      for (var entry : completeRows.entrySet()) {
        var totals = entry.getValue();
        if (totals.nanos > 0L && totals.events == 0L) {
          throw new IllegalArgumentException(
              "The unitId ("
                  + unit.unitId()
                  + ") measurement ("
                  + entry.getKey()
                  + ") has combined exclusiveNanos ("
                  + totals.nanos
                  + ") without an entered invocation.");
        }
      }
    }
    requireBuckets(
        "initialization",
        rowsByStage.get(ConstraintProfile.Stage.INITIALIZATION),
        initialization.buckets(),
        true);
    requireBuckets(
        "search", rowsByStage.get(ConstraintProfile.Stage.SEARCH), search.buckets(), true);
    requireSum(
        "scoringWallNanos",
        combined.scoringWallNanos(),
        initialization.scoringWallNanos(),
        search.scoringWallNanos());
    requireSum(
        "measuredNanos",
        combined.measuredNanos(),
        initialization.measuredNanos(),
        search.measuredNanos());
    requireSum(
        "unmeasuredNanos",
        combined.unmeasuredNanos(),
        initialization.unmeasuredNanos(),
        search.unmeasuredNanos());
    requireSum(
        "eventCount", combined.eventCount(), initialization.eventCount(), search.eventCount());
    requireSum(
        "failureCount",
        combined.failureCount(),
        initialization.failureCount(),
        search.failureCount());
    var combinedBuckets = new HashMap<List<ConstraintRef>, Totals>();
    for (var section : List.of(initialization, search)) {
      for (var bucket : section.buckets()) {
        add(
            combinedBuckets,
            bucket.owners(),
            bucket.exclusiveNanos(),
            bucket.eventCount(),
            bucket.failureCount(),
            false);
      }
    }
    requireBuckets("combined", combinedBuckets, combined.buckets(), false);
  }

  private static void validateReference(
      ConstraintProfileUnit unit, ConstraintProfileMeasurement row, GenerationIndex generation) {
    var state = generation.epochs().get(row.ownershipEpoch());
    if (state != row.ownershipState()) {
      throw new IllegalArgumentException(
          "The unitId ("
              + unit.unitId()
              + ") measurement ownershipEpoch ("
              + row.ownershipEpoch()
              + ") has ownershipState ("
              + row.ownershipState()
              + "), but generationId ("
              + unit.generationId()
              + ") declares ("
              + state
              + ").");
    }
    for (var owner : row.owners()) {
      var activity = generation.activities().get(owner);
      if (activity == null
          || activity == ConstraintProfile.Activity.DISABLED_WEIGHT
          || (row.ownershipState() == ConstraintProfile.OwnershipState.ACTIVE
              && activity != ConstraintProfile.Activity.ACTIVE)) {
        throw new IllegalArgumentException(
            "The unitId ("
                + unit.unitId()
                + ") owner ("
                + owner
                + ") with ownershipState ("
                + row.ownershipState()
                + ") has incompatible activity ("
                + activity
                + ") in generationId ("
                + unit.generationId()
                + ").");
      }
    }
  }

  private static void add(
      Map<List<ConstraintRef>, Totals> totalsByOwners,
      List<ConstraintRef> owners,
      long nanos,
      long events,
      long failures,
      boolean partial) {
    if (nanos != 0L || events != 0L || failures != 0L || partial) {
      totalsByOwners
          .computeIfAbsent(owners, ignored -> new Totals())
          .add(nanos, events, failures, partial);
    }
  }

  private static void requireBuckets(
      String section,
      Map<List<ConstraintRef>, Totals> expected,
      List<ConstraintProfileBucket> buckets,
      boolean preservePartial) {
    var actual = new HashMap<List<ConstraintRef>, Totals>();
    for (var bucket : buckets) {
      if (!preservePartial && bucket.partialInvocations()) {
        throw new IllegalArgumentException(
            "The combined bucket owners ("
                + bucket.owners()
                + ") must have partialInvocations (false).");
      }
      add(
          actual,
          bucket.owners(),
          bucket.exclusiveNanos(),
          bucket.eventCount(),
          bucket.failureCount(),
          bucket.partialInvocations());
    }
    if (!actual.keySet().equals(expected.keySet())) {
      throw new IllegalArgumentException(
          "The "
              + section
              + " bucket owners ("
              + actual.keySet()
              + ") do not match measured owners ("
              + expected.keySet()
              + ").");
    }
    for (var entry : expected.entrySet()) {
      var totals = entry.getValue();
      var other = actual.get(entry.getKey());
      var context = section + " bucket owners " + entry.getKey();
      totals.requireCounters(context, other.nanos, other.events, other.failures);
      if (totals.partial != other.partial) {
        throw new IllegalArgumentException(
            "The "
                + context
                + " partialInvocations ("
                + other.partial
                + ") must match measured value ("
                + totals.partial
                + ").");
      }
    }
  }

  private static void requireSum(String name, long actual, long first, long second) {
    var expected = sum(name, first, second);
    if (actual != expected) {
      throw new IllegalArgumentException(
          "The combined "
              + name
              + " ("
              + actual
              + ") must equal initialization ("
              + first
              + ") plus search ("
              + second
              + "), which is ("
              + expected
              + ").");
    }
  }

  private static long sum(String name, long first, long second) {
    try {
      return Math.addExact(first, second);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(
          "The " + name + " sum of (" + first + ") and (" + second + ") exceeds the long range.",
          exception);
    }
  }

  private static int compareOwners(List<ConstraintRef> first, List<ConstraintRef> second) {
    for (int i = 0; i < Math.min(first.size(), second.size()); i++) {
      var comparison = first.get(i).compareTo(second.get(i));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(first.size(), second.size());
  }

  private record MeasurementKey(
      ConstraintProfile.Stage stage, int epoch, ConstraintProfile.Operation operation) {}

  private record InvocationKey(int epoch, ConstraintProfile.Operation operation) {}

  private record Ownership(ConstraintProfile.OwnershipState state, List<ConstraintRef> owners) {}

  private record GenerationIndex(
      Map<Integer, ConstraintProfile.OwnershipState> epochs,
      Map<ConstraintRef, ConstraintProfile.Activity> activities) {}

  private static final class Totals {
    private long nanos;
    private long events;
    private long failures;
    private boolean partial;

    void add(long nanos, long events, long failures, boolean partial) {
      this.nanos = sum("exclusiveNanos", this.nanos, nanos);
      this.events = sum("eventCount", this.events, events);
      this.failures = sum("failureCount", this.failures, failures);
      this.partial |= partial;
    }

    void requireCounters(String context, long nanos, long events, long failures) {
      if (this.nanos != nanos || this.events != events || this.failures != failures) {
        throw new IllegalArgumentException(
            "The "
                + context
                + " counters (exclusiveNanos="
                + nanos
                + ", eventCount="
                + events
                + ", failureCount="
                + failures
                + ") do not match measured totals (exclusiveNanos="
                + this.nanos
                + ", eventCount="
                + this.events
                + ", failureCount="
                + this.failures
                + ").");
      }
    }
  }
}
