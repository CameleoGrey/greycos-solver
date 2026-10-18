package greycos.solver.core.impl.score.director.stream;

import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import java.util.function.Supplier;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.MatchOccurrenceAnalysis;
import greycos.solver.core.api.score.analysis.RawScoreAnalysis;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.score.stream.DefaultConstraintJustification;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.declarative.ConsistencyTracker;
import greycos.solver.core.impl.score.analysis.IndictmentMaps;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintSession;

/**
 * Standalone candidate-only cost probe, without timing assertions or additional dependencies.
 *
 * <p>Usage: {@code <NONE|TERMINAL|BOTH|RAW_TERMINAL|RAW_BOTH> <combined|map|group|exists|join>
 * <size> [warmup=2] [iterations=5] [churnCycles=3] [repeatedExtractions=3] [buckets=32]}. NONE
 * keeps legacy FETCH_ALL constraint matching enabled. Each output line beginning with {@code
 * INDICTMENT_ANALYSIS } contains one JSON measurement. Validation and the independent oracle run
 * outside timed regions. Allocation bytes are cumulative thread allocation, never retained heap.
 *
 * <p>First extraction includes lazy constraint matches/totals and indictment maps. Repeated
 * extraction keeps constraint matches cached but invokes getIndictmentMaps again, creating a fresh
 * lineage resolver. Churn replaces source identities while preserving their business values; checks
 * against new sources and a final empty extraction detect stale contributors.
 */
public final class IndictmentAnalysisBenchmark {
  private static final String PREFIX = "INDICTMENT_ANALYSIS ";
  private static final String CORE_LOCATION_JSON =
      jsonString(
          BavetConstraintSession.class
              .getProtectionDomain()
              .getCodeSource()
              .getLocation()
              .toString());
  private static final String JAVA_VERSION_JSON = jsonString(System.getProperty("java.version"));
  private static final com.sun.management.ThreadMXBean ALLOCATION = allocationBean();
  private static volatile long blackhole;

  private IndictmentAnalysisBenchmark() {}

  public static void main(String[] args) {
    if (args.length < 3 || args.length > 8) {
      throw new IllegalArgumentException(
          "Usage: <NONE|TERMINAL|BOTH|RAW_TERMINAL|RAW_BOTH> <combined|map|group|exists|join>"
              + " <size> [warmup=2] [iterations=5] [churnCycles=3] [repeatedExtractions=3] [buckets=32]");
    }
    var options =
        new Options(
            Mode.valueOf(args[0]),
            args[1],
            Integer.parseInt(args[2]),
            argument(args, 3, 2),
            argument(args, 4, 5),
            argument(args, 5, 3),
            argument(args, 6, 3),
            argument(args, 7, 32));
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(BenchmarkSolution.class, DummyEntity.class);
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<BenchmarkSolution, SimpleScore>(
            descriptor, provider(options.workload()), EnvironmentMode.NO_ASSERT);
    for (var iteration = -options.warmup(); iteration < options.iterations(); iteration++) {
      run(factory, options, iteration);
    }
  }

  private static int argument(String[] args, int index, int fallback) {
    return args.length > index ? Integer.parseInt(args[index]) : fallback;
  }

