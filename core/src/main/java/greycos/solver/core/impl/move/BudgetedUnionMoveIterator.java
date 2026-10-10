package greycos.solver.core.impl.move;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;

import greycos.solver.core.impl.heuristic.selector.common.KnownExhaustionIterator;
import greycos.solver.core.impl.heuristic.selector.common.SelectionAttemptContext;
import greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import greycos.solver.core.impl.solver.random.RandomUtils;
import greycos.solver.core.preview.api.move.Move;

/**
 * Budgeted union selection must not prefetch other children's proposals inside the reservation for
 * the selected move. Children are opened lazily and only the chosen child is advanced.
 */
public final class BudgetedUnionMoveIterator<Solution_, Source_>
    extends UpcomingSelectionIterator<Move<Solution_>> {

  private final List<Entry> entries;
  private final RandomGenerator random;
  private final BiFunction<Source_, RandomGenerator, Iterator<Move<Solution_>>> extractor;
  private final boolean uniform;

  public BudgetedUnionMoveIterator(
      RandomGenerator random,
      List<Source_> sources,
      BiFunction<Source_, RandomGenerator, Iterator<Move<Solution_>>> extractor,
      ToDoubleFunction<Source_> weightFunction) {
    this.random = random;
    this.extractor = extractor;
    this.uniform = weightFunction == null;
    entries = new ArrayList<>(sources.size());
    for (var source : sources) {
      double weight = uniform ? 1.0 : weightFunction.applyAsDouble(source);
      if (!Double.isFinite(weight) || weight < 0.0) {
        throw new IllegalArgumentException(
            "The union selection weight (" + weight + ") must be finite and non-negative.");
      }
      if (weight > 0.0) {
        entries.add(new Entry(source, weight));
      }
    }
  }

  @Override
  protected Move<Solution_> createUpcomingSelection() {
    while (!entries.isEmpty()) {
      int index = selectIndex();
      var entry = entries.get(index);
      long checkpoint = SelectionAttemptContext.checkpoint();
      if (entry.iterator == null) {
        entry.iterator = extractor.apply(entry.source, random);
      }
      if (!entry.iterator.hasNext()) {
        entries.remove(index);
        // Child filtering owns any rejected proposals it already charged.
        SelectionAttemptContext.failedSelectionSince(checkpoint);
        continue;
      }
      var move = entry.iterator.next();
      if (KnownExhaustionIterator.isExhausted(entry.iterator)) {
        entries.remove(index);
      }
      return move;
    }
    return noUpcomingSelection();
  }

  private int selectIndex() {
    if (uniform) {
      return entries.size() == 1 ? 0 : random.nextInt(entries.size());
    }
    double totalWeight = 0.0;
    for (var entry : entries) {
      totalWeight += entry.weight;
    }
    double offset = RandomUtils.nextDouble(random, totalWeight);
    for (int index = 0; index < entries.size() - 1; index++) {
      offset -= entries.get(index).weight;
      if (offset < 0.0) {
        return index;
      }
    }
    return entries.size() - 1;
  }

  @Override
  public boolean isKnownExhausted() {
    return super.isKnownExhausted() || (!upcomingCreated && entries.isEmpty());
  }

  private final class Entry {
    private final Source_ source;
    private final double weight;
    private Iterator<Move<Solution_>> iterator;

    private Entry(Source_ source, double weight) {
      this.source = source;
      this.weight = weight;
    }
  }
}
