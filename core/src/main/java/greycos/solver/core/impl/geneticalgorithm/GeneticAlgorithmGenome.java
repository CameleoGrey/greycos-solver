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
  private final GeneticAlgorithmListSnapshot lists;

  public GeneticAlgorithmGenome(@Nullable Object[] values) {
    this(values, GeneticAlgorithmListSnapshot.EMPTY);
  }

  public GeneticAlgorithmGenome(@Nullable Object[] values, int[][] lists) {
    this(values, new GeneticAlgorithmListSnapshot(lists));
  }

  GeneticAlgorithmGenome(@Nullable Object[] values, GeneticAlgorithmListSnapshot lists) {
    this(values, lists, true);
  }

  private GeneticAlgorithmGenome(
      @Nullable Object[] values, GeneticAlgorithmListSnapshot lists, boolean copyValues) {
    Objects.requireNonNull(values);
    this.values = copyValues ? values.clone() : values;
    this.lists = Objects.requireNonNull(lists);
  }

  GeneticAlgorithmGenome withBasicValues(@Nullable Object[] values) {
    return new GeneticAlgorithmGenome(values, lists);
  }

  GeneticAlgorithmGenome withLists(int[][] lists) {
    // The basic array is already private and immutable; only its referenced values may change.
    return new GeneticAlgorithmGenome(values, new GeneticAlgorithmListSnapshot(lists), false);
  }

  GeneticAlgorithmListSnapshot listSnapshot() {
    return lists;
  }

  int listFingerprint() {
    return lists.fingerprint();
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
    return lists.ownerCount();
  }

  public int[] list(int owner) {
    return lists.copyList(owner);
  }

  public int[][] lists() {
    return lists.copyLists();
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return this == other
        || other instanceof GeneticAlgorithmGenome genome
            && listFingerprint() == genome.listFingerprint()
            && Arrays.equals(values, genome.values)
            && lists.equals(genome.lists);
  }

  @Override
  public int hashCode() {
    // Basic values may be mutable entities. Only the primitive list component can be cached.
    return lists.ownerCount() == 0
        ? Arrays.hashCode(values)
        : 31 * Arrays.hashCode(values) + lists.fingerprint();
  }

  @Override
  public String toString() {
    return lists.ownerCount() == 0
        ? Arrays.toString(values)
        : Arrays.toString(values) + "; lists=" + lists;
  }
}
