package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assignments;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.recordPhase;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.recordSteps;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.solver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises the public factory wiring, including physical work discarded before logical commit. */
@Timeout(30)
class GeneticAlgorithmEvaluatorLifecycleTest {

  @ParameterizedTest
  @CsvSource({"false, 0", "true, 0", "false, 1", "true, 1", "false, 14", "true, 14"})
  void factoryMoveBudgetsCountOnlyCompletedAttempts(boolean phaseLocal, long limit) {
    var phaseConfig = pooledPhase(4);
    var solverConfig = config(phaseConfig);
    var termination = new TerminationConfig().withMoveCountLimit(limit);
    if (phaseLocal) phaseConfig.setTerminationConfig(termination);
    else solverConfig.setTerminationConfig(termination);
    var solver = solver(solverConfig);
    var steps = recordSteps(solver);

    var result = solver.solve(problem(20, 12));

    assertThat(steps).hasSize((int) limit);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(limit);
    if (limit == 0) assertThat(assignments(result)).containsOnly("0");
    assertReplay(result);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void precedingPhaseMovesAreIncludedOnlyInTheSolverBudget(boolean phaseLocal) {
    var first =
        new GeneticAlgorithmPhaseConfig()
            .withPopulationSize(4)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(3));
    var second = pooledPhase(3);
    var solverConfig = config(first).withPhases(first, second);
    var termination = new TerminationConfig().withMoveCountLimit(14L);
    if (phaseLocal) second.setTerminationConfig(termination);
    else solverConfig.setTerminationConfig(termination);
    var solver = solver(solverConfig);
    var counts = new ArrayList<Long>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
            counts.add(scope.getPhaseMoveEvaluationCount());
          }
        });

    assertReplay(solver.solve(problem(20, 12)));

    assertThat(counts).containsExactly(3L, phaseLocal ? 14L : 11L);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(phaseLocal ? 17 : 14);
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void scoreBudgetsObserveEverySuccessfulNativeCallIncludingWorkers(
      boolean phaseLocal, boolean precedingPhase) throws Exception {
    var phaseConfig = pooledPhase(3).withPopulationSize(4);
    var solverConfig = countedConfig(phaseConfig);
    if (precedingPhase) {
      solverConfig.withPhases(
          new GeneticAlgorithmPhaseConfig()
              .withPopulationSize(4)
              .withTerminationConfig(new TerminationConfig().withStepCountLimit(3)),
          phaseConfig);
    }
    long limit = 40;
    var termination = new TerminationConfig().withScoreCalculationCountLimit(limit);
    if (phaseLocal) phaseConfig.setTerminationConfig(termination);
    else solverConfig.setTerminationConfig(termination);
    var solver = solver(solverConfig);
    var phase = recordPhase(solver);
    var callsAtStart = new AtomicLong();
    try (var control = new ScoreControl()) {
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
              callsAtStart.set(control.successfulCalls.get());
            }
          });

      assertReplay(solver.solve(problem(30, 20)));

      assertThat(control.workerCalls()).isGreaterThan(3);
      assertThat(solver.getSolverScope().getScoreCalculationCount())
          .isEqualTo(control.successfulCalls.get());
      assertThat(solver.getSolverScope().getScoreDirector().getCalculationCount())
          .isEqualTo(control.successfulCalls.get());
      assertThat(phase.get().getPhaseScoreCalculationCount())
          .isEqualTo(control.successfulCalls.get() - callsAtStart.get());
      long budgetCount =
          phaseLocal
              ? phase.get().getPhaseScoreCalculationCount()
              : solver.getSolverScope().getScoreCalculationCount();
      // At most three active workers, their initialization, and required native validation can
      // overshoot the cooperative limit. A coordinator-only termination counter fails this bound.
      assertThat(budgetCount).isBetween(limit, limit + 12);
      if (precedingPhase) assertThat(callsAtStart).hasPositiveValue();
      control.assertWorkersStopped();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void zeroScoreBudgetStartsNoAttempt(boolean phaseLocal) {
    var phaseConfig = pooledPhase(2);
    var solverConfig = countedConfig(phaseConfig);
    var termination = new TerminationConfig().withScoreCalculationCountLimit(0L);
    if (phaseLocal) phaseConfig.setTerminationConfig(termination);
    else solverConfig.setTerminationConfig(termination);
    var solver = solver(solverConfig);
    var steps = recordSteps(solver);
    try (var control = new ScoreControl()) {
      var result = solver.solve(problem(5, 8));

      assertThat(steps).isEmpty();
      assertThat(control.workerCalls()).isZero();
      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
      assertThat(assignments(result)).containsOnly("0");
      assertReplay(result);
    }
  }

  @Test
  void cachedNoChangePopulationTerminatesWithoutInventedScoreCalculations() {
    var solver =
        solver(
            countedConfig(
                    pooledPhase(4)
                        .withPopulationSize(1)
                        .withNoProgressAttemptLimit(9L)
                        .withMutationOperators(
                            new GeneticAlgorithmMutationOperatorConfig()
                                .withType(GeneticAlgorithmMutationType.SWAP)
                                .withProbability(1.0)))
                .withTerminationConfig(
                    new TerminationConfig().withScoreCalculationCountLimit(100L)));
    var steps = recordSteps(solver);
    var phase = recordPhase(solver);
    try (var control = new ScoreControl()) {
      assertReplay(solver.solve(problem(2, 5)));

      assertThat(steps).hasSize(9);
      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(9);
      assertThat(phase.get().getNoProgressAttemptCount()).isEqualTo(9);
      assertThat(phase.get().getTerminationReason()).contains("no fresh valid offspring");
      assertThat(control.workerCalls()).isEqualTo(4);
      // Initial coordinator score and one setup score for each retained worker; cache hits
      // themselves must not invent score calculations.
      assertThat(phase.get().getPhaseScoreCalculationCount()).isEqualTo(5);
      assertThat(solver.getSolverScope().getScoreCalculationCount())
          .isEqualTo(control.successfulCalls.get());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void cancellationInterruptsBlockedWorkerAndLeavesOnlyCommittedAttempts(boolean interrupt)
      throws Exception {
    var solver =
        solver(
            countedConfig(
                pooledPhase(3)
                    .withMutationRateMultiplier(20.0)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(100))));
    var steps = recordSteps(solver);
    var entered = new CountDownLatch(3);
    var interrupted = new CountDownLatch(3);
    var release = new CountDownLatch(1);
    try (var control = new ScoreControl();
        var executor = Executors.newSingleThreadExecutor()) {
      control.onWorkerCall =
          call -> {
            if (call == 2) {
              entered.countDown();
              try {
                release.await();
              } catch (InterruptedException expected) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
              }
            }
          };
      var future =
          executor.submit(
              () -> {
                control.coordinator = Thread.currentThread();
                return solver.solve(problem(30, 20));
              });
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        var publishedBeforeCancellation = assignments(solver.getSolverScope().getBestSolution());
        if (interrupt) control.coordinator.interrupt();
        else assertThat(solver.terminateEarly()).isTrue();
        assertThat(interrupted.await(10, TimeUnit.SECONDS)).isTrue();
        var result = future.get(10, TimeUnit.SECONDS);

        assertThat(steps).hasSize(3).allSatisfy(step -> assertThat(step.isSeeding()).isTrue());
        assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(3);
        assertThat(solver.getSolverScope().getScoreCalculationCount())
            .isEqualTo(control.successfulCalls.get());
        assertThat(assignments(result)).isEqualTo(publishedBeforeCancellation);
        assertThat(solver.isSolving()).isFalse();
        assertReplay(result);
        control.assertWorkersStopped();
      } finally {
        release.countDown();
        solver.terminateEarly();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void workerInitializationAndEvaluationFailureKeepTheOriginalException(int failAtCall)
      throws Exception {
    var solver =
        solver(
            countedConfig(
                pooledPhase(3)
                    .withPopulationSize(1)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(100))));
    var failure = new IllegalStateException("intentional evaluator scoring failure");
    var cleanup = new IllegalArgumentException("intentional evaluator phase cleanup failure");
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
            throw cleanup;
          }
        });
    try (var control = new ScoreControl()) {
      control.onWorkerCall =
          call -> {
            if (call == failAtCall) throw failure;
          };

      assertThatThrownBy(() -> solver.solve(problem(30, 20)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("worker")
          .satisfies(
              thrown -> {
                assertThat(thrown.getCause()).isSameAs(failure);
                assertThat(thrown.getSuppressed()).contains(cleanup);
              });

      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
      assertThat(solver.getSolverScope().getScoreCalculationCount())
          .isEqualTo(control.successfulCalls.get());
      assertThat(solver.isSolving()).isFalse();
      control.assertWorkersStopped();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void pooledPublicationAndCompletionCallbackFailuresKeepTheirIdentity(boolean publication)
      throws Exception {
    var solver =
        solver(
            countedConfig(
                pooledPhase(3)
                    .withPopulationSize(1)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(100))));
    var failure = new IllegalStateException("intentional pooled callback failure");
    if (publication) {
      solver.addEventListener(
          event -> {
            if (event.getProducerId().equals(EventProducerId.geneticAlgorithm(0))) {
              assertReplay(event.getNewBestSolution());
              throw failure;
            }
          });
    } else {
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
              throw failure;
            }
          });
    }
    try (var control = new ScoreControl()) {
      assertThatThrownBy(() -> solver.solve(problem(30, 20))).isSameAs(failure);

      assertThat(control.workerCalls()).isPositive();
      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
      assertThat(solver.getSolverScope().getScoreCalculationCount())
          .isEqualTo(control.successfulCalls.get());
      assertThat(solver.isSolving()).isFalse();
      assertReplay(solver.getSolverScope().getBestSolution());
      control.assertWorkersStopped();
    }
  }

  @Test
  void problemChangeRestartsWorkerOwnershipRangesAndCachedFitness() {
    var solver =
        solver(
            config(
                pooledPhase(3)
                    .withPopulationSize(3)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(10))));
    var queued = new AtomicBoolean();
    var starts = new ArrayList<Integer>();
    var traces = new ArrayList<List<Integer>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
            starts.add(scope.getWorkingSolution().getEntityList().size());
            traces.add(new ArrayList<>());
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            var step = (GeneticAlgorithmStepScope<TestdataSolution>) scope;
            traces.getLast().add(step.getStepIndex());
            if (!step.isSeeding() && queued.compareAndSet(false, true)) {
              solver.addProblemChange(
                  (solution, director) -> {
                    solution.setValueList(new ArrayList<>(solution.getValueList()));
                    var high = new TestdataValue("9");
                    director.addProblemFact(high, solution.getValueList()::add);
                    for (var entity : solution.getEntityList()) {
                      director.changeVariable(entity, "value", working -> working.setValue(high));
                    }
                    director.addEntity(
                        new TestdataEntity("new-entity", high), solution.getEntityList()::add);
                  });
            }
          }
        });

    var result = solver.solve(problem(3, 5));

    assertThat(starts).containsExactly(5, 6);
    assertThat(traces.getFirst()).containsExactly(0, 1, 2);
    assertThat(traces.getLast()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    assertThat(solver.isEveryProblemChangeProcessed()).isTrue();
    assertThat(result.getEntityList()).hasSize(6);
    assertThat(assignments(result)).containsOnly("9");
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(54));
    assertReplay(result);
  }

  @Test
  void repeatedSolveOnDifferentUserThreadsHandsThePublishedCloneToLocalSearch() throws Exception {
    var ga =
        pooledPhase(3)
            .withPopulationSize(4)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(24));
    var solver =
        solver(
            countedConfig(ga)
                .withPhases(
                    ga,
                    new LocalSearchPhaseConfig()
                        .withTerminationConfig(new TerminationConfig().withStepCountLimit(2))));
    var handoffs = new AtomicInteger();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
            if (scope.getPhaseIndex() == 1) {
              handoffs.incrementAndGet();
              assertThat(assignments(scope.getWorkingSolution()))
                  .isEqualTo(assignments(scope.getSolverScope().getBestSolution()));
              assertReplay(scope.getWorkingSolution());
            }
          }
        });
    var buildThread = Thread.currentThread();
    var results = new ArrayList<List<String>>();
    for (int run = 0; run < 2; run++) {
      var input = problem(15, 12);
      try (var control = new ScoreControl();
          var executor = Executors.newSingleThreadExecutor()) {
        var result =
            executor
                .submit(
                    () -> {
                      control.coordinator = Thread.currentThread();
                      assertThat(control.coordinator).isNotSameAs(buildThread);
                      return solver.solve(input);
                    })
                .get(10, TimeUnit.SECONDS);
        results.add(assignments(result));
        assertReplay(result);
        assertThat(assignments(input)).containsOnly("0");
        assertThat(control.workerCalls()).isPositive();
        assertThat(solver.getSolverScope().getScoreCalculationCount())
            .isEqualTo(control.successfulCalls.get());
        control.assertWorkersStopped();
      }
    }
    assertThat(handoffs).hasValue(2);
    assertThat(results.getLast()).isEqualTo(results.getFirst());
  }

  private static GeneticAlgorithmPhaseConfig pooledPhase(int workers) {
    return new GeneticAlgorithmPhaseConfig()
        .withEvaluatorThreadCount(workers)
        .withPopulationSize(4)
        .withMutationOperators(
            new GeneticAlgorithmMutationOperatorConfig()
                .withType(GeneticAlgorithmMutationType.CHANGE)
                .withProbability(1.0));
  }

  private static SolverConfig countedConfig(GeneticAlgorithmPhaseConfig phase) {
    return config(phase)
        .withScoreDirectorFactory(
            new ScoreDirectorFactoryConfig()
                .withEasyScoreCalculatorClass(CountingScoreCalculator.class));
  }

  private static final class ScoreControl implements AutoCloseable {
    private volatile Thread coordinator = Thread.currentThread();
    private volatile IntConsumer onWorkerCall = ignored -> {};
    private final AtomicLong successfulCalls = new AtomicLong();
    private final Map<Thread, AtomicInteger> workerCalls = new ConcurrentHashMap<>();

    private ScoreControl() {
      assertThat(CountingScoreCalculator.CONTROL.compareAndSet(null, this)).isTrue();
    }

    private int workerCalls() {
      return workerCalls.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    private void assertWorkersStopped() throws InterruptedException {
      for (var thread : workerCalls.keySet()) {
        thread.join(1000);
        assertThat(thread.isAlive()).as("Evaluator thread %s", thread.getName()).isFalse();
      }
    }

    @Override
    public void close() {
      assertThat(CountingScoreCalculator.CONTROL.compareAndSet(this, null)).isTrue();
    }
  }

  public static final class CountingScoreCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    private static final AtomicReference<ScoreControl> CONTROL = new AtomicReference<>();

    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      var control = CONTROL.get();
      if (control != null && Thread.currentThread() != control.coordinator) {
        int call =
            control
                .workerCalls
                .computeIfAbsent(Thread.currentThread(), ignored -> new AtomicInteger())
                .incrementAndGet();
        control.onWorkerCall.accept(call);
      }
      int score =
          solution.getEntityList().stream()
              .filter(entity -> entity.getValue() != null)
              .mapToInt(entity -> Integer.parseInt(entity.getValue().getCode()))
              .sum();
      if (control != null) control.successfulCalls.incrementAndGet();
      return SimpleScore.of(score);
    }
  }
}
