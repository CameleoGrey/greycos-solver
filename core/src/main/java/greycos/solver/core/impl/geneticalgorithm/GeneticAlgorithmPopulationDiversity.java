package greycos.solver.core.impl.geneticalgorithm;

import java.util.ArrayList;
import java.util.List;

/** Equality-based representatives of one run's current population, excluding pending winners. */
final class GeneticAlgorithmPopulationDiversity {

  private final List<GeneticAlgorithmGenome> representatives = new ArrayList<>();

  void add(GeneticAlgorithmGenome genome) {
    // Compare each newly seeded member once instead of recounting the entire growing population.
    if (!representatives.contains(genome)) {
      representatives.add(genome);
    }
  }

  int size() {
    return representatives.size();
  }

  void clear() {
    representatives.clear();
  }
}
