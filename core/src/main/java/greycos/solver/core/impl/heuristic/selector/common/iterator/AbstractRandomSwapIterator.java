package greycos.solver.core.impl.heuristic.selector.common.iterator;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Supplier;

import greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.Nullable;

public abstract class AbstractRandomSwapIterator<
        Solution_, Move_ extends Move<Solution_>, SubSelection_>
    extends UpcomingSelectionIterator<Move_> {

  protected final Iterable<SubSelection_> leftSubSelector;
  protected final Iterable<SubSelection_> rightSubSelector;
  private final @Nullable Supplier<? extends Move_> emptyRightSelectionSupplier;

  protected Iterator<SubSelection_> leftSubSelectionIterator;
  protected Iterator<SubSelection_> rightSubSelectionIterator;

  public AbstractRandomSwapIterator(
      Iterable<SubSelection_> leftSubSelector, Iterable<SubSelection_> rightSubSelector) {
    this(leftSubSelector, rightSubSelector, null);
  }

  public AbstractRandomSwapIterator(
      Iterable<SubSelection_> leftSubSelector,
      Iterable<SubSelection_> rightSubSelector,
      @Nullable Supplier<? extends Move_> emptyRightSelectionSupplier) {
    this.leftSubSelector = leftSubSelector;
    this.rightSubSelector = rightSubSelector;
    this.emptyRightSelectionSupplier = emptyRightSelectionSupplier;
    leftSubSelectionIterator = this.leftSubSelector.iterator();
    rightSubSelectionIterator = this.rightSubSelector.iterator();
    // Don't do hasNext() in constructor (to avoid upcoming selections breaking mimic recording)
  }

  @Override
  protected Move_ createUpcomingSelection() {
    // Ideally, this code should have read:
    //     SubS leftSubSelection = leftSubSelectionIterator.next();
    //     SubS rightSubSelection = rightSubSelectionIterator.next();
    // But empty selectors and ending selectors (such as non-random or shuffled) make it more
    // complex
    if (!leftSubSelectionIterator.hasNext()) {
      leftSubSelectionIterator = leftSubSelector.iterator();
      if (!leftSubSelectionIterator.hasNext()) {
        return noUpcomingSelection();
      }
    }
    SubSelection_ leftSubSelection = leftSubSelectionIterator.next();
    if (!rightSubSelectionIterator.hasNext()) {
      rightSubSelectionIterator = rightSubSelector.iterator();
      if (!rightSubSelectionIterator.hasNext()) {
        // An origin-dependent secondary can be empty for this origin only. Return a non-doable
        // attempt so the next request selects another origin; move filtering bounds retries.
        if (emptyRightSelectionSupplier != null) {
          return Objects.requireNonNull(emptyRightSelectionSupplier.get());
        }
        return noUpcomingSelection();
      }
    }
    SubSelection_ rightSubSelection = rightSubSelectionIterator.next();
    return newSwapSelection(leftSubSelection, rightSubSelection);
  }

  protected abstract Move_ newSwapSelection(
      SubSelection_ leftSubSelection, SubSelection_ rightSubSelection);
}
