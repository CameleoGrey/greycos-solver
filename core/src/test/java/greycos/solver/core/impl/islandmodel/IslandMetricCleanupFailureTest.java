package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.phase.Phase;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import greycos.solver.core.impl.solver.monitoring.SolverMetricRun;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Tags;

class IslandMetricCleanupFailureTest {

  @Test
  @SuppressWarnings("unchecked")
  void lifecycleFailureSurvivesMetricCleanupAndDirectorIsClosedOnce() {
    var primaryFailure = new AssertionError("Island lifecycle failed");
    var cleanupFailure = new IllegalStateException("Metric cleanup failed");
    var metricRun = failingCleanup(cleanupFailure);
    var scope = new SolverScope<TestdataSolution>();
    var director = (InnerScoreDirector<TestdataSolution, ?>) mock(InnerScoreDirector.class);
    scope.setScoreDirector(director);
    scope.setMetricRun(metricRun);
    var phase = (Phase<TestdataSolution>) mock(Phase.class);
    doThrow(primaryFailure).when(phase).solvingEnded(scope);
    var solver = solver(phase);

    assertThatThrownBy(() -> solver.solvingEnded(scope)).isSameAs(primaryFailure);
    solver.solvingError(scope, primaryFailure);

    assertThat(primaryFailure.getSuppressed()).containsExactly(cleanupFailure);
    verify(metricRun).cleanup(any());
    verify(phase).solvingError(scope, primaryFailure);
    verify(director).close();
  }

  @Test
  @SuppressWarnings("unchecked")
  void finalPublicationFailureSurvivesMetricCleanupAndIsNotPublishedAgain() {
    var publicationFailure = new IllegalStateException("Final metric publication failed");
    var cleanupFailure = new IllegalStateException("Metric cleanup failed");
    var metricRun = failingCleanup(cleanupFailure);
    var scope = new SolverScope<TestdataSolution>();
    var director = (InnerScoreDirector<TestdataSolution, ?>) mock(InnerScoreDirector.class);
    scope.setScoreDirector(director);
    scope.setMetricRun(metricRun);
    scope.setMetricSource("phase-0/island-0");
    scope.setMonitoringTags(Tags.empty());
    scope.startingNow();
    var publications = new AtomicInteger();
    scope.setMetricSamplePublisher(
        sample -> {
          publications.incrementAndGet();
          throw publicationFailure;
        });
    var phase = (Phase<TestdataSolution>) mock(Phase.class);
    var solver = solver(phase);

    assertThatThrownBy(() -> solver.solvingEnded(scope)).isSameAs(publicationFailure);
    solver.solvingError(scope, publicationFailure);

    assertThat(publicationFailure.getSuppressed()).containsExactly(cleanupFailure);
    assertThat(publications).hasValue(1);
    verify(metricRun).cleanup(any());
    verify(director).close();
  }

  private static SolverMetricRun failingCleanup(RuntimeException cleanupFailure) {
    var metricRun = spy(new SolverMetricRun());
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw cleanupFailure;
            })
        .when(metricRun)
        .cleanup(any());
    return metricRun;
  }

  @SuppressWarnings("unchecked")
  private static IslandSolver<TestdataSolution> solver(Phase<TestdataSolution> phase) {
    return new IslandSolver<>(
        EnvironmentMode.NO_ASSERT,
        mock(ScoreDirectorFactory.class),
        mock(BestSolutionRecaller.class),
        new BasicPlumbingTermination<>(false),
        List.of(phase));
  }
}
