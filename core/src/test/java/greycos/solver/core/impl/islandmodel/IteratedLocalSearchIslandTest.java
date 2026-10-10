package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchPhase;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchPhaseScope;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchStepScope;
import greycos.solver.core.impl.move.SolutionAssignmentMove;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

@Timeout(30)
@Execution(ExecutionMode.SAME_THREAD)
class IteratedLocalSearchIslandTest {

  @ParameterizedTest
  @EnumSource(IteratedLocalSearchStepScope.Origin.class)
  void globalComparisonCountsPrimitiveStepsAndSchedulesCompleteAssignments(
      IteratedLocalSearchStepScope.Origin origin) {
    var solver = newSolver();
    var scope = solver.getSolverScope();
    var initial = TestdataSolution.generateUninitializedSolution(2, 3);
    initial.getEntityList().forEach(entity -> entity.setValue(initial.getValueList().getFirst()));
    scope.setInitialSolution(initial);
    try (var director = scope.getScoreDirector()) {
      scope.setBestScore(director.calculateScore());
      var migrant = director.cloneWorkingSolution();
      migrant.getEntityList().getFirst().setValue(migrant.getValueList().getLast());
      var migrantScore = new TestdataEasyScoreCalculator().calculateScore(migrant);
      var global = new SharedGlobalState<TestdataSolution>();
      global.tryUpdate(migrant, InnerScore.fullyAssigned(migrantScore));
      var listener =
          new GlobalCompareListener<>(
              global, IslandModelConfig.builder().withReceiveGlobalUpdateFrequency(2).build(), 0);
      var phase = new IteratedLocalSearchPhaseScope<>(scope, 0);
      listener.stepEnded(new IteratedLocalSearchStepScope<>(phase, origin));
      assertThat(scope.hasPendingMove()).isFalse();
      listener.stepEnded(new IteratedLocalSearchStepScope<>(phase, origin));
      var pending = scope.consumePendingMove();
      assertThat(pending).isNotNull();
      assertThat(pending.move()).isInstanceOf(SolutionAssignmentMove.class);
      assertThat(pending.requiresReset()).isTrue();
      director.getMoveDirector().execute(pending.move());
      assertThat(director.calculateScore().raw()).isEqualTo(migrantScore);
      assertThat(director.getWorkingSolution().getEntityList().getFirst().getValue().getCode())
          .isEqualTo(migrant.getValueList().getLast().getCode());
      assertThat(phase.getLastCompletedStepScope().getStepIndex()).isEqualTo(-1);
    }
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void agentAttachesMigrationAndGlobalComparisonToIteratedSearch() {
    var scope = newSolver().getSolverScope();
    var islandSolver = mock(IslandSolver.class);
    scope.setSolver(islandSolver);
    var phase = mock(IteratedLocalSearchPhase.class);
    var random = DefaultRandomSource.seeded(0).splitForChildThread();
    scope.setWorkingRandom(random);
    var initial = TestdataSolution.generateSolution(2, 3);
    var agent =
        new IslandAgent<>(
            0,
            List.of(phase),
            initial,
            new SharedGlobalState<>(),
            new BoundedChannel<>(1),
            new BoundedChannel<>(1),
            IslandModelConfig.builder().withIslandCount(1).build(),
            random,
            scope,
            new CountDownLatch(1));
    try (var ignored = scope.getScoreDirector()) {
      agent.run();
      var captor = ArgumentCaptor.forClass(PhaseLifecycleListener.class);
      verify(phase, times(3)).addPhaseLifecycleListener(captor.capture());
      assertThat(captor.getAllValues())
          .extracting(Object::getClass)
          .containsExactly(
              MigrationTrigger.class, GlobalBestUpdater.class, GlobalCompareListener.class);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void administrativeBestPublicationUsesImmutableSnapshotWithoutPrimitiveSteps() {
    var scope = newSolver().getSolverScope();
    var global = new SharedGlobalState<TestdataSolution>();
    var islandSolver = (IslandSolver<TestdataSolution>) mock(IslandSolver.class);
    when(islandSolver.getEnclosingGlobalState()).thenReturn(global);
    scope.setSolver(islandSolver);
    scope.setInitialSolution(TestdataSolution.generateSolution(2, 3));
    try (var director = scope.getScoreDirector()) {
      var score = director.calculateScore();
      scope.setBestScore(score);
      scope.setBestSolution(director.getWorkingSolution());
      var phase = new IteratedLocalSearchPhaseScope<>(scope, 0);
      GlobalBestUpdater.publishCurrentBestToGlobal(scope);
      var published = global.getBestSnapshot();
      assertThat(published).isNotNull();
      assertThat(published.getSolution()).isNotSameAs(director.getWorkingSolution());
      assertThat(published.getInnerScore()).isEqualTo(score);
      assertThat(phase.getLastCompletedStepScope().getStepIndex()).isEqualTo(-1);
      GlobalBestUpdater.publishCurrentBestToGlobal(scope);
      assertThat(global.getBestSnapshot().getVersion()).isEqualTo(published.getVersion());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2", "4", "UNSPECIFIED"})
  void factoryBuiltIslandsAndMoveWorkersReturnReplayableBest(String moveThreads) {
    RecordingThreadFactory.threads.clear();
    var phase =
        new IteratedLocalSearchPhaseConfig()
            .withLocalSearch(new LocalSearchPhaseConfig())
            .withPerturbationStrengths(1, 2)
            .withPerturbationAttemptLimit(8L)
            .withEpisodeCandidateAttemptLimit(12L)
            .withIterationCountLimit(2L);
    var island =
        new IslandModelPhaseConfig()
            .withIslandCount(2)
            .withMigrationFrequency(1)
            .withReceiveGlobalUpdateFrequency(1)
            .withPhaseConfigList(List.of(phase))
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(20));
    if (!moveThreads.equals("UNSPECIFIED")) {
      island.setMoveThreadCount(moveThreads);
    }
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
            .withMoveThreadCount("4")
            .withThreadFactoryClass(RecordingThreadFactory.class)
            .withPhases(island);
    var solver = SolverFactory.<TestdataSolution>create(config).buildSolver();
    var result = solver.solve(TestdataSolution.generateSolution(3, 6));
    assertThat(result.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
    assertThat(result.getScore())
        .isEqualTo(new TestdataEasyScoreCalculator().calculateScore(result));
    assertThat(phase.getMoveThreadCount()).isNull();
    var workers =
        moveThreads.equals("NONE") || moveThreads.equals("UNSPECIFIED")
            ? 0
            : Integer.parseInt(moveThreads);
    assertThat(RecordingThreadFactory.threads)
        .hasSize(2 * (1 + workers))
        .allSatisfy(thread -> assertThat(thread.isAlive()).isFalse());
  }

  public static final class RecordingThreadFactory implements ThreadFactory {
    private static final ConcurrentLinkedQueue<Thread> threads = new ConcurrentLinkedQueue<>();

    @Override
    public Thread newThread(Runnable runnable) {
      var thread = new Thread(runnable, "ils-island-resource-test");
      threads.add(thread);
      return thread;
    }
  }

  private static DefaultSolver<TestdataSolution> newSolver() {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class);
    return (DefaultSolver<TestdataSolution>)
        SolverFactory.<TestdataSolution>create(config).buildSolver();
  }
}
