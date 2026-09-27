package greycos.solver.core.impl.solver.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.testutil.AbstractMeterTest;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class SolverMetricSampleTest extends AbstractMeterTest {
  @Test
  void capturesImmutableSourceValuesAndUsesReportedTotals() {
    var rootTags = SolverTags.withProblemId("problem").asTags();
    var childTags = rootTags.and("island.id", "1");
    var value = new AtomicLong(7);
    var meterName = SolverMetric.PICKED_MOVE_TYPE_STEP_SCORE_DIFF.getMeterId() + ".score";
    var registry = new SimpleMeterRegistry();
    try {
      Metrics.addRegistry(registry);
      try {
        Metrics.gauge(meterName, childTags.and("move.type", "change"), value);
        var sample =
            new SolverMetricSample(
                SolverMetricSample.Kind.STEP,
                15,
                "phase-0/island-1",
                childTags,
                "change",
                false,
                null,
                new SolverWorkSnapshot(30, 12, Map.of()),
                SolverMetricSample.captureMeasurements(childTags));
        value.set(99);
        assertThat(sample.gaugeValue(meterName, rootTags.and("move.type", "change"))).isEqualTo(7);
        assertThat(sample.gaugeValue(meterName, rootTags.and("move.type", "swap"))).isNull();
        assertThat(sample.gaugeValue(SolverMetric.SCORE_CALCULATION_COUNT.getMeterId(), rootTags))
            .isEqualTo(30);
        assertThat(
                sample
                    .withWork(new SolverWorkSnapshot(40, 15, Map.of()))
                    .work()
                    .scoreCalculationCount())
            .isEqualTo(40);
        assertThat(sample.work().scoreCalculationCount()).isEqualTo(30);
      } finally {
        Metrics.removeRegistry(registry);
      }
    } finally {
      registry.close();
    }
  }
}
