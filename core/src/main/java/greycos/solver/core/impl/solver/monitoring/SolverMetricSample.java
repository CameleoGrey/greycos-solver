package greycos.solver.core.impl.solver.monitoring;

import java.util.LinkedHashMap;
import java.util.Map;

import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.score.director.InnerScore;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;

/** A completed metric publication, safe to hand from an island to its coordinator. */
public record SolverMetricSample(
    Kind kind,
    long timeMillisSpent,
    String source,
    Tags tags,
    String moveType,
    boolean bestScoreImproved,
    InnerScore<?> stepScore,
    SolverWorkSnapshot work,
    Map<Meter.Id, Double> measurements) {

  public enum Kind {
    STEP,
    BEST,
    FINAL
  }

  public SolverMetricSample {
    measurements = Map.copyOf(measurements);
  }

  public SolverMetricSample withWork(SolverWorkSnapshot reportedWork) {
    return new SolverMetricSample(
        kind,
        timeMillisSpent,
        source,
        tags,
        moveType,
        bestScoreImproved,
        stepScore,
        reportedWork,
        measurements);
  }

  /** Captures only this source's gauges; callers invoke this after collecting its step metrics. */
  public static Map<Meter.Id, Double> captureMeasurements(Tags tags) {
    var values = new LinkedHashMap<Meter.Id, Double>();
    for (var meter : Metrics.globalRegistry.getMeters()) {
      if (!(meter instanceof Gauge gauge) || !containsTags(meter.getId(), tags)) {
        continue;
      }
      var value = gauge.value();
      if (Double.isFinite(value)) {
        values.put(meter.getId(), value);
      }
    }
    return Map.copyOf(values);
  }

  /** Resolves a benchmark's root tags against this sample's source without changing other tags. */
  public Double gaugeValue(String name, Tags requestedTags) {
    if (name.equals(SolverMetric.SCORE_CALCULATION_COUNT.getMeterId())) {
      return (double) work.scoreCalculationCount();
    }
    if (name.equals(SolverMetric.MOVE_EVALUATION_COUNT.getMeterId())) {
      return (double) work.moveEvaluationCount();
    }
    var sourceTags =
        requestedTags.and(
            "island.id",
            tags.stream()
                .filter(tag -> tag.getKey().equals("island.id"))
                .map(tag -> tag.getValue())
                .findFirst()
                .orElse("root"));
    for (var entry : measurements.entrySet()) {
      if (entry.getKey().getName().equals(name) && containsTags(entry.getKey(), sourceTags)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static boolean containsTags(Meter.Id id, Tags tags) {
    for (var tag : tags) {
      if (!tag.getValue().equals(id.getTag(tag.getKey()))) {
        return false;
      }
    }
    return true;
  }
}
