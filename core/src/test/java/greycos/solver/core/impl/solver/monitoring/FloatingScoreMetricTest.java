package greycos.solver.core.impl.solver.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;

import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.score.definition.SimpleDoubleScoreDefinition;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;

class FloatingScoreMetricTest {
  @Test
  void differenceMetricRetainsWidenedLevelsWithoutConstructingScore() {
    var tags = Tags.of("test", "floating-difference");
    var metric = SolverMetric.PICKED_MOVE_TYPE_STEP_SCORE_DIFF;
    var levels = new HashMap<Tags, ScoreLevels>();
    try {
      SolverMetricUtil.registerScoreDifference(
          metric,
          tags,
          new SimpleDoubleScoreDefinition(),
          levels,
          SimpleDoubleScore.of(Double.MAX_VALUE),
          SimpleDoubleScore.of(-Double.MAX_VALUE));
      assertThat(levels.get(tags).levelValues[0].get())
          .isEqualTo(new BigDecimal(Double.MAX_VALUE).multiply(BigDecimal.valueOf(2)));
      SolverMetricUtil.registerScoreDifference(
          metric,
          tags,
          new SimpleDoubleScoreDefinition(),
          levels,
          SimpleDoubleScore.of(0.75),
          SimpleDoubleScore.of(0.25));
      assertThat(levels.get(tags).levelValues[0].get().doubleValue()).isEqualTo(0.5);
    } finally {
      Metrics.globalRegistry
          .find(metric.getMeterId() + ".score")
          .tags(tags)
          .gauges()
          .forEach(Metrics.globalRegistry::remove);
      Metrics.globalRegistry
          .find(metric.getMeterId() + ".unassigned.count")
          .tags(tags)
          .gauges()
          .forEach(Metrics.globalRegistry::remove);
    }
  }
}
