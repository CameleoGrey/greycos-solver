package greycos.solver.core.impl.islandmodel.resumable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;

class ResidentSearchSolverMetricsTest {

  @Test
  @SuppressWarnings("unchecked")
  void failureRemovesOnlyOwnedEpisodeMetersAndSealsPublication() {
    var scope = new SolverScope<Object>();
    var tags = Tags.of("problem.id", "resident-metric-cleanup", "resumable.session", "episode-1");
    scope.setMonitoringTags(tags);
    scope.setSolverMetricSet(EnumSet.of(SolverMetric.MOVE_COUNT_PER_STEP));
    var director = (InnerScoreDirector<Object, ?>) mock(InnerScoreDirector.class);
    scope.setScoreDirector(director);
    var solver =
        new ResidentSearchSolver<>(
            EnvironmentMode.NO_ASSERT,
            mock(ScoreDirectorFactory.class),
            mock(BestSolutionRecaller.class),
            new BasicPlumbingTermination<>(false),
            List.of());
    var owned =
        Metrics.gauge(
            "greycos.resident.test", tags.and("constraint.id", "extra"), new AtomicLong(1));
    var siblingTags =
        Tags.of("problem.id", "resident-metric-cleanup", "resumable.session", "episode-2");
    var sibling = Metrics.gauge("greycos.resident.test", siblingTags, new AtomicLong(2));
    try {
      var failure = new IllegalStateException("Resident startup failed.");
      solver.solvingError(scope, failure);
      solver.solvingError(scope, failure);

      assertThat(Metrics.globalRegistry.find("greycos.resident.test").tags(tags).gauge()).isNull();
      assertThat(Metrics.globalRegistry.find("greycos.resident.test").tags(siblingTags).gauge())
          .isNotNull();
      assertThat(scope.getMetricRun().isActive()).isFalse();
      verify(director).close();
      assertThat(failure.getSuppressed()).isEmpty();
      assertThat(owned).hasValue(1);
      assertThat(sibling).hasValue(2);
    } finally {
      for (var meter : Metrics.globalRegistry.getMeters()) {
        if ("resident-metric-cleanup".equals(meter.getId().getTag("problem.id"))) {
          Metrics.globalRegistry.remove(meter);
        }
      }
    }
  }
}
