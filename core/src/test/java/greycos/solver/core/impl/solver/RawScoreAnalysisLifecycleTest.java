package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.MatchOccurrenceAnalysis;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.api.solver.IndictmentFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisOptions;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testutil.AbstractMeterTest;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Solver-free assignment replay verifies occurrence associations on each final supplied model. */
@Timeout(30)
class RawScoreAnalysisLifecycleTest extends AbstractMeterTest {
  private static final ScoreAnalysisOptions RAW =
      new ScoreAnalysisOptions(
          ScoreAnalysisFetchPolicy.FETCH_ALL, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL, true);

  static Stream<Arguments> topologies() {
    return Stream.concat(
        Stream.of("NONE", "1", "8").map(workers -> Arguments.of("ordinary", workers)),
        Stream.of("islands", "partitions", "island-partition", "partition-island")
            .map(topology -> Arguments.of(topology, "2")));
  }

  @ParameterizedTest
  @MethodSource("topologies")
  void reusedSolversKeepRawCollectionOptInAndReturnOnlyFinalModelSources(
      String topology, String workers) {
    var factory = SolverFactory.<TestdataSolution>create(config(topology, workers));
    var solver = (DefaultSolver<TestdataSolution>) factory.buildSolver();
    SolutionManager<TestdataSolution, SimpleScore> manager = SolutionManager.create(factory);
    ScoreAnalysis<SimpleScore> previous = null;
    Replay previousReplay = null;
    for (int run = 0; run < 2; run++) {
      var problem = problem();
      Constraints.selectionCalls.set(0);
      var solved = solver.solve(problem);
      var replay = replay(solved);
      assertThat(solved.getScore()).isEqualTo(replay.score);
      assertThat(Constraints.selectionCalls).hasValue(0);
      assertThat(
              solver
                  .getSolverScope()
                  .getScoreDirector()
                  .getConstraintMatchPolicy()
                  .isMatchOccurrenceEnabled())
          .isFalse();
      var legacy = manager.analyze(solved);
      assertThat(legacy.rawAnalysis()).isNull();
      assertThat(Constraints.selectionCalls).hasValue(0);
      var grouped =
          manager.analyzeWithIndictments(solved, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL);
      assertThat(grouped.rawAnalysis()).isNull();
      var raw = manager.analyzeWithOptions(solved, RAW);
      assertAnalysis(raw, replay);
      assertThat(raw.score()).isEqualTo(grouped.score()).isEqualTo(legacy.score());
      assertThat(raw.indictmentMap()).isEqualTo(grouped.indictmentMap());
      assertThat(raw.originalIndictmentMap()).isEqualTo(grouped.originalIndictmentMap());
      for (var source : raw.rawAnalysis().originalIndictmentMap().keySet()) {
        assertThat(replay.sources.contains(source))
            .as("No obsolete worker or migration source escapes")
            .isTrue();
      }
      if (previous != null) {
        assertAnalysis(previous, previousReplay);
        for (var oldSource : previous.rawAnalysis().originalIndictmentMap().keySet()) {
          assertThat(raw.rawAnalysis().originalIndictmentMap().keySet())
              .noneMatch(source -> source == oldSource);
        }
      }
      previous = raw;
      previousReplay = replay;
    }
  }