  private static void run(
      BavetConstraintStreamScoreDirectorFactory<BenchmarkSolution, SimpleScore> factory,
      Options options,
      int iteration) {
    var data = new Dataset(options.size(), options.buckets());
    boolean usesWitnesses =
        includes(options.workload(), "exists") || includes(options.workload(), "join");
    int liveInputCount = data.items.length + (usesWitnesses ? data.witnesses.length : 0);
    var construction =
        measure(
            () ->
                factory.newSession(null, new ConsistencyTracker<>(), options.mode().policy, true));
    var session = construction.value();
    record(options, iteration, -1, "session_build", construction, Expected.empty(), null, 0);
    var insertion =
        measure(
            () -> {
              for (var item : data.items) session.insert(item);
              if (usesWitnesses) for (var witness : data.witnesses) session.insert(witness);
              return session.calculateScore();
            });
    var expected = expected(data, options.workload());
    require(insertion.value().score() == expected.score, "Initial score differs from replay.");
    record(options, iteration, -1, "initial_insert", insertion, expected, null, liveInputCount);
    extract(session, options, iteration, -1, "first_extract", expected, liveInputCount);
    for (var repeat = 0; repeat < options.repeatedExtractions(); repeat++) {
      extract(session, options, iteration, repeat, "repeated_extract", expected, liveInputCount);
    }
    var changedCount = Math.max(1, options.size() / 20);
    for (var cycle = 0; cycle < options.churnCycles(); cycle++) {
      int first = (cycle * changedCount) % options.size();
      var update =
          measure(
              () -> {
                for (var offset = 0; offset < changedCount; offset++) {
                  var item = data.items[(first + offset) % data.items.length];
                  item.weight = item.weight % 3 + 1;
                  item.bucket = (item.bucket + 1) % data.buckets;
                  session.update(item);
                }
                return session.calculateScore();
              });
      expected = expected(data, options.workload());
      require(update.value().score() == expected.score, "Updated score differs from replay.");
      record(options, iteration, cycle, "churn_update", update, expected, null, liveInputCount);
      extract(session, options, iteration, cycle, "churn_update_extract", expected, liveInputCount);
      var replacement =
          measure(
              () -> {
                for (var offset = 0; offset < changedCount; offset++) {
                  int index = (first + offset) % data.items.length;
                  var old = data.items[index];
                  session.retract(old);
                  if (usesWitnesses) session.retract(data.witnesses[index]);
                  var item = new Item(old.id, old.bucket, old.weight);
                  data.items[index] = item;
                  session.insert(item);
                  if (usesWitnesses) {
                    var witness = new Witness(old.id);
                    data.witnesses[index] = witness;
                    session.insert(witness);
                  }
                }
                return session.calculateScore();
              });
      expected = expected(data, options.workload());
      require(
          replacement.value().score() == expected.score, "Replacement score differs from replay.");
      record(
          options, iteration, cycle, "churn_replace", replacement, expected, null, liveInputCount);
      extract(
          session, options, iteration, cycle, "churn_replace_extract", expected, liveInputCount);
    }
    var retraction =
        measure(
            () -> {
              for (var item : data.items) session.retract(item);
              if (usesWitnesses) for (var witness : data.witnesses) session.retract(witness);
              return session.calculateScore();
            });
    require(retraction.value().isZero(), "Score remains after removing all inputs.");
    record(options, iteration, -1, "final_retract", retraction, Expected.empty(), null, 0);
    extract(session, options, iteration, -1, "empty_extract", Expected.empty(), 0);
  }

  private static void extract(
      BavetConstraintSession<SimpleScore> session,
      Options options,
      int iteration,
      int cycle,
      String stage,
      Expected expected,
      int liveInputs) {
    var measurement =
        measure(
            () -> {
              var totals = session.getConstraintMatchTotalMap();
              long matches = 0;
              long score = 0;
              for (var total : totals.values()) {
                matches += total.getConstraintMatchCount();
                score += total.getScore().score();
              }
              var details = session.getScoreInliner().getAnalysisDetails();
              return new Extraction(matches, score, details.indictments(), details.rawAnalysis());
            });
    var extraction = measurement.value();
    require(
        extraction.matches() == expected.matches && extraction.score() == expected.score,
        "Materialized constraint matches differ from replay.");
    var maps = extraction.maps();
    verifyView(maps.terminal(), expected.terminal, options.mode() != Mode.NONE, "terminal");
    verifyView(maps.original(), expected.original, options.mode().originals(), "original");
    verifyRaw(extraction.raw(), maps, expected, options.mode());
    blackhole ^= expected.checksum();
    record(options, iteration, cycle, stage, measurement, expected, extraction, liveInputs);
  }

