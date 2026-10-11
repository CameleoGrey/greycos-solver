package greycos.solver.greybay.api.objective;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import greycos.solver.greybay.api.ComponentIdentity;

/** Frozen exact baseline means and positive scales, retaining the explicit panel order. */
public record BalancedObjectiveCalibration(
    ScoreLayout layout,
    ComponentIdentity scoreAdapterIdentity,
    List<String> problemIds,
    List<Long> seeds,
    Map<String, List<ExactRational>> anchors,
    Map<String, List<ExactRational>> scales)
    implements ObjectiveCalibration {
  public BalancedObjectiveCalibration {
    Objects.requireNonNull(layout, "layout");
    Objects.requireNonNull(scoreAdapterIdentity, "scoreAdapterIdentity");
    problemIds = List.copyOf(problemIds);
    seeds = List.copyOf(seeds);
    if (problemIds.isEmpty()
        || seeds.isEmpty()
        || problemIds.stream().anyMatch(String::isBlank)
        || new HashSet<>(problemIds).size() != problemIds.size()
        || new HashSet<>(seeds).size() != seeds.size()) {
      throw new IllegalArgumentException(
          "Calibration panel IDs and seeds must be nonempty and unique.");
    }
    anchors = copyVectors(problemIds, layout.levelCount(), anchors, false);
    scales = copyVectors(problemIds, layout.levelCount(), scales, true);
  }

  private static Map<String, List<ExactRational>> copyVectors(
      List<String> problemIds,
      int levels,
      Map<String, List<ExactRational>> vectors,
      boolean positive) {
    if (!vectors.keySet().equals(new HashSet<>(problemIds))) {
      throw new IllegalArgumentException(
          "Calibration vectors must cover exactly the declared problem IDs.");
    }
    var copy = new LinkedHashMap<String, List<ExactRational>>();
    for (var problemId : problemIds) {
      var vector = List.copyOf(vectors.get(problemId));
      if (vector.size() != levels
          || (positive && vector.stream().anyMatch(value -> value.signum() <= 0))) {
        throw new IllegalArgumentException(
            "Invalid calibration "
                + (positive ? "scales" : "anchors")
                + " for problem ("
                + problemId
                + "): "
                + vector
                + ".");
      }
      copy.put(problemId, vector);
    }
    return Collections.unmodifiableMap(copy);
  }
}
