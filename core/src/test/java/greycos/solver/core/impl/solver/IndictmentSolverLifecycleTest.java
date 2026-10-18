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
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.api.solver.IndictmentFetchPolicy;
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

/**
 * Final-result attribution is checked against supplied objects and a solver-free business replay.
 */
@Timeout(30)
class IndictmentSolverLifecycleTest extends AbstractMeterTest {

  static Stream<Arguments> solveModes() {
    return Stream.concat(
        Stream.of("NONE", "1", "2", "8").map(workers -> Arguments.of("ordinary", workers)),
        Stream.of(
                "islands",
                "partitions",
                "nestedIslands",
                "nestedPartitions",
                "islandThenPartition",
                "partitionThenIsland")
            .map(mode -> Arguments.of(mode, "2")));
  }

  @ParameterizedTest
  @MethodSource("solveModes")
  void finalAnalysisUsesOnlySuppliedSourcesAcrossSolverReuse(String mode, String workers) {
    var factory = SolverFactory.<TestdataSolution>create(config(mode, workers));
    var solver = (DefaultSolver<TestdataSolution>) factory.buildSolver();
    SolutionManager<TestdataSolution, SimpleScore> manager = SolutionManager.create(factory);
    ScoreAnalysis<SimpleScore> previousAnalysis = null;
    Replay previousReplay = null;
    for (int run = 0; run < 2; run++) {
      // Reuse IDs, but use entirely new entity and fact instances on the second run.
      var problem = problem();
      Constraints.mappingCalls.set(0);
      var result = solver.solve(problem);
      var replay = replay(result);
      assertThat(result.getScore()).isEqualTo(replay.score());
      assertThat(Constraints.mappingCalls).hasValue(0);
      assertThat(
              solver
                  .getSolverScope()
                  .getScoreDirector()
                  .getConstraintMatchPolicy()
                  .getIndictmentFetchPolicy())
          .isEqualTo(IndictmentFetchPolicy.NONE);

      var ordinaryAnalysis = manager.analyze(result);
      assertThat(ordinaryAnalysis.score()).isEqualTo(replay.score());
      assertThat(ordinaryAnalysis.indictmentMap()).isNull();
      assertThat(ordinaryAnalysis.originalIndictmentMap()).isNull();
      assertThat(Constraints.mappingCalls).hasValue(0);

      var analysis =
          manager.analyzeWithIndictments(result, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL);
      assertAnalysis(result, analysis, replay);
      assertThat(Constraints.mappingCalls.get()).isEqualTo(replay.terminal().size());
      assertThat(result.getScore()).isEqualTo(replay.score());
      for (var entity : result.getEntityList()) {
        assertThat(problem.getEntityList()).noneMatch(input -> input == entity);
      }
      if (previousAnalysis != null) {
        // A new analysis must neither reuse old worker objects nor mutate an earlier snapshot.
        assertMap(previousAnalysis.indictmentMap(), previousReplay.terminal());
        assertMap(previousAnalysis.originalIndictmentMap(), previousReplay.original());
        for (var source : previousAnalysis.originalIndictmentMap().keySet()) {
          assertThat(analysis.originalIndictmentMap().keySet())
              .noneMatch(current -> current == source);
        }
      }
      previousAnalysis = analysis;
      previousReplay = replay;
    }
  }

  private static void assertAnalysis(
      TestdataSolution supplied, ScoreAnalysis<SimpleScore> analysis, Replay replay) {
    assertThat(analysis.score()).isEqualTo(replay.score());
    assertMap(analysis.indictmentMap(), replay.terminal());
    assertMap(analysis.originalIndictmentMap(), replay.original());
    Set<Object> suppliedObjects = Collections.newSetFromMap(new IdentityHashMap<>());
    suppliedObjects.addAll(supplied.getEntityList());
    suppliedObjects.addAll(supplied.getValueList());
    analysis
        .originalIndictmentMap()
        .forEach(
            (source, indictment) -> {
              assertThat(suppliedObjects.contains(source))
                  .as("Original sources must belong to the supplied final solution")
                  .isTrue();
              assertThat(indictment.indictedObject()).isSameAs(source);
            });
    assertThat(analysis.indictmentMap().keySet())
        .allMatch(key -> key instanceof AssignmentId || key instanceof GroupId);
  }

  private static void assertMap(
      Map<Object, IndictmentAnalysis<SimpleScore>> actual, Map<Object, Contribution> expected) {
    assertThat(actual).hasSize(expected.size());
    expected.forEach(
        (key, contribution) -> {
          var indictment = actual.get(key);
          assertThat(indictment).isNotNull();
          assertThat(indictment.score()).isEqualTo(SimpleScore.of(contribution.penalty()));
          assertThat(indictment.matchCount()).isEqualTo(contribution.occurrences());
        });
  }