  private static void assertAnalysis(ScoreAnalysis<SimpleScore> analysis, Replay replay) {
    assertThat(analysis.score()).isEqualTo(replay.score);
    var raw = analysis.rawAnalysis();
    assertThat(raw.originalsFetched()).isTrue();
    assertThat(raw.matchOccurrences()).hasSize(replay.rows.size());
    var canonical =
        Collections.newSetFromMap(
            new IdentityHashMap<MatchOccurrenceAnalysis<SimpleScore>, Boolean>());
    var actualByKey = new LinkedHashMap<RowKey, MatchOccurrenceAnalysis<SimpleScore>>();
    for (var row : raw.matchOccurrences()) {
      assertThat(canonical.add(row)).isTrue();
      var key = new RowKey(row.constraintRef().id(), row.indictedObjects().getFirst());
      var expected = replay.rows.get(key);
      assertThat(expected).isNotNull();
      assertThat(actualByKey.put(key, row)).isNull();
      assertThat(row.matchScore()).isEqualTo(SimpleScore.of(expected.score));
      assertThat(row.score()).isEqualTo(row.matchScore());
      assertThat(row.occurrenceSign()).isEqualTo(1);
      assertThat(row.justification()).isEqualTo(Reason.INSTANCE);
      assertThat(row.indictedObjects()).containsExactlyElementsOf(expected.selected);
      assertThat(row.originalIndictedObjects()).hasSize(expected.originals.size());
      var originalIdentities = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
      originalIdentities.addAll(row.originalIndictedObjects());
      assertThat(originalIdentities).hasSize(expected.originals.size());
      for (var original : expected.originals)
        assertThat(originalIdentities.contains(original)).isTrue();
      if (key.constraint.equals("congestion")) {
        assertThat(row.indictedObjects().getFirst())
            .isEqualTo(row.indictedObjects().getLast())
            .isNotSameAs(row.indictedObjects().getLast());
      }
    }
    assertThat(actualByKey).containsOnlyKeys(replay.rows.keySet());
    var expectedTerminal = new LinkedHashMap<Object, List<RowKey>>();
    var expectedOriginal = new IdentityHashMap<Object, List<RowKey>>();
    var expectedConstraints = new LinkedHashMap<String, List<RowKey>>();
    for (var entry : replay.rows.entrySet()) {
      expectedTerminal
          .computeIfAbsent(entry.getKey().selected, ignored -> new ArrayList<>())
          .add(entry.getKey());
      expectedConstraints
          .computeIfAbsent(entry.getKey().constraint, ignored -> new ArrayList<>())
          .add(entry.getKey());
      for (var source : entry.getValue().originals) {
        expectedOriginal.computeIfAbsent(source, ignored -> new ArrayList<>()).add(entry.getKey());
      }
    }
    assertThat(raw.indictmentMap()).hasSize(expectedTerminal.size());
    expectedTerminal.forEach(
        (key, rows) -> assertCanonicalRows(raw.indictmentMap().get(key), rows, actualByKey));
    assertThat(raw.originalIndictmentMap()).hasSize(expectedOriginal.size());
    expectedOriginal.forEach(
        (key, rows) ->
            assertCanonicalRows(raw.originalIndictmentMap().get(key), rows, actualByKey));
    assertThat(raw.constraintMap()).hasSize(expectedConstraints.size());
    raw.constraintMap()
        .forEach(
            (key, rows) ->
                assertCanonicalRows(rows, expectedConstraints.get(key.id()), actualByKey));
    for (var constraint : analysis.constraintAnalyses()) {
      assertThat(constraint.rawMatchCount())
          .isEqualTo((long) expectedConstraints.get(constraint.constraintId()).size());
      assertThat(constraint.matchCount()).isEqualTo(1);
    }
  }

  private static void assertCanonicalRows(
      List<MatchOccurrenceAnalysis<SimpleScore>> actual,
      List<RowKey> expected,
      Map<RowKey, MatchOccurrenceAnalysis<SimpleScore>> canonical) {
    assertThat(actual).hasSize(expected.size());
    var seen =
        Collections.newSetFromMap(
            new IdentityHashMap<MatchOccurrenceAnalysis<SimpleScore>, Boolean>());
    for (var row : actual) assertThat(seen.add(row)).isTrue();
    for (var key : expected) assertThat(actual).anyMatch(row -> row == canonical.get(key));
  }

  private record RowKey(String constraint, Object selected) {}

  private record ExpectedRow(long score, List<Object> selected, List<Object> originals) {}

  private record Replay(SimpleScore score, Map<RowKey, ExpectedRow> rows, Set<Object> sources) {}

  private static Replay replay(TestdataSolution solution) {
    var rows = new LinkedHashMap<RowKey, ExpectedRow>();
    var groups = new IdentityHashMap<TestdataValue, List<TestdataEntity>>();
    var sources = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    sources.addAll(solution.getEntityList());
    sources.addAll(solution.getValueList());
    long total = 0;
    for (var entity : solution.getEntityList()) {
      var value = entity.getValue();
      assertThat(value).isNotNull();
      assertThat(solution.getValueList()).anyMatch(candidate -> candidate == value);
      long contribution = -Integer.parseInt(value.getCode().substring(1));
      var selected = new Assignment(entity.getCode(), value.getCode());
      rows.put(
          new RowKey("assignment", selected),
          new ExpectedRow(contribution, List.of(selected), List.of(entity, value)));
      groups.computeIfAbsent(value, ignored -> new ArrayList<>()).add(entity);
      total += contribution;
    }
    for (var entry : groups.entrySet()) {
      long count = entry.getValue().size();
      long contribution = -count * count;
      var selected = new Group(entry.getKey().getCode());
      rows.put(
          new RowKey("congestion", selected),
          new ExpectedRow(
              contribution, List.of(selected, selected), new ArrayList<>(entry.getValue())));
      total += contribution;
    }
    return new Replay(SimpleScore.of(total), rows, sources);
  }

