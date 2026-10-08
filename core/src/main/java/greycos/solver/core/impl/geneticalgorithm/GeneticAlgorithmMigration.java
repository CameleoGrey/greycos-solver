package greycos.solver.core.impl.geneticalgorithm;

import java.util.List;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Island-owned communication invoked only by the genetic algorithm's solver thread. */
@NullMarked
public interface GeneticAlgorithmMigration<Solution_> {

  int frequency();

  void phaseStarted();

  @Nullable GeneticAlgorithmMigrationBatch<Solution_> exchange(
      long generation, List<GeneticAlgorithmMigrationBatch.Entry<Solution_>> emigrants);

  void publishBest();

  void phaseEnded();
}