  /** Checks every physical occurrence and the identity shared by all indexes outside timing. */
  private static void verifyRaw(
      RawScoreAnalysis<SimpleScore> raw,
      IndictmentMaps<SimpleScore> maps,
      Expected expected,
      Mode mode) {
    if (!mode.raw()) {
      require(raw == null, "An unrequested raw occurrence table was materialized.");
      return;
    }
    require(
        raw != null && raw.originalsFetched() == mode.originals(), "Wrong raw view availability.");
    require(raw.matchOccurrences().size() == expected.matches, "Wrong raw occurrence count.");
    var unmatched = new HashMap<OccurrenceKey, ArrayDeque<ExpectedOccurrence>>();
    for (var occurrence : expected.occurrences) {
      unmatched
          .computeIfAbsent(occurrence.key(), ignored -> new ArrayDeque<>())
          .addLast(occurrence);
    }
    Set<MatchOccurrenceAnalysis<SimpleScore>> identities =
        Collections.newSetFromMap(new IdentityHashMap<>());
    var constraints =
        new LinkedHashMap<ConstraintRef, List<MatchOccurrenceAnalysis<SimpleScore>>>();
    var terminal = new LinkedHashMap<Object, List<MatchOccurrenceAnalysis<SimpleScore>>>();
    var original = new LinkedHashMap<Object, List<MatchOccurrenceAnalysis<SimpleScore>>>();
    long score = 0;
    for (var occurrence : raw.matchOccurrences()) {
      require(identities.add(occurrence), "A canonical raw occurrence instance was reused.");
      require(
          occurrence.occurrenceSign() == 1, "A regular analysis contains a negative occurrence.");
      require(occurrence.score().equals(occurrence.matchScore()), "Wrong signed occurrence score.");
      var key =
          new OccurrenceKey(
              occurrence.constraintRef().id(),
              occurrence.matchScore().score(),
              occurrence.indictedObjects());
      var candidates = unmatched.get(key);
      require(candidates != null && !candidates.isEmpty(), "Unexpected raw occurrence: " + key);
      var wanted = candidates.removeFirst();
      require(
          occurrence.justification() instanceof DefaultConstraintJustification,
          "Unexpected raw justification type.");
      var justification = (DefaultConstraintJustification) occurrence.justification();
      require(
          justification.getFacts().equals(wanted.key().terminals())
              && justification.<SimpleScore>getImpact().score() == wanted.key().score(),
          "Raw justification differs from the independent occurrence oracle.");
      if (mode.originals()) {
        verifySourceIdentities(occurrence.originalIndictedObjects(), wanted.originals());
      } else {
        require(
            occurrence.originalIndictedObjects() == null, "Unexpected original-source selection.");
      }
      score += occurrence.score().score();
      constraints
          .computeIfAbsent(occurrence.constraintRef(), ignored -> new ArrayList<>())
          .add(occurrence);
      index(terminal, occurrence, occurrence.indictedObjects());
      if (mode.originals()) index(original, occurrence, occurrence.originalIndictedObjects());
    }
    require(
        score == expected.score && unmatched.values().stream().allMatch(ArrayDeque::isEmpty),
        "Raw occurrences omit an independent oracle contribution.");
    verifySharedIndex(raw.constraintMap(), constraints, "constraint");
    verifySharedIndex(raw.indictmentMap(), terminal, "terminal");
    verifyGroupedFromRaw(maps.terminal(), terminal);
    if (mode.originals()) {
      verifySharedIndex(raw.originalIndictmentMap(), original, "original");
      verifyGroupedFromRaw(maps.original(), original);
    } else {
      require(raw.originalIndictmentMap() == null, "Unexpected raw original index.");
    }
  }

  private static void verifySourceIdentities(List<Object> actual, List<Object> expected) {
    require(
        actual != null && actual.size() == expected.size(), "Wrong original source cardinality.");
    Set<Object> remaining = Collections.newSetFromMap(new IdentityHashMap<>());
    remaining.addAll(expected);
    for (var source : actual) {
      require(
          remaining.remove(source),
          "A raw source is duplicated, stale, or not an oracle contributor.");
    }
    require(remaining.isEmpty(), "A raw source was omitted.");
  }

