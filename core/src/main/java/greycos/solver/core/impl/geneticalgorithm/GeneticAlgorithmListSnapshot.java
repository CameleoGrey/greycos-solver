package greycos.solver.core.impl.geneticalgorithm;

import java.util.Arrays;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Immutable primitive list assignments, safe to share between genomes and evaluator jobs. */
@NullMarked
final class GeneticAlgorithmListSnapshot {

  static final GeneticAlgorithmListSnapshot EMPTY = new GeneticAlgorithmListSnapshot(new int[0][]);

  private final int[][] lists;
  private final int fingerprint;

  GeneticAlgorithmListSnapshot(int[][] source) {
    Objects.requireNonNull(source);
    lists = new int[source.length][];
    for (int owner = 0; owner < source.length; owner++) {
      lists[owner] = Objects.requireNonNull(source[owner]).clone();
    }
    fingerprint = Arrays.deepHashCode(lists);
  }

  int ownerCount() {
    return lists.length;
  }

  int size(int owner) {
    return lists[owner].length;
  }

  int get(int owner, int index) {
    return lists[owner][index];
  }

  int fingerprint() {
    return fingerprint;
  }

  int[] copyList(int owner) {
    return lists[owner].clone();
  }

  int[][] copyLists() {
    var copy = new int[lists.length][];
    for (int owner = 0; owner < lists.length; owner++) {
      copy[owner] = copyList(owner);
    }
    return copy;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return this == other
        || other instanceof GeneticAlgorithmListSnapshot snapshot
            && fingerprint == snapshot.fingerprint
            && Arrays.deepEquals(lists, snapshot.lists);
  }

  @Override
  public int hashCode() {
    return fingerprint;
  }

  @Override
  public String toString() {
    return Arrays.deepToString(lists);
  }
}
