package greycos.solver.core.impl.geneticalgorithm;

import java.util.Arrays;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * An immutable array of genuine assignments in a phase-local slot order. Assignment values retain
 * their canonical identity in the workspace; this snapshot never owns entities or shadow state.
 */
@NullMarked
public final class GeneticAlgorithmGenome {

  private final @Nullable Object[] values;

  public GeneticAlgorithmGenome(@Nullable Object[] values) {
    this.values = Objects.requireNonNull(values).clone();
  }

  public int size() {
    return values.length;
  }

  public @Nullable Object value(int index) {
    return values[index];
  }

  public @Nullable Object[] toArray() {
    return values.clone();
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return this == other
        || other instanceof GeneticAlgorithmGenome genome && Arrays.equals(values, genome.values);
  }

  @Override
  public int hashCode() {
    // The phase compares genomes directly: planning values may themselves be mutable entities.
    return Arrays.hashCode(values);
  }

  @Override
  public String toString() {
    return Arrays.toString(values);
  }
}