  private static void index(
      Map<Object, List<MatchOccurrenceAnalysis<SimpleScore>>> index,
      MatchOccurrenceAnalysis<SimpleScore> occurrence,
      List<Object> objects) {
    for (var object : new LinkedHashSet<>(objects)) {
      index.computeIfAbsent(object, ignored -> new ArrayList<>()).add(occurrence);
    }
  }

  private static <Key_> void verifySharedIndex(
      Map<Key_, List<MatchOccurrenceAnalysis<SimpleScore>>> actual,
      Map<Key_, List<MatchOccurrenceAnalysis<SimpleScore>>> expected,
      String name) {
    require(
        actual != null && actual.keySet().equals(expected.keySet()),
        "Wrong raw " + name + " index keys.");
    for (var entry : expected.entrySet()) {
      var found = actual.get(entry.getKey());
      var wanted = entry.getValue();
      require(found.size() == wanted.size(), "Wrong raw " + name + " index multiplicity.");
      for (var i = 0; i < wanted.size(); i++) {
        require(
            found.get(i) == wanted.get(i),
            "Raw " + name + " index copied or reordered an occurrence.");
      }
    }
  }

  private static void verifyGroupedFromRaw(
      Map<Object, IndictmentAnalysis<SimpleScore>> grouped,
      Map<Object, List<MatchOccurrenceAnalysis<SimpleScore>>> raw) {
    require(
        grouped != null && grouped.keySet().equals(raw.keySet()),
        "Raw/grouped object keys differ.");
    for (var entry : raw.entrySet()) {
      var groups = new HashMap<GroupKey, Contribution>();
      long total = 0;
      for (var occurrence : entry.getValue()) {
        var contribution =
            groups.computeIfAbsent(
                new GroupKey(occurrence.constraintRef(), occurrence.justification()),
                ignored -> new Contribution());
        contribution.score += occurrence.score().score();
        contribution.matches++;
        total += occurrence.score().score();
      }
      var actual = grouped.get(entry.getKey());
      require(
          actual.score().score() == total
              && actual.matchCount() == entry.getValue().size()
              && actual.matches().size() == groups.size(),
          "Raw/grouped totals differ.");
      for (var group : actual.matches()) {
        var wanted = groups.remove(new GroupKey(group.constraintRef(), group.justification()));
        require(
            wanted != null
                && group.score().score() == wanted.score
                && group.matchCount() == wanted.matches,
            "Raw/grouped justification contributions differ.");
      }
      require(groups.isEmpty(), "A raw justification group was omitted.");
    }
  }

  private static void verifyView(
      SequencedMap<Object, IndictmentAnalysis<SimpleScore>> actual,
      Map<Object, Contribution> expected,
      boolean available,
      String name) {
    if (!available) {
      require(actual == null, "Unexpected " + name + " view.");
      return;
    }
    require(
        actual != null && actual.keySet().equals(expected.keySet()),
        "Stale or missing " + name + " contributor.");
    for (var entry : expected.entrySet()) {
      var found = actual.get(entry.getKey());
      var wanted = entry.getValue();
      require(
          found.score().score() == wanted.score && found.matchCount() == wanted.matches,
          "Incorrect "
              + name
              + " score or occurrence count for "
              + entry.getKey()
              + ": expected score="
              + wanted.score
              + ", count="
              + wanted.matches
              + "; actual score="
              + found.score()
              + ", count="
              + found.matchCount()
              + ", matches="
              + found.matches());
      require(
          found.matches().stream().mapToLong(match -> match.matchCount()).sum() == wanted.matches,
          "Incorrect " + name + " grouped occurrence count.");
    }
  }

