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
  private final int[][] lists;

  public GeneticAlgorithmGenome(@Nullable Object[] values) {
    this(values, new int[0][]);
  }

  public GeneticAlgorithmGenome(@Nullable Object[] values, int[][] lists) {
    this.values = Objects.requireNonNull(values).clone();
    this.lists = copyLists(lists);
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

  public int listCount() {
    return lists.length;
  }

  public int[] list(int owner) {
    return lists[owner].clone();
  }

  public int[][] lists() {
    return copyLists(lists);
  }

  private static int[][] copyLists(int[][] source) {
    Objects.requireNonNull(source);
    var copy = new int[source.length][];
    for (var i = 0; i < source.length; i++) {
      copy[i] = Objects.requireNonNull(source[i]).clone();
    }
    return copy;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return this == other
        || other instanceof GeneticAlgorithmGenome genome
            && Arrays.equals(values, genome.values)
            && Arrays.deepEquals(lists, genome.lists);
  }

  @Override
  public int hashCode() {
    // The phase compares genomes directly: planning values may themselves be mutable entities.
    return lists.length == 0
        ? Arrays.hashCode(values)
        : 31 * Arrays.hashCode(values) + Arrays.deepHashCode(lists);
  }

  @Override
  public String toString() {
    return lists.length == 0
        ? Arrays.toString(values)
        : Arrays.toString(values) + "; lists=" + Arrays.deepToString(lists);
  }
}