  private static SolverConfig config(String topology, String workers) {
    PhaseConfig<?> phase =
        switch (topology) {
          case "ordinary" -> search();
          case "islands" -> islands(search(), workers);
          case "partitions" -> partitions(search());
          case "island-partition" -> islands(partitions(search()), workers);
          case "partition-island" -> partitions(islands(search(), workers));
          default -> throw new IllegalArgumentException(topology);
        };
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withConstraintProviderClass(Constraints.class)
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withRandomSeed(0L)
        .withMoveThreadCount(workers)
        .withPhases(phase);
  }

  private static LocalSearchPhaseConfig search() {
    return new LocalSearchPhaseConfig()
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(4));
  }

  private static IslandModelPhaseConfig islands(PhaseConfig<?> phase, String workers) {
    return new IslandModelPhaseConfig()
        .withIslandCount(2)
        .withMoveThreadCount(workers)
        .withCompareGlobalEnabled(false)
        .withMigrationFrequency(Integer.MAX_VALUE)
        .withPhaseConfigList(List.of(phase));
  }

  private static PartitionedSearchPhaseConfig partitions(PhaseConfig<?> phase) {
    return new PartitionedSearchPhaseConfig()
        .withSolutionPartitionerClass(HalvingPartitioner.class)
        .withPhaseConfigList(List.of(phase));
  }

  private static TestdataSolution problem() {
    var problem = new TestdataSolution("same IDs, independently reconstructed model");
    problem.setValueList(
        new ArrayList<>(
            List.of(new TestdataValue("v1"), new TestdataValue("v2"), new TestdataValue("v3"))));
    var entities = new ArrayList<TestdataEntity>();
    for (int i = 0; i < 4; i++)
      entities.add(new TestdataEntity("e" + i, problem.getValueList().getLast()));
    problem.setEntityList(entities);
    return problem;
  }

  public record Assignment(String entityId, String valueId) {}

  public record Group(String valueId) {}

  public enum Reason implements ConstraintJustification {
    INSTANCE
  }

  public static class Constraints implements ConstraintProvider {
    static final AtomicInteger selectionCalls = new AtomicInteger();

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .join(TestdataValue.class, Joiners.equal(TestdataEntity::getValue, value -> value))
            .penalize(
                SimpleScore.ONE, (entity, value) -> Integer.parseInt(value.getCode().substring(1)))
            .justifyWith((entity, value, score) -> Reason.INSTANCE)
            .indictWith(
                (entity, value) -> {
                  selectionCalls.incrementAndGet();
                  return List.of(new Assignment(entity.getCode(), value.getCode()));
                })
            .asConstraint("assignment"),
        factory
            .forEach(TestdataEntity.class)
            .groupBy(TestdataEntity::getValue, ConstraintCollectors.count())
            .penalize(SimpleScore.ONE, (value, count) -> count * count)
            .justifyWith((value, count, score) -> Reason.INSTANCE)
            .indictWith(
                (value, count) -> {
                  selectionCalls.incrementAndGet();
                  return List.of(new Group(value.getCode()), new Group(value.getCode()));
                })
            .asConstraint("congestion")
      };
    }
  }

  public static class HalvingPartitioner implements SolutionPartitioner<TestdataSolution> {
    @Override
    public List<TestdataSolution> splitWorkingSolution(
        ScoreDirector<TestdataSolution> director, Integer runnablePartThreadLimit) {
      var source = director.getWorkingSolution();
      var result = new ArrayList<TestdataSolution>();
      int width = Math.max(1, (source.getEntityList().size() + 1) / 2);
      for (int offset = 0; offset < source.getEntityList().size(); offset += width) {
        var part = new TestdataSolution();
        part.setValueList(new ArrayList<>(source.getValueList()));
        var entities = new ArrayList<TestdataEntity>();
        for (int i = offset; i < Math.min(offset + width, source.getEntityList().size()); i++) {
          var entity = source.getEntityList().get(i);
          entities.add(new TestdataEntity(entity.getCode(), entity.getValue()));
        }
        part.setEntityList(entities);
        result.add(part);
      }
      return result;
    }
  }
}