  private static ConstraintProvider provider(String workload) {
    return factory -> {
      var constraints = new ArrayList<Constraint>();
      var items = factory.forEach(Item.class);
      if (includes(workload, "map")) {
        constraints.add(
            items
                .map(item -> new Token(item.id, item.weight))
                .penalize(SimpleScore.ONE, Token::weight)
                .asConstraint("map"));
      }
      if (includes(workload, "group")) {
        constraints.add(
            items
                .groupBy(item -> item.bucket, ConstraintCollectors.count())
                .map((bucket, count) -> new GroupTotal(bucket, count))
                .penalize(SimpleScore.ONE, GroupTotal::count)
                .asConstraint("group"));
      }
      if (includes(workload, "exists")) {
        constraints.add(
            items
                .ifExists(Witness.class, Joiners.equal(item -> item.id, witness -> witness.id))
                .penalize(SimpleScore.ONE, item -> item.weight)
                .asConstraint("exists"));
      }
      if (includes(workload, "join")) {
        constraints.add(
            items
                .join(Witness.class, Joiners.equal(item -> item.id, witness -> witness.id))
                .penalize(SimpleScore.ONE, (item, witness) -> item.weight)
                .asConstraint("join"));
      }
      return constraints.toArray(Constraint[]::new);
    };
  }

  private static Expected expected(Dataset data, String workload) {
    var result = Expected.empty();
    var witnesses = new HashMap<Integer, Witness>();
    for (var witness : data.witnesses) witnesses.put(witness.id, witness);
    var groups = new LinkedHashMap<Integer, List<Item>>();
    for (var item : data.items) {
      if (includes(workload, "map")) {
        result.add("map", -item.weight, List.of(new Token(item.id, item.weight)), List.of(item));
      }
      if (includes(workload, "group")) {
        groups.computeIfAbsent(item.bucket, ignored -> new ArrayList<>()).add(item);
      }
      var witness = witnesses.get(item.id);
      if (witness != null && includes(workload, "exists")) {
        // A positive existence output includes all live matching witness sources.
        result.add("exists", -item.weight, List.of(item), List.of(item, witness));
      }
      if (witness != null && includes(workload, "join")) {
        result.add("join", -item.weight, List.of(item, witness), List.of(item, witness));
      }
    }
    groups.forEach(
        (bucket, members) ->
            result.add(
                "group",
                -members.size(),
                List.of(new GroupTotal(bucket, members.size())),
                members));
    return result;
  }

  private static boolean includes(String workload, String component) {
    return workload.equals("combined") || workload.equals(component);
  }

  private static <T> Measurement<T> measure(Supplier<T> action) {
    long collections = gcCount();
    long gcMillis = gcMillis();
    long allocated = allocatedBytes();
    long started = System.nanoTime();
    T value = action.get();
    long elapsed = System.nanoTime() - started;
    long allocation = allocatedBytes();
    return new Measurement<>(
        value,
        elapsed,
        allocated < 0 || allocation < 0 ? -1 : allocation - allocated,
        gcCount() - collections,
        gcMillis() - gcMillis);
  }

  private static void record(
      Options options,
      int iteration,
      int cycle,
      String stage,
      Measurement<?> measurement,
      Expected expected,
      Extraction extraction,
      int liveInputs) {
    if (iteration < 0) return;
    System.out.println(
        PREFIX
            + "{\"schema_version\":1,\"core_location\":"
            + CORE_LOCATION_JSON
            + ",\"java_version\":"
            + JAVA_VERSION_JSON
            + ",\"mode\":\""
            + options.mode()
            + "\",\"workload\":\""
            + options.workload()
            + "\",\"size\":"
            + options.size()
            + ",\"buckets\":"
            + options.buckets()
            + ",\"iteration\":"
            + iteration
            + ",\"cycle\":"
            + cycle
            + ",\"stage\":\""
            + stage
            + "\",\"elapsed_ns\":"
            + measurement.nanos()
            + ",\"allocated_bytes\":"
            + nullable(measurement.allocated())
            + ",\"gc_count\":"
            + measurement.collections()
            + ",\"gc_time_ms\":"
            + measurement.gcMillis()
            + ",\"score\":"
            + expected.score
            + ",\"raw_matches\":"
            + expected.matches
            + ",\"input_operations\":"
            + operationCount(options, stage)
            + ",\"live_input_objects\":"
            + liveInputs
            + ",\"expected_terminal_objects\":"
            + expected.terminal.size()
            + ",\"expected_original_objects\":"
            + expected.original.size()
            + ",\"expected_terminal_memberships\":"
            + memberships(expected.terminal)
            + ",\"expected_original_memberships\":"
            + memberships(expected.original)
            + ",\"actual_terminal_memberships\":"
            + actualMemberships(extraction == null ? null : extraction.maps().terminal())
            + ",\"actual_original_memberships\":"
            + actualMemberships(extraction == null ? null : extraction.maps().original())
            + ",\"actual_raw_matches\":"
            + (extraction == null || extraction.raw() == null
                ? "null"
                : extraction.raw().matchOccurrences().size())
            + ",\"actual_raw_index_references\":"
            + rawIndexReferences(extraction == null ? null : extraction.raw())
            + ",\"checksum\":"
            + expected.checksum()
            + "}");
  }

