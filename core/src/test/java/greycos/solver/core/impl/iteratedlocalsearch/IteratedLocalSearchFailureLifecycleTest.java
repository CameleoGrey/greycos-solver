package greycos.solver.core.impl.iteratedlocalsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Machine;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchIntegrationTest.RecordingThreadFactory;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchSemanticsTest.EmptyMoves;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchSemanticsTest.LandscapeScoreCalculator;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchSemanticsTest.NextMoves;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.AbstractSolver;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListEasyScoreCalculator;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListEntity;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListSolution;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListValue;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@Execution(ExecutionMode.SAME_THREAD)
@Timeout(30)
class IteratedLocalSearchFailureLifecycleTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void outOfRangeStartingBindingFailsBeforeMoveWorkersStart(boolean pinned) {
    RecordingThreadFactory.threads.clear();
    var workload = new BasicWorkload();
    var problem = workload.createProblem(16);
    var job = problem.getJobs().getFirst();
    job.setMachine(new Machine(99));
    job.setPinned(pinned);
    var config =
        IteratedLocalSearchIntegrationTest.config(workload, "basic", "2")
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withThreadFactoryClass(RecordingThreadFactory.class);
    assertThatThrownBy(
            () -> SolverFactory.<BasicSolution>create(config).buildSolver().solve(problem))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("outside")
        .hasMessageContaining("value range");
    assertThat(RecordingThreadFactory.threads).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 3})
  void invalidStartingPinnedPrefixFailsBeforeMoveWorkersStart(int pinIndex) {
    RecordingThreadFactory.threads.clear();
    var values =
        List.of(
            new TestdataPinnedWithIndexListValue("a"), new TestdataPinnedWithIndexListValue("b"));
    var entity =
        TestdataPinnedWithIndexListEntity.createWithValues(
            "entity", values.toArray(TestdataPinnedWithIndexListValue[]::new));
    entity.setPinIndex(pinIndex);
    var problem = new TestdataPinnedWithIndexListSolution();
    problem.setValueList(values);
    problem.setEntityList(List.of(entity));
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataPinnedWithIndexListSolution.class)
            .withEntityClasses(
                TestdataPinnedWithIndexListEntity.class, TestdataPinnedWithIndexListValue.class)
            .withEasyScoreCalculatorClass(TestdataPinnedWithIndexListEasyScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withMoveThreadCount("2")
            .withThreadFactoryClass(RecordingThreadFactory.class)
            .withPhases(IteratedLocalSearchIntegrationTest.phase("list"));
    assertThatThrownBy(
            () ->
                SolverFactory.<TestdataPinnedWithIndexListSolution>create(config)
                    .buildSolver()
                    .solve(problem))
        .isInstanceOf(RuntimeException.class)
        .satisfies(failure -> assertThat(failure.getMessage().toLowerCase()).contains("pin"));
    assertThat(RecordingThreadFactory.threads).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"NONE,LOCAL_SEARCH", "2,LOCAL_SEARCH", "NONE,PERTURBATION", "2,PERTURBATION"})
  void stepStartedFailurePreservesPrimaryExceptionCleansRngAndWorkersAndAllowsAnotherSolve(
      String threads, IteratedLocalSearchStepScope.Origin origin) throws Exception {
    RecordingThreadFactory.threads.clear();
    var primary = new IllegalStateException("Intentional primitive step start failure");
    var cleanup = new IllegalStateException("Intentional outer phase cleanup failure");
    var remainingCleanupFailure = new AtomicReference<RuntimeException>(cleanup);
    var config = failureConfig(threads, origin);
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var thrown = new AtomicBoolean();
    // Phase listeners run after AbstractSolver has installed the saved random delegate.
    solver
        .getPhaseList()
        .getFirst()
        .addPhaseLifecycleListener(
            new PhaseLifecycleListenerAdapter<>() {
              @Override
              public void stepStarted(AbstractStepScope<TestdataSolution> step) {
                if (((IteratedLocalSearchStepScope<TestdataSolution>) step).getOrigin() == origin
                    && thrown.compareAndSet(false, true)) throw primary;
              }

              @Override
              public void phaseEnded(AbstractPhaseScope<TestdataSolution> phaseScope) {
                var exception = remainingCleanupFailure.getAndSet(null);
                if (exception != null) throw exception;
              }
            });
    assertThatThrownBy(() -> solver.solve(problem()))
        .isSameAs(primary)
        .satisfies(failure -> assertThat(failure.getSuppressed()).contains(cleanup));
    assertThat(thrown).isTrue();
    assertSavedRandomCleared(solver);
    assertWorkersStopped(threads.equals("NONE") ? 0 : 2);
    var result = solver.solve(problem());
    assertThat(new LandscapeScoreCalculator().calculateScore(result)).isEqualTo(result.getScore());
    assertSavedRandomCleared(solver);
    assertWorkersStopped(threads.equals("NONE") ? 0 : 4);
    var fresh = SolverFactory.<TestdataSolution>create(config).buildSolver().solve(problem());
    assertThat(result.getEntityList().getFirst().getValue().getCode())
        .isEqualTo(fresh.getEntityList().getFirst().getValue().getCode());
    assertThat(result.getScore()).isEqualTo(fresh.getScore());
  }

  private static SolverConfig failureConfig(
      String threads, IteratedLocalSearchStepScope.Origin origin) {
    var innerFactory =
        origin == IteratedLocalSearchStepScope.Origin.LOCAL_SEARCH
            ? NextMoves.class
            : EmptyMoves.class;
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withEasyScoreCalculatorClass(LandscapeScoreCalculator.class)
        .withRandomSeed(0L)
        .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
        .withMoveThreadCount(threads)
        .withThreadFactoryClass(RecordingThreadFactory.class)
        .withPhases(
            new IteratedLocalSearchPhaseConfig()
                .withLocalSearch(
                    new LocalSearchPhaseConfig()
                        .withMoveSelectorConfig(
                            new MoveIteratorFactoryConfig()
                                .withMoveIteratorFactoryClass(innerFactory))
                        .withAcceptorConfig(
                            new LocalSearchAcceptorConfig().withLateAcceptanceSize(4))
                        .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1))
                        .withTerminationConfig(new TerminationConfig().withStepCountLimit(2)))
                .withPerturbationMoveSelectorConfig(
                    new MoveIteratorFactoryConfig().withMoveIteratorFactoryClass(NextMoves.class))
                .withPerturbationStrengths(1)
                .withPerturbationAttemptLimit(4)
                .withEpisodeCandidateAttemptLimit(4)
                .withIterationCountLimit(2));
  }

  private static TestdataSolution problem() {
    var solution = new TestdataSolution("failure lifecycle");
    var values =
        List.of(new TestdataValue("0:0"), new TestdataValue("1:5"), new TestdataValue("2:1"));
    solution.setValueList(values);
    solution.setEntityList(List.of(new TestdataEntity("entity", values.getFirst())));
    return solution;
  }

  private static void assertSavedRandomCleared(DefaultSolver<?> solver)
      throws ReflectiveOperationException {
    var field = AbstractSolver.class.getDeclaredField("savedRandom");
    field.setAccessible(true);
    assertThat(field.get(solver)).isNull();
  }

  private static void assertWorkersStopped(int expected) throws InterruptedException {
    assertThat(RecordingThreadFactory.threads).hasSize(expected);
    for (var thread : RecordingThreadFactory.threads) {
      thread.join(5_000);
      assertThat(thread.isAlive()).as("%s stopped", thread.getName()).isFalse();
    }
  }
}
