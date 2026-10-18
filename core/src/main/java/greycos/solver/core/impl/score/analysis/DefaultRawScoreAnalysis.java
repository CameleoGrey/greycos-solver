package greycos.solver.core.impl.score.analysis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.MatchOccurrenceAnalysis;
import greycos.solver.core.api.score.analysis.RawScoreAnalysis;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class DefaultRawScoreAnalysis<Score_ extends Score<Score_>>
    implements RawScoreAnalysis<Score_> {

  private final List<MatchOccurrenceAnalysis<Score_>> matchOccurrences;
  private final boolean originalsFetched;
  private final SequencedMap<ConstraintRef, List<MatchOccurrenceAnalysis<Score_>>> constraintMap;
  private final SequencedMap<Object, List<MatchOccurrenceAnalysis<Score_>>> indictmentMap;
  private final @Nullable SequencedMap<Object, List<MatchOccurrenceAnalysis<Score_>>>
      originalIndictmentMap;

  public DefaultRawScoreAnalysis(
      List<MatchOccurrenceAnalysis<Score_>> matchOccurrences, boolean originalsFetched) {
    this.matchOccurrences = List.copyOf(matchOccurrences);
    this.originalsFetched = originalsFetched;
    var constraints = new LinkedHashMap<ConstraintRef, List<MatchOccurrenceAnalysis<Score_>>>();
    var terminal = new LinkedHashMap<Object, List<MatchOccurrenceAnalysis<Score_>>>();
    var original =
        originalsFetched
            ? new LinkedHashMap<Object, List<MatchOccurrenceAnalysis<Score_>>>()
            : null;
    var seen =
        Collections.newSetFromMap(new IdentityHashMap<MatchOccurrenceAnalysis<Score_>, Boolean>());
    for (var occurrence : this.matchOccurrences) {
      validate(occurrence, originalsFetched);
      if (!seen.add(occurrence)) {
        throw new IllegalArgumentException(
            "A canonical raw occurrence instance appears more than once. Use distinct instances for distinct occurrences.");
      }
      constraints
          .computeIfAbsent(occurrence.constraintRef(), ignored -> new ArrayList<>())
          .add(occurrence);
      indexObjects(terminal, occurrence, occurrence.indictedObjects());
      if (original != null) {
        indexObjects(
            original, occurrence, Objects.requireNonNull(occurrence.originalIndictedObjects()));
      }
    }
    constraintMap = freeze(constraints);
    indictmentMap = freeze(terminal);
    originalIndictmentMap = original == null ? null : freeze(original);
  }

  private static void validate(MatchOccurrenceAnalysis<?> occurrence, boolean originalsFetched) {
    Objects.requireNonNull(occurrence.constraintRef(), "constraintRef");
    Objects.requireNonNull(occurrence.matchScore(), "matchScore");
    Objects.requireNonNull(occurrence.justification(), "justification");
    if (occurrence.occurrenceSign() != 1 && occurrence.occurrenceSign() != -1) {
      throw new IllegalArgumentException(
          "The occurrenceSign (%d) must be +1 or -1.".formatted(occurrence.occurrenceSign()));
    }
    if ((occurrence.originalIndictedObjects() != null) != originalsFetched) {
      throw new IllegalArgumentException(
          "The raw occurrence's original-source availability differs from originalsFetched (%s)."
              .formatted(originalsFetched));
    }
  }

  private static <Score_ extends Score<Score_>> void indexObjects(
      Map<Object, List<MatchOccurrenceAnalysis<Score_>>> target,
      MatchOccurrenceAnalysis<Score_> occurrence,
      List<Object> objects) {
    switch (objects.size()) {
      case 0 -> {
        // No objects to index.
      }
      case 1 -> indexObject(target, occurrence, objects.getFirst());
      case 2 -> {
        var first = Objects.requireNonNull(objects.get(0), "indictedObject");
        var second = Objects.requireNonNull(objects.get(1), "indictedObject");
        indexObject(target, occurrence, first);
        if (!second.equals(first)) {
          indexObject(target, occurrence, second);
        }
      }
      default -> {
        for (var object : new LinkedHashSet<>(objects)) {
          indexObject(target, occurrence, object);
        }
      }
    }
  }

  private static <Score_ extends Score<Score_>> void indexObject(
      Map<Object, List<MatchOccurrenceAnalysis<Score_>>> target,
      MatchOccurrenceAnalysis<Score_> occurrence,
      Object object) {
    Objects.requireNonNull(object, "indictedObject");
    target.computeIfAbsent(object, ignored -> new ArrayList<>()).add(occurrence);
  }

  private static <Key_, Score_ extends Score<Score_>>
      SequencedMap<Key_, List<MatchOccurrenceAnalysis<Score_>>> freeze(
          LinkedHashMap<Key_, List<MatchOccurrenceAnalysis<Score_>>> map) {
    map.replaceAll((key, occurrences) -> List.copyOf(occurrences));
    return Collections.unmodifiableSequencedMap(map);
  }

  @Override
  public List<MatchOccurrenceAnalysis<Score_>> matchOccurrences() {
    return matchOccurrences;
  }

  @Override
  public boolean originalsFetched() {
    return originalsFetched;
  }

  @Override
  public SequencedMap<ConstraintRef, List<MatchOccurrenceAnalysis<Score_>>> constraintMap() {
    return constraintMap;
  }

  @Override
  public SequencedMap<Object, List<MatchOccurrenceAnalysis<Score_>>> indictmentMap() {
    return indictmentMap;
  }

  @Override
  public @Nullable SequencedMap<Object, List<MatchOccurrenceAnalysis<Score_>>>
      originalIndictmentMap() {
    return originalIndictmentMap;
  }

  /** Differences signed occurrence multisets without assigning identities across snapshots. */
  public static <Score_ extends Score<Score_>> @Nullable DefaultRawScoreAnalysis<Score_> diff(
      @Nullable RawScoreAnalysis<Score_> left, @Nullable RawScoreAnalysis<Score_> right) {
    if ((left == null) != (right == null)) {
      throw new IllegalStateException(
          "One score analysis provides raw match occurrences, while the other does not.");
    }
    if (left == null) {
      return null;
    }
    Objects.requireNonNull(right);
    if (left.originalsFetched() != right.originalsFetched()) {
      throw new IllegalStateException(
          "One raw score analysis provides original contributors, while the other does not.");
    }
    var buckets = new LinkedHashMap<OccurrenceKey, ArrayDeque<Pending<Score_>>>();
    var encounterOrder = new ArrayList<Pending<Score_>>();
    for (var occurrence : left.matchOccurrences()) {
      cancelOrAppend(
          buckets,
          encounterOrder,
          occurrence,
          occurrence.occurrenceSign(),
          left.originalsFetched());
    }
    for (var occurrence : right.matchOccurrences()) {
      cancelOrAppend(
          buckets,
          encounterOrder,
          occurrence,
          -occurrence.occurrenceSign(),
          right.originalsFetched());
    }
    var result = new ArrayList<MatchOccurrenceAnalysis<Score_>>();
    for (var pending : encounterOrder) {
      if (!pending.canceled) {
        var occurrence = pending.occurrence;
        result.add(
            pending.sign == occurrence.occurrenceSign()
                ? occurrence
                : new DefaultMatchOccurrenceAnalysis<>(
                    occurrence.constraintRef(),
                    occurrence.matchScore(),
                    occurrence.justification(),
                    pending.sign,
                    occurrence.indictedObjects(),
                    occurrence.originalIndictedObjects()));
      }
    }
    return new DefaultRawScoreAnalysis<>(result, left.originalsFetched());
  }

  private static <Score_ extends Score<Score_>> void cancelOrAppend(
      Map<OccurrenceKey, ArrayDeque<Pending<Score_>>> buckets,
      List<Pending<Score_>> encounterOrder,
      MatchOccurrenceAnalysis<Score_> occurrence,
      int sign,
      boolean originalsFetched) {
    validate(occurrence, originalsFetched);
    var key =
        new OccurrenceKey(
            occurrence.constraintRef(),
            occurrence.matchScore(),
            occurrence.justification(),
            bag(occurrence.indictedObjects()),
            occurrence.originalIndictedObjects() == null
                ? null
                : bag(occurrence.originalIndictedObjects()));
    var queue = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
    if (!queue.isEmpty() && queue.getFirst().sign != sign) {
      queue.removeFirst().canceled = true;
    } else {
      var pending = new Pending<>(occurrence, sign);
      queue.addLast(pending);
      encounterOrder.add(pending);
    }
  }

  private static Map<Object, Integer> bag(List<Object> objects) {
    var counts = new LinkedHashMap<Object, Integer>();
    for (var object : objects) {
      counts.merge(Objects.requireNonNull(object, "indictedObject"), 1, Math::addExact);
    }
    return counts;
  }

  private record OccurrenceKey(
      ConstraintRef constraintRef,
      Score<?> matchScore,
      ConstraintJustification justification,
      Map<Object, Integer> terminal,
      @Nullable Map<Object, Integer> original) {}

  private static final class Pending<Score_ extends Score<Score_>> {
    private final MatchOccurrenceAnalysis<Score_> occurrence;
    private final int sign;
    private boolean canceled;

    private Pending(MatchOccurrenceAnalysis<Score_> occurrence, int sign) {
      this.occurrence = occurrence;
      this.sign = sign;
    }
  }

  // Container value equality preserves canonical order and multiplicity, while individual rows
  // retain identity semantics. This also supports a value-equal persistence round trip.
  @Override
  public boolean equals(Object other) {
    if (!(other instanceof DefaultRawScoreAnalysis<?> analysis)
        || originalsFetched != analysis.originalsFetched
        || matchOccurrences.size() != analysis.matchOccurrences.size()) {
      return false;
    }
    for (int i = 0; i < matchOccurrences.size(); i++) {
      if (!value(matchOccurrences.get(i)).equals(value(analysis.matchOccurrences.get(i)))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    int hash = Boolean.hashCode(originalsFetched);
    for (var occurrence : matchOccurrences) {
      hash = 31 * hash + value(occurrence).hashCode();
    }
    return hash;
  }

  private static OccurrenceValue value(MatchOccurrenceAnalysis<?> occurrence) {
    return new OccurrenceValue(
        occurrence.constraintRef(),
        occurrence.matchScore(),
        occurrence.justification(),
        occurrence.occurrenceSign(),
        occurrence.indictedObjects(),
        occurrence.originalIndictedObjects());
  }

  private record OccurrenceValue(
      ConstraintRef constraintRef,
      Score<?> score,
      ConstraintJustification justification,
      int sign,
      List<Object> terminal,
      @Nullable List<Object> original) {}
}