  private static String jsonString(String value) {
    var result = new StringBuilder(value.length() + 2).append('"');
    for (var index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character == '"' || character == '\\') {
        result.append('\\').append(character);
      } else if (character < 0x20) {
        result.append("\\u");
        for (int shift = 12; shift >= 0; shift -= 4) {
          result.append(Character.forDigit((character >>> shift) & 0xf, 16));
        }
      } else {
        result.append(character);
      }
    }
    return result.append('"').toString();
  }

  private static int operationCount(Options options, String stage) {
    boolean usesWitnesses =
        includes(options.workload(), "exists") || includes(options.workload(), "join");
    int liveInputs = options.size() * (usesWitnesses ? 2 : 1);
    int changed = Math.max(1, options.size() / 20);
    return switch (stage) {
      case "initial_insert", "final_retract" -> liveInputs;
      case "churn_update" -> changed;
      case "churn_replace" -> changed * (usesWitnesses ? 4 : 2);
      default -> 0;
    };
  }

  private static String actualMemberships(Map<Object, IndictmentAnalysis<SimpleScore>> map) {
    return map == null
        ? "null"
        : Long.toString(map.values().stream().mapToLong(IndictmentAnalysis::matchCount).sum());
  }

  private static long memberships(Map<Object, Contribution> map) {
    return map.values().stream().mapToLong(value -> value.matches).sum();
  }

  private static String rawIndexReferences(RawScoreAnalysis<SimpleScore> raw) {
    if (raw == null) return "null";
    long references =
        raw.constraintMap().values().stream().mapToLong(List::size).sum()
            + raw.indictmentMap().values().stream().mapToLong(List::size).sum();
    if (raw.originalIndictmentMap() != null) {
      references += raw.originalIndictmentMap().values().stream().mapToLong(List::size).sum();
    }
    return Long.toString(references);
  }

  private static String nullable(long value) {
    return value < 0 ? "null" : Long.toString(value);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static com.sun.management.ThreadMXBean allocationBean() {
    if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
        && bean.isThreadAllocatedMemorySupported()) {
      try {
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        return bean;
      } catch (UnsupportedOperationException | SecurityException ignored) {
        return null;
      }
    }
    return null;
  }

  private static long allocatedBytes() {
    return ALLOCATION == null
        ? -1
        : ALLOCATION.getThreadAllocatedBytes(Thread.currentThread().threadId());
  }

  private static long gcCount() {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .mapToLong(bean -> Math.max(0, bean.getCollectionCount()))
        .sum();
  }

  private static long gcMillis() {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .mapToLong(bean -> Math.max(0, bean.getCollectionTime()))
        .sum();
  }

  private enum Mode {
    NONE(ConstraintMatchPolicy.ENABLED),
    TERMINAL(ConstraintMatchPolicy.ENABLED_WITH_INDICTMENTS),
    BOTH(ConstraintMatchPolicy.ENABLED_WITH_ORIGINAL_INDICTMENTS),
    RAW_TERMINAL(ConstraintMatchPolicy.ENABLED_WITH_RAW_INDICTMENTS),
    RAW_BOTH(ConstraintMatchPolicy.ENABLED_WITH_RAW_ORIGINAL_INDICTMENTS);
    private final ConstraintMatchPolicy policy;

    Mode(ConstraintMatchPolicy policy) {
      this.policy = policy;
    }

    boolean originals() {
      return this == BOTH || this == RAW_BOTH;
    }

    boolean raw() {
      return this == RAW_TERMINAL || this == RAW_BOTH;
    }
  }

  private record Options(
      Mode mode,
      String workload,
      int size,
      int warmup,
      int iterations,
      int churnCycles,
      int repeatedExtractions,
      int buckets) {
    private Options {
      if (!List.of("combined", "map", "group", "exists", "join").contains(workload)
          || size < 1
          || warmup < 0
          || iterations < 1
          || churnCycles < 0
          || repeatedExtractions < 0
          || buckets < 1) {
        throw new IllegalArgumentException("Invalid workload, size or iteration budget.");
      }
    }
  }

  private record Measurement<T>(
      T value, long nanos, long allocated, long collections, long gcMillis) {}

  private record Extraction(
      long matches,
      long score,
      IndictmentMaps<SimpleScore> maps,
      RawScoreAnalysis<SimpleScore> raw) {}

  private record OccurrenceKey(String constraint, long score, List<?> terminals) {}

  private record ExpectedOccurrence(OccurrenceKey key, List<Object> originals) {}

  private record GroupKey(ConstraintRef constraint, ConstraintJustification justification) {}

  private record Token(int id, int weight) {}

  private record GroupTotal(int bucket, long count) {}

  private static final class Contribution {
    private long score;
    private long matches;
  }

  private static final class Expected {
    private long score;
    private long matches;
    private final Map<Object, Contribution> terminal = new HashMap<>();
    private final Map<Object, Contribution> original = new HashMap<>();
    private final List<ExpectedOccurrence> occurrences = new ArrayList<>();

    static Expected empty() {
      return new Expected();
    }

    void add(String constraint, long impact, List<?> terminals, List<?> originals) {
      score += impact;
      matches++;
      addTo(terminal, terminals, impact);
      addTo(original, originals, impact);
      occurrences.add(
          new ExpectedOccurrence(
              new OccurrenceKey(constraint, impact, List.copyOf(terminals)),
              List.copyOf(originals)));
    }

    private static void addTo(Map<Object, Contribution> target, List<?> keys, long impact) {
      for (var key : keys) {
        var contribution = target.computeIfAbsent(key, ignored -> new Contribution());
        contribution.score += impact;
        contribution.matches++;
      }
    }

    long checksum() {
      return score * 31 + matches * 17 + memberships(terminal) * 13 + memberships(original) * 7;
    }
  }

  private static final class Dataset {
    private final Item[] items;
    private final Witness[] witnesses;
    private final int buckets;

    private Dataset(int size, int buckets) {
      this.buckets = buckets;
      items = new Item[size];
      witnesses = new Witness[size];
      for (var i = 0; i < size; i++) {
        items[i] = new Item(i, i % buckets, 1 + i % 3);
        witnesses[i] = new Witness(i);
      }
    }
  }

  public static final class Item {
    private final int id;
    private int bucket;
    private int weight;

    Item(int id, int bucket, int weight) {
      this.id = id;
      this.bucket = bucket;
      this.weight = weight;
    }
  }

  public static final class Witness {
    private final int id;

    Witness(int id) {
      this.id = id;
    }
  }

  @PlanningEntity
  public static class DummyEntity {
    @PlanningVariable(valueRangeProviderRefs = "values")
    public Integer value;
  }

  @PlanningSolution
  public static class BenchmarkSolution {
    @ProblemFactCollectionProperty public List<Item> items;
    @ProblemFactCollectionProperty public List<Witness> witnesses;

    @ValueRangeProvider(id = "values")
    @ProblemFactCollectionProperty
    public List<Integer> values;

    @PlanningEntityCollectionProperty public List<DummyEntity> entities;
    @PlanningScore public SimpleScore score;
  }
}
