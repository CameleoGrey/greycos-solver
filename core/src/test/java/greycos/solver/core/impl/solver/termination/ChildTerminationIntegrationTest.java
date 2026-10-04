package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationCompositionStyle;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.localsearch.decider.forager.AbstractLocalSearchForager;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Factory-built child solves; every local search has an independent five-step safety bound. */
@Timeout(15)
class ChildTerminationIntegrationTest {
  private static final Map<String, Map<Long, Integer>> COMPLETED_STEPS = new ConcurrentHashMap<>();

  @ParameterizedTest
  @ValueSource(strings = {"step", "unimprovedStep"})
  void serialControlHonorsInheritedPhaseOnlyLimit(String kind) {
    try (var recorder = new Recorder()) {
      var config = config(recorder.search()).withTerminationConfig(limit(kind));
      SolverFactory.<TestdataSolution>create(config)
          .buildSolver()
          .solve(TestdataSolution.generateSolution(3, 2));
      assertThat(recorder.steps()).containsExactly(2);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "step, solver, 1",
    "step, solver, 2",
    "step, partition, 1",
    "step, partition, 2",
    "unimprovedStep, solver, 1",
    "unimprovedStep, solver, 2",
    "unimprovedStep, partition, 1",
    "unimprovedStep, partition, 2"
  })
  void partitionChildrenHonorInheritedPhaseOnlyLimit(String kind, String origin, int phaseCount) {
    try (var recorder = new Recorder()) {
      var partition = new PartitionedSearchPhaseConfig();
      partition.setSolutionPartitionerClass(OneEntityPerPartition.class);
      partition.setRunnablePartThreadLimit("2");
      partition.setPhaseConfigList(
          phaseCount == 1
              ? List.of(recorder.search())
              : List.of(recorder.search(), recorder.search()));
      var config = config(partition);
      if (origin.equals("solver")) {
        config.withTerminationConfig(limit(kind));
      } else {
        partition.withTerminationConfig(limit(kind));
      }
      SolverFactory.<TestdataSolution>create(config)
          .buildSolver()
          .solve(TestdataSolution.generateSolution(3, 2));
      assertThat(recorder.steps()).hasSize(2).containsOnly(phaseCount * 2);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void islandAndWaitsForSearchOnlyConditionBeforeStoppingConstruction(int islandCount) {
    try (var recorder = new Recorder()) {
      var island =
          new IslandModelPhaseConfig()
              .withIslandCount(islandCount)
              .withCompareGlobalEnabled(false)
              .withMigrationFrequency(Integer.MAX_VALUE)
              .withTerminationConfig(expiredTimeAndUnimprovedSteps())
              .withPhaseConfigList(
                  List.of(new ConstructionHeuristicPhaseConfig(), recorder.search()));
      var result =
          SolverFactory.<TestdataSolution>create(config(island))
              .buildSolver()
              .solve(TestdataSolution.generateUninitializedSolution(3, 2));
      assertThat(result.getEntityList())
          .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
      assertThat(recorder.steps()).hasSize(islandCount).containsOnly(2);
    }
  }

  @Test
  void serialAndControlWaitsForSearchOnlyConditionBeforeStoppingConstruction() {
    try (var recorder = new Recorder()) {
      var config =
          config(new ConstructionHeuristicPhaseConfig(), recorder.search())
              .withTerminationConfig(expiredTimeAndUnimprovedSteps());
      var result =
          SolverFactory.<TestdataSolution>create(config)
              .buildSolver()
              .solve(TestdataSolution.generateUninitializedSolution(3, 2));
      assertThat(result.getEntityList())
          .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
      assertThat(recorder.steps()).containsExactly(2);
    }
  }

  private static TerminationConfig expiredTimeAndUnimprovedSteps() {
    return new TerminationConfig()
        .withSpentLimit(Duration.ZERO)
        .withUnimprovedStepCountLimit(2)
        .withTerminationCompositionStyle(TerminationCompositionStyle.AND);
  }

  private static TerminationConfig limit(String kind) {
    return kind.equals("step")
        ? new TerminationConfig().withStepCountLimit(2)
        : new TerminationConfig().withUnimprovedStepCountLimit(2);
  }

  private static SolverConfig config(PhaseConfig<?>... phases) {
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withEasyScoreCalculatorClass(ConstantScoreCalculator.class)
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withMoveThreadCount("NONE")
        .withPhases(phases);
  }

  public static final class ConstantScoreCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static final class OneEntityPerPartition implements SolutionPartitioner<TestdataSolution> {
    @Override
    public List<TestdataSolution> splitWorkingSolution(
        ScoreDirector<TestdataSolution> scoreDirector, Integer runnablePartThreadLimit) {
      var source = scoreDirector.getWorkingSolution();
      return source.getEntityList().stream()
          .map(
              entity -> {
                var copy = new TestdataEntity(entity.getCode(), entity.getValue());
                var partition = new TestdataSolution();
                partition.setEntityList(new ArrayList<>(List.of(copy)));
                partition.setValueList(new ArrayList<>(source.getValueList()));
                return partition;
              })
          .toList();
    }
  }

  /** Equal-score moves are accepted; select one candidate and record completed steps only. */
  public static final class RecordingForager extends AbstractLocalSearchForager<TestdataSolution> {
    private String runId;
    private LocalSearchMoveScope<TestdataSolution> picked;

    public void setRunId(String runId) {
      this.runId = runId;
    }

    @Override
    public void stepStarted(LocalSearchStepScope<TestdataSolution> stepScope) {
      picked = null;
    }

    @Override
    public boolean supportsNeverEndingMoveSelector() {
      return true;
    }

    @Override
    public void addMove(LocalSearchMoveScope<TestdataSolution> moveScope) {
      picked = moveScope;
    }

    @Override
    public boolean isQuitEarly() {
      return picked != null;
    }

    @Override
    public LocalSearchMoveScope<TestdataSolution> pickMove(
        LocalSearchStepScope<TestdataSolution> stepScope) {
      stepScope.setSelectedMoveCount(picked == null ? 0L : 1L);
      stepScope.setAcceptedMoveCount(picked != null && picked.getAccepted() ? 1L : 0L);
      return picked;
    }

    @Override
    public void stepEnded(LocalSearchStepScope<TestdataSolution> stepScope) {
      COMPLETED_STEPS.get(runId).merge(Thread.currentThread().threadId(), 1, Integer::sum);
    }
  }

  private static final class Recorder implements AutoCloseable {
    private final String runId = UUID.randomUUID().toString();

    private Recorder() {
      COMPLETED_STEPS.put(runId, new ConcurrentHashMap<>());
    }

    private LocalSearchPhaseConfig search() {
      return new LocalSearchPhaseConfig()
          .withForagerConfig(
              new LocalSearchForagerConfig()
                  .withForagerClass(RecordingForager.class)
                  .withCustomProperties(Map.of("runId", runId)))
          .withTerminationConfig(new TerminationConfig().withStepCountLimit(5));
    }

    private List<Integer> steps() {
      return List.copyOf(COMPLETED_STEPS.get(runId).values());
    }

    @Override
    public void close() {
      COMPLETED_STEPS.remove(runId);
    }
  }
}