  private record Contribution(long penalty, long occurrences) {
    Contribution plus(Contribution other) {
      return new Contribution(penalty + other.penalty, occurrences + other.occurrences);
    }
  }

  private record Replay(
      SimpleScore score, Map<Object, Contribution> terminal, Map<Object, Contribution> original) {}

  private static Replay replay(TestdataSolution solution) {
    var terminal = new LinkedHashMap<Object, Contribution>();
    var original = new IdentityHashMap<Object, Contribution>();
    var groups = new IdentityHashMap<TestdataValue, List<TestdataEntity>>();
    long total = 0L;
    for (var entity : solution.getEntityList()) {
      var value = entity.getValue();
      assertThat(value).isNotNull();
      var assignment = new Contribution(-Integer.parseInt(value.getCode().substring(1)), 1L);
      terminal.put(new AssignmentId(entity.getCode(), value.getCode()), assignment);
      original.merge(entity, assignment, Contribution::plus);
      original.merge(value, assignment, Contribution::plus);
      groups.computeIfAbsent(value, ignored -> new ArrayList<>()).add(entity);
      total += assignment.penalty();
    }
    for (var entry : groups.entrySet()) {
      long count = entry.getValue().size();
      var group = new Contribution(-count * count, 1L);
      terminal.put(new GroupId(entry.getKey().getCode()), group);
      for (var entity : entry.getValue()) {
        original.merge(entity, group, Contribution::plus);
      }
      total += group.penalty();
    }
    return new Replay(SimpleScore.of(total), terminal, original);
  }

  private static SolverConfig config(String mode, String workers) {
    PhaseConfig<?> phase =
        switch (mode) {
          case "ordinary" -> search();
          case "islands" -> islands(search(), workers);
          case "partitions" -> partition(search());
          case "nestedIslands" -> islands(islands(search(), workers), workers);
          case "nestedPartitions" -> partition(partition(search()));
          case "islandThenPartition" -> islands(partition(search()), workers);
          case "partitionThenIsland" -> partition(islands(search(), workers));
          default -> throw new IllegalArgumentException(mode);
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

  private static PartitionedSearchPhaseConfig partition(PhaseConfig<?> phase) {
    return new PartitionedSearchPhaseConfig()
        .withSolutionPartitionerClass(HalvingPartitioner.class)
        .withPhaseConfigList(List.of(phase));
  }

  private static TestdataSolution problem() {
    var solution = new TestdataSolution("problem");
    solution.setValueList(
        new ArrayList<>(
            List.of(new TestdataValue("v1"), new TestdataValue("v2"), new TestdataValue("v3"))));
    var entities = new ArrayList<TestdataEntity>();
    for (int i = 0; i < 4; i++) {
      entities.add(new TestdataEntity("e" + i, solution.getValueList().getLast()));
    }
    solution.setEntityList(entities);
    return solution;
  }

  public record AssignmentId(String entityId, String valueId) {}

  public record GroupId(String valueId) {}

  public static final class Constraints implements ConstraintProvider {
    static final AtomicInteger mappingCalls = new AtomicInteger();

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .join(TestdataValue.class, Joiners.equal(TestdataEntity::getValue, value -> value))
            .penalize(
                SimpleScore.ONE, (entity, value) -> Integer.parseInt(value.getCode().substring(1)))
            .indictWith(
                (entity, value) -> {
                  mappingCalls.incrementAndGet();
                  return List.of(new AssignmentId(entity.getCode(), value.getCode()));
                })
            .asConstraint("assignment cost"),
        factory
            .forEach(TestdataEntity.class)
            .groupBy(TestdataEntity::getValue, ConstraintCollectors.count())
            .penalize(SimpleScore.ONE, (value, count) -> count * count)
            .indictWith(
                (value, count) -> {
                  mappingCalls.incrementAndGet();
                  return List.of(new GroupId(value.getCode()));
                })
            .asConstraint("group congestion")
      };
    }
  }

  public static final class HalvingPartitioner implements SolutionPartitioner<TestdataSolution> {
    @Override
    public List<TestdataSolution> splitWorkingSolution(
        ScoreDirector<TestdataSolution> director, Integer runnablePartThreadLimit) {
      var source = director.getWorkingSolution();
      var result = new ArrayList<TestdataSolution>();
      int size = Math.max(1, (source.getEntityList().size() + 1) / 2);
      for (int offset = 0; offset < source.getEntityList().size(); offset += size) {
        var part = new TestdataSolution();
        part.setValueList(new ArrayList<>(source.getValueList()));
        part.setEntityList(
            new ArrayList<>(
                source
                    .getEntityList()
                    .subList(offset, Math.min(offset + size, source.getEntityList().size()))
                    .stream()
                    .map(entity -> new TestdataEntity(entity.getCode(), entity.getValue()))
                    .toList()));
        result.add(part);
      }
      return result;
    }
  }
}
