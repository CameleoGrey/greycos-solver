package greycos.solver.core.impl.geneticalgorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Equality-based representatives of one run's current population, excluding pending winners. */
final class GeneticAlgorithmPopulationDiversity {

  private final Map<Integer, List<GeneticAlgorithmGenome>> representatives = new HashMap<>();
  private int size;

  void add(GeneticAlgorithmGenome genome) {
    var bucket =
        representatives.computeIfAbsent(genome.listFingerprint(), ignored -> new ArrayList<>());
    // contains retains query.equals(representative), including live basic-value equality.
    if (!bucket.contains(genome)) {
      bucket.add(genome);
      size++;
    }
  }

  int size() {
    return size;
  }

  void clear() {
    representatives.clear();
    size = 0;
  }
}
