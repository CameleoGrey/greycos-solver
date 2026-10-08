package greycos.solver.core.impl.geneticalgorithm;

import java.util.List;
import java.util.Objects;

import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.score.director.InnerScore;

import org.jspecify.annotations.NullMarked;

/** A ranked, immutable population snapshot; assignment references belong to a detached clone. */
@NullMarked
public record GeneticAlgorithmMigrationBatch<Solution_>(
    int sourceIslandId, long generation, List<Entry<Solution_>> entries) {

  public GeneticAlgorithmMigrationBatch {
    if (sourceIslandId < 0 || generation < 0L) {
      throw new IllegalArgumentException(
          "The migration sourceIslandId (%d) and generation (%d) must be nonnegative."
              .formatted(sourceIslandId, generation));
    }
    entries = List.copyOf(entries);
  }

  public record Entry<Solution_>(SolutionAssignments<Solution_> assignments, InnerScore<?> score) {
    public Entry {
      Objects.requireNonNull(assignments, "Migration assignments must not be null.");
      Objects.requireNonNull(score, "Migration score must not be null.");
    }
  }
}
