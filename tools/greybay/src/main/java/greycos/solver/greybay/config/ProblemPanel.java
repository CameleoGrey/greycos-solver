package greycos.solver.greybay.config;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** An ordered Cartesian product of problem identities and explicit solver seeds. */
public record ProblemPanel(List<String> problemIds, List<Long> seeds) {
  public ProblemPanel {
    problemIds = List.copyOf(problemIds);
    seeds = List.copyOf(seeds);
    if (problemIds.isEmpty() || seeds.isEmpty()) {
      throw new IllegalArgumentException("A problem panel requires nonempty problemIds and seeds.");
    }
    if (problemIds.stream().anyMatch(String::isBlank)
        || new HashSet<>(problemIds).size() != problemIds.size()
        || new HashSet<>(seeds).size() != seeds.size()) {
      throw new IllegalArgumentException(
          "Panel problemIds and seeds must be unique; problemIds must not be blank.");
    }
    problemIds.forEach(Objects::requireNonNull);
    seeds.forEach(Objects::requireNonNull);
    Math.multiplyExact((long) problemIds.size(), seeds.size());
  }

  public long size() {
    return Math.multiplyExact((long) problemIds.size(), seeds.size());
  }
}
