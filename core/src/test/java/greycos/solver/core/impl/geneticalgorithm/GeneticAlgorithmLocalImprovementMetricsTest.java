package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.recordPhase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.DefaultSolverFactory;
import greycos.solver.core.impl.solver.monitoring.SolverTags;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@Timeout(30)
class GeneticAlgorithmLocalImprovementMetricsTest {

  @Test
  void thresholdTerminationObservesEachTemporaryBestBeforePollingAndOuterCompletion() {
    try (var meters = new TestMeters()) {
      var events = new ArrayList<String>();
      // This verifies notification wiring, not elapsed time. The real factory termination uses
      // its system clock; keep the idle limit beyond this test's timeout instead of mixing clocks.
      var solver =
          controlledSolver(
              meters,
              new TerminationConfig()
                  .withStepCountLimit(100)
                  .withUnimprovedDaysSpentLimit(1L)
                  .withUnimprovedScoreDifferenceThreshold("50"),
              original -> {
                var termination = spy(original);
                doAnswer(
                        invocation -> {
                          AbstractStepScope<TestdataSolution> step = invocation.getArgument(0);
                          long best =
                              step.getPhaseScope().<SimpleScore>getBestScore().raw().score();
                          if (best == 80 || best == 160) {
                            assertThat(step.getBestScoreImproved()).isTrue();
                            assertReplay(step.getWorkingSolution());
                            assertThat(step.getWorkingSolution().getScore())
                                .isEqualTo(SimpleScore.of(best));
                            events.add("best/" + best);
                          }
                          return invocation.callRealMethod();
                        })
                    .when(termination)
                    .bestScoreImproved(any());
                doAnswer(
                        invocation -> {
                          AbstractPhaseScope<TestdataSolution> phase = invocation.getArgument(0);
                          long best = phase.<SimpleScore>getBestScore().raw().score();
                          if (best == 80 || best == 160) {
                            assertThat(events).contains("best/" + best);
                            events.add("poll/" + best);
                          }
                          return invocation.callRealMethod();
                        })
                    .when(termination)
                    .isPhaseTerminated(any());
                doAnswer(
                        invocation -> {
                          GeneticAlgorithmStepScope<TestdataSolution> step =
                              invocation.getArgument(0);
                          if (step.getLocalImprovementProbeCount() > 0) {
                            assertThat(step.getBestScoreImproved()).isTrue();
                            assertThat(step.getLocalImprovementProbeCount()).isEqualTo(3);
                            events.add("stepEnded");
                          }
                          return invocation.callRealMethod();
                        })
                    .when(termination)
                    .stepEnded(any());
                return termination;
              });
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
              if (((GeneticAlgorithmStepScope<TestdataSolution>) scope)
                      .getLocalImprovementProbeCount()
                  > 0) {
                solver.terminateEarly();
              }
            }
          });

      var result = solver.solve(problem(21, 8));

      assertThat(events)
          .filteredOn(event -> event.startsWith("best/"))
          .containsExactly("best/80", "best/160");
      assertThat(events)
          .containsSubsequence("best/80", "poll/80", "best/160", "poll/160", "stepEnded");
      assertThat(result.getScore()).isEqualTo(SimpleScore.of(160));
      assertReplay(result);
    }
  }

  @Test
  void finalRejectedProbeDoesNotOverwriteBestOrStepConstraintMetrics() {
    try (var meters = new TestMeters()) {
      var solver = controlledSolver(meters);
      var completed = new AtomicInteger();
      var publications = new ArrayList<TestdataSolution>();
      solver.addEventListener(event -> publications.add(event.getNewBestSolution()));
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
              completed.incrementAndGet();
              var step = (GeneticAlgorithmStepScope<TestdataSolution>) scope;
              if (step.getLocalImprovementProbeCount() == 0) return;
              assertThat(step.getLocalImprovementProbeCount()).isEqualTo(3);
              assertThat(step.getLocalImprovementAcceptedCount()).isEqualTo(2);
              assertThat(step.getBestScoreImproved()).isTrue();
              assertThat(step.<SimpleScore>getScore().raw()).isEqualTo(SimpleScore.of(160));
              meters.assertConstraintScore(SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE, 160);
              meters.assertConstraintScore(SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE, 160);
              meters.assertMoveCounts(1, 1);
              solver.terminateEarly();
            }
          });

      var result = solver.solve(problem(21, 8));

      assertThat(result.getScore()).isEqualTo(SimpleScore.of(160));
      assertThat(publications)
          .extracting(TestdataSolution::getScore)
          .contains(SimpleScore.of(80), SimpleScore.of(160));
      publications.forEach(GeneticAlgorithmIntegrationTest::assertReplay);
      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(completed.get() + 3);
      assertTypeCountsReconcile(solver);
      meters.assertConstraintScore(SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE, 160);
      assertReplay(result);
    }
  }

  @Test
  void interruptedSecondBestProbeRetainsPublishedBestMetricsAndCompletedProbeCredits() {
    try (var meters = new TestMeters()) {
      var solver = controlledSolver(meters);
      var phase = recordPhase(solver);
      var completed = new AtomicInteger();
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
              completed.incrementAndGet();
              assertThat(
                      ((GeneticAlgorithmStepScope<TestdataSolution>) scope)
                          .getLocalImprovementProbeCount())
                  .isZero();
            }
          });
      solver.addEventListener(
          event -> {
            assertReplay(event.getNewBestSolution());
            if (event.getNewBestSolution().getScore().equals(SimpleScore.of(160))) {
              solver.terminateEarly();
            }
          });

      var result = solver.solve(problem(21, 8));

      assertThat(result.getScore()).isEqualTo(SimpleScore.of(160));
      assertThat(phase.get().getLocalImprovementProbeCount()).isEqualTo(2);
      assertThat(phase.get().getLocalImprovementAcceptedCount()).isEqualTo(1);
      assertThat(phase.get().getNextStepIndex()).isEqualTo(completed.get());
      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(completed.get() + 2);
      assertTypeCountsReconcile(solver);
      meters.assertConstraintScore(SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE, 160);
      meters.assertConstraintScore(SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE, 0);
      meters.assertMoveCounts(completed.get() == 0 ? 0 : 1, completed.get() == 0 ? 0 : 1);
      assertReplay(result);
    }
  }

  @Test
  void throwingBestObserverRetainsPublishedBestMetricsAndEarlierProbeCredits() {
    try (var meters = new TestMeters()) {
      var solver = controlledSolver(meters);
      var phase = recordPhase(solver);
      var completed = new AtomicInteger();
      var failure = new IllegalStateException("Best observer failed.");
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
              completed.incrementAndGet();
              assertThat(
                      ((GeneticAlgorithmStepScope<TestdataSolution>) scope)
                          .getLocalImprovementProbeCount())
                  .isZero();
            }
          });
      solver.addEventListener(
          event -> {
            assertReplay(event.getNewBestSolution());
            if (event.getNewBestSolution().getScore().equals(SimpleScore.of(160))) {
              throw failure;
            }
          });

      assertThatThrownBy(() -> solver.solve(problem(21, 8))).isSameAs(failure);

      assertThat(phase.get().getLocalImprovementProbeCount()).isEqualTo(1);
      assertThat(phase.get().getLocalImprovementAcceptedCount()).isEqualTo(1);
      assertThat(phase.get().getNextStepIndex()).isEqualTo(completed.get());
      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(completed.get() + 1);
      assertTypeCountsReconcile(solver);
      meters.assertMoveCounts(completed.get() == 0 ? 0 : 1, completed.get() == 0 ? 0 : 1);
      meters.assertConstraintScore(SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE, 160);
      // Exceptional teardown keeps the last completed step instead of sampling queued undo state.
      meters.assertConstraintScore(SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE, 0);
      assertReplay(solver.getSolverScope().getBestSolution());
    }
  }

  @Test
  void outerCompletionFailureRollsBackOnlyTheOuterCreditAndMoveGauges() {
    try (var meters = new TestMeters()) {
      var solver = controlledSolver(meters);
      var phase = recordPhase(solver);
      var completed = new AtomicInteger();
      var failure = new IllegalStateException("Outer completion failed.");
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
              var step = (GeneticAlgorithmStepScope<TestdataSolution>) scope;
              if (step.getLocalImprovementProbeCount() == 0) {
                completed.incrementAndGet();
              } else {
                meters.assertMoveCounts(1, 1);
                assertThat(solver.getSolverScope().getMoveEvaluationCount())
                    .isEqualTo(completed.get() + 4);
                throw failure;
              }
            }
          });

      assertThatThrownBy(() -> solver.solve(problem(21, 8))).isSameAs(failure);

      assertThat(phase.get().getLocalImprovementProbeCount()).isEqualTo(3);
      assertThat(phase.get().getLocalImprovementAcceptedCount()).isEqualTo(2);
      assertThat(phase.get().getNextStepIndex()).isEqualTo(completed.get());
      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(completed.get() + 3);
      assertTypeCountsReconcile(solver);
      meters.assertMoveCounts(completed.get() == 0 ? 0 : 1, completed.get() == 0 ? 0 : 1);
      meters.assertConstraintScore(SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE, 160);
      assertReplay(solver.getSolverScope().getBestSolution());
    }
  }

  static DefaultSolver<TestdataSolution> controlledSolver(TestMeters meters) {
    return controlledSolver(
        meters, new TerminationConfig().withStepCountLimit(100), UnaryOperator.identity());
  }

  @SuppressWarnings("unchecked")
  private static DefaultSolver<TestdataSolution> controlledSolver(
      TestMeters meters,
      TerminationConfig terminationConfig,
      UnaryOperator<PhaseTermination<TestdataSolution>> decorateTermination) {
    var phaseConfig =
        new GeneticAlgorithmPhaseConfig()
            .withPopulationSize(1)
            .withLocalImprovementMoveCountLimit(3L)
            .withMutationOperators(
                new GeneticAlgorithmMutationOperatorConfig()
                    .withType(GeneticAlgorithmMutationType.CHANGE)
                    .withProbability(1.0))
            .withTerminationConfig(terminationConfig);
    var solverConfig = config(phaseConfig);
    var factory =
        spy(
            new DefaultSolverFactory<TestdataSolution>(
                solverConfig.withMonitoringConfig(
                    new MonitoringConfig()
                        .withConstraintMatchMetricSampleInterval(1)
                        .withSolverMetricList(
                            List.of(
                                SolverMetric.MOVE_COUNT_PER_STEP,
                                SolverMetric.MOVE_COUNT_PER_TYPE,
                                SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE,
                                SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE)))));
    var moves = mock(GeneticAlgorithmLocalImprovementMoves.class);
    var activeStep = new AtomicReference<GeneticAlgorithmStepScope<TestdataSolution>>();
    var selectionIndex = new AtomicInteger();
    doAnswer(
            invocation -> {
              activeStep.set(invocation.getArgument(0));
              selectionIndex.set(0);
              return null;
            })
        .when(moves)
        .stepStarted(any());
    when(moves.nextMove(any()))
        .thenAnswer(
            invocation -> {
              int target = new int[] {10, 20, 0}[selectionIndex.getAndIncrement()];
              var step = activeStep.get();
              var variable =
                  step.getPhaseScope()
                      .getSolutionDescriptor()
                      .getMetaModel()
                      .genuineEntity(TestdataEntity.class)
                      .basicVariable("value", TestdataValue.class);
              var solution = step.getWorkingSolution();
              Move<TestdataSolution> move =
                  view -> {
                    for (var entity : solution.getEntityList()) {
                      view.changeVariable(variable, entity, solution.getValueList().get(target));
                    }
                  };
              return move;
            });
    doAnswer(
            invocation -> {
              List<DefaultGeneticAlgorithmPhase<TestdataSolution>> original =
                  (List<DefaultGeneticAlgorithmPhase<TestdataSolution>>)
                      invocation.callRealMethod();
              var termination =
                  decorateTermination.apply(
                      (PhaseTermination<TestdataSolution>)
                          original.getFirst().getPhaseTermination());
              return List.of(
                  new DefaultGeneticAlgorithmPhase.Builder<>(
                          0,
                          EnvironmentMode.NO_ASSERT,
                          "",
                          termination,
                          phaseConfig.resolve(),
                          invocation.getArgument(1))
                      .withLocalImprovementMovesFactory(random -> moves)
                      .build());
            })
        .when(factory)
        .buildPhaseList(any(), any(), any());
    var solver = (DefaultSolver<TestdataSolution>) factory.buildSolver();
    solver.setMonitorTags(SolverTags.withProblemId(meters.tag));
    return solver;
  }

  private static void assertTypeCountsReconcile(DefaultSolver<TestdataSolution> solver) {
    assertThat(
            solver.getSolverScope().getMoveEvaluationCountPerType().values().stream()
                .mapToLong(Long::longValue)
                .sum())
        .isEqualTo(solver.getSolverScope().getMoveEvaluationCount());
  }

  static final class TestMeters implements AutoCloseable {
    private final String tag = UUID.randomUUID().toString();
    private final Tags tags = SolverTags.withProblemId(tag).asTags();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    TestMeters() {
      Metrics.addRegistry(registry);
    }

    private void assertMoveCounts(long selected, long accepted) {
      assertThat(
              registry
                  .find(SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".selected")
                  .tags(tags)
                  .gauge())
          .isNotNull()
          .satisfies(gauge -> assertThat(gauge.value()).isEqualTo((double) selected));
      assertThat(
              registry
                  .find(SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".accepted")
                  .tags(tags)
                  .gauge())
          .isNotNull()
          .satisfies(gauge -> assertThat(gauge.value()).isEqualTo((double) accepted));
    }

    private void assertConstraintScore(SolverMetric metric, double score) {
      var gauges = registry.find(metric.getMeterId() + ".score").tags(tags).gauges();
      assertThat(gauges).isNotEmpty();
      assertThat(gauges.stream().mapToDouble(gauge -> gauge.value()).sum()).isEqualTo(score);
    }

    @Override
    public void close() {
      Metrics.removeRegistry(registry);
      registry.close();
      for (var meter : List.copyOf(Metrics.globalRegistry.getMeters())) {
        if (tag.equals(meter.getId().getTag("problem.id"))) {
          Metrics.globalRegistry.remove(meter);
        }
      }
    }
  }
}
