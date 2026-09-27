package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.monitoring.SolverMetricRun;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.AbstractMeterTest;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.micrometer.core.instrument.Metrics;

class SolverMetricCleanupFailureTest extends AbstractMeterTest {

  private static final List<SolverMetric> METRICS =
      List.of(SolverMetric.SCORE_CALCULATION_COUNT, SolverMetric.MOVE_EVALUATION_COUNT);

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void primaryFailureSurvivesMetricCleanupFailure(boolean failDuringSolvingEnded) {
    var primaryFailure = new AssertionError("Solver lifecycle failed");
    var cleanupFailure = new IllegalStateException("Metric cleanup failed");
    var cleanup = failingCleanup(cleanupFailure);
    var metricRun = cleanup.metricRun();
    var solver =
        solver(
            context -> {
              if (!failDuringSolvingEnded) {
                throw primaryFailure;
              }
            });
    var observedFailures = new ArrayList<Throwable>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingStarted(SolverScope<TestdataSolution> scope) {
            scope.setMetricRun(metricRun);
          }

          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
            assertMetersRegistered(scope.getSolverScope());
          }

          @Override
          public void solvingEnded(SolverScope<TestdataSolution> scope) {
            if (failDuringSolvingEnded) {
              throw primaryFailure;
            }
          }

          @Override
          public void solvingError(SolverScope<TestdataSolution> scope, Throwable failure) {
            observedFailures.add(failure);
          }
        });

    assertThatThrownBy(() -> solver.solve(TestdataSolution.generateSolution(2, 2)))
        .isSameAs(primaryFailure);

    assertThat(primaryFailure.getSuppressed()).containsExactly(cleanupFailure);
    assertThat(observedFailures).containsExactly(primaryFailure);
    assertThat(metricRun.isActive()).isFalse();
    assertThat(cleanup.completedOuterCleanups()).hasValue(1);
    assertMetersRemoved(solver.getSolverScope());
    assertThat(solver.getSolverScope().getScoreDirector().getWorkingSolution()).isNull();
    assertThat(solver.isSolving()).isFalse();
  }

  @Test
  void metricCleanupFailureFailsAnOtherwiseSuccessfulSolve() {
    var cleanupFailure = new IllegalStateException("Metric cleanup failed");
    var cleanup = failingCleanup(cleanupFailure);
    var metricRun = cleanup.metricRun();
    var solver = solver(context -> {});
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingStarted(SolverScope<TestdataSolution> scope) {
            scope.setMetricRun(metricRun);
          }

          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
            assertMetersRegistered(scope.getSolverScope());
          }
        });

    assertThatThrownBy(() -> solver.solve(TestdataSolution.generateSolution(2, 2)))
        .isSameAs(cleanupFailure);

    assertThat(cleanupFailure.getSuppressed()).isEmpty();
    assertThat(metricRun.isActive()).isFalse();
    assertThat(cleanup.completedOuterCleanups()).hasValue(1);
    assertMetersRemoved(solver.getSolverScope());
    assertThat(solver.getSolverScope().getScoreDirector().getWorkingSolution()).isNull();
    assertThat(solver.isSolving()).isFalse();
  }

  private static CleanupFixture failingCleanup(RuntimeException cleanupFailure) {
    var metricRun = spy(new SolverMetricRun());
    var cleanupDepth = new AtomicInteger();
    var completedOuterCleanups = new AtomicInteger();
    doAnswer(
            invocation -> {
              boolean outermost = cleanupDepth.getAndIncrement() == 0;
              try {
                invocation.callRealMethod();
              } finally {
                cleanupDepth.decrementAndGet();
              }
              // Metric unregister operations may reenter cleanup; let every nested operation
              // finish.
              if (outermost && completedOuterCleanups.incrementAndGet() == 1) {
                throw cleanupFailure;
              }
              return null;
            })
        .when(metricRun)
        .cleanup(any());
    return new CleanupFixture(metricRun, completedOuterCleanups);
  }

  private record CleanupFixture(SolverMetricRun metricRun, AtomicInteger completedOuterCleanups) {}

  private static void assertMetersRegistered(SolverScope<?> scope) {
    for (var metric : METRICS) {
      assertThat(
              Metrics.globalRegistry
                  .find(metric.getMeterId())
                  .tags(scope.getMonitoringTags())
                  .gauge())
          .isNotNull();
    }
  }

  private static void assertMetersRemoved(SolverScope<?> scope) {
    for (var metric : METRICS) {
      assertThat(
              Metrics.globalRegistry
                  .find(metric.getMeterId())
                  .tags(scope.getMonitoringTags())
                  .gauge())
          .isNull();
    }
  }

  private static DefaultSolver<TestdataSolution> solver(PhaseCommand<TestdataSolution> command) {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
            .withMonitoringConfig(new MonitoringConfig().withSolverMetricList(METRICS))
            .withPhases(new CustomPhaseConfig().withCustomPhaseCommands(command));
    return (DefaultSolver<TestdataSolution>)
        SolverFactory.<TestdataSolution>create(config).buildSolver();
  }
}
