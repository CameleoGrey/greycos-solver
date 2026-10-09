package greycos.solver.core.impl.geneticalgorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Ordered lookup buckets keyed only by immutable list assignments, never mutable basic values. */
@NullMarked
final class GeneticAlgorithmGenomeIndex<T> {

  private record Entry<T>(GeneticAlgorithmGenome genome, T payload) {}

  private final Map<Integer, List<Entry<T>>> buckets = new HashMap<>();

  void add(GeneticAlgorithmGenome genome, T payload) {
    buckets
        .computeIfAbsent(genome.listFingerprint(), ignored -> new ArrayList<>())
        .add(new Entry<>(genome, Objects.requireNonNull(payload)));
  }

  @Nullable T firstMatch(GeneticAlgorithmGenome query) {
    var entries = buckets.get(query.listFingerprint());
    if (entries != null) {
      for (var entry : entries) {
        // Keep both comparison direction and insertion order from the original population scan.
        if (entry.genome().equals(query)) return entry.payload();
      }
    }
    return null;
  }

  void clear() {
    buckets.clear();
  }
}
