package greycos.solver.greybay.api.objective;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.score.Score;

/** An immutable complete Cartesian panel, ordered first by problem and then by common seed. */
public record CompletedPanel<Score_ extends Score<Score_>>(
    List<String> problemIds, List<Long> seeds, List<ScoredObservation<Score_>> observations) {

  public CompletedPanel {
    problemIds = List.copyOf(problemIds);
    seeds = List.copyOf(seeds);
    observations = List.copyOf(observations);
    if (problemIds.isEmpty()
        || seeds.isEmpty()
        || new HashSet<>(problemIds).size() != problemIds.size()
        || new HashSet<>(seeds).size() != seeds.size()
        || problemIds.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException(
          "Panel problem IDs and seeds must be nonempty and unique.");
    }
    if ((long) problemIds.size() * seeds.size() != observations.size()) {
      throw new IllegalArgumentException(
          "The panel is incomplete: expected "
              + ((long) problemIds.size() * seeds.size())
              + " cells, received "
              + observations.size()
              + ".");
    }
    record Key(String problemId, long seed) {}
    var indexed = new HashMap<Key, ScoredObservation<Score_>>();
    ScoreLayout layout = null;
    for (var observation : observations) {
      Objects.requireNonNull(observation, "observation");
      if (indexed.put(new Key(observation.problemId(), observation.seed()), observation) != null) {
        throw new IllegalArgumentException(
            "Duplicate panel cell (" + observation.problemId() + ", " + observation.seed() + ").");
      }
      var currentLayout = ScoreLayout.of(observation.score());
      if (layout != null && !layout.equals(currentLayout)) {
        throw new IllegalArgumentException(
            "Incompatible score layouts (" + layout + ") and (" + currentLayout + ").");
      }
      layout = currentLayout;
    }
    var ordered = new ArrayList<ScoredObservation<Score_>>(observations.size());
    for (var problemId : problemIds) {
      for (var seed : seeds) {
        var observation = indexed.get(new Key(problemId, seed));
        if (observation == null) {
          throw new IllegalArgumentException(
              "Missing panel cell (" + problemId + ", " + seed + ").");
        }
        ordered.add(observation);
      }
    }
    observations = List.copyOf(ordered);
  }

  public ScoredObservation<Score_> observation(String problemId, long seed) {
    int problemIndex = problemIds.indexOf(problemId);
    int seedIndex = seeds.indexOf(seed);
    if (problemIndex < 0 || seedIndex < 0) {
      throw new IllegalArgumentException("Unknown panel cell (" + problemId + ", " + seed + ").");
    }
    return observations.get(problemIndex * seeds.size() + seedIndex);
  }
}
