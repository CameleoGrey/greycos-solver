package greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt;

import static greycos.solver.core.impl.heuristic.selector.move.generic.list.ListChangeMoveSelector.filterPinnedListPlanningVariableValuesWithIndex;

import java.util.Iterator;
import java.util.function.Supplier;

import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.AbstractGenericListMoveSelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.FilteringValueSelector;
import greycos.solver.core.impl.util.MathUtils;
import greycos.solver.core.preview.api.move.Move;

final class KOptListMoveSelector<Solution_> extends AbstractGenericListMoveSelector<Solution_> {

  private final IterableValueSelector<Solution_> originSelector;
  private final IterableValueSelector<Solution_> valueSelector;
  private final int minK;
  private final int maxK;

  private final int[] pickedKDistribution;

  public KOptListMoveSelector(
      ListVariableDescriptor<Solution_> listVariableDescriptor,
      IterableValueSelector<Solution_> originSelector,
      IterableValueSelector<Solution_> valueSelector,
      int minK,
      int maxK,
      int[] pickedKDistribution) {
    super(listVariableDescriptor);
    this.originSelector = createEffectiveValueSelector(originSelector, this::getListVariableState);
    this.valueSelector = createEffectiveValueSelector(valueSelector, this::getListVariableState);
    this.minK = minK;
    this.maxK = maxK;
    this.pickedKDistribution = pickedKDistribution;

    phaseLifecycleSupport.addEventListener(this.originSelector);
    phaseLifecycleSupport.addEventListener(this.valueSelector);
  }

  private IterableValueSelector<Solution_> createEffectiveValueSelector(
      IterableValueSelector<Solution_> iterableValueSelector,
      Supplier<ListVariableState<Solution_, Object, Object>> listVariableStateSupplier) {
    var filteredValueSelector =
        filterPinnedListPlanningVariableValuesWithIndex(
            iterableValueSelector, listVariableStateSupplier);
    return FilteringValueSelector.ofAssigned(filteredValueSelector, listVariableStateSupplier);
  }

  @Override
  public long getSize() {
    long valueSelectorSize = valueSelector.getSize();
    long originSelectorSize = originSelector.getSize();
    if (originSelectorSize == 0 || valueSelectorSize == 0) {
      return 0L;
    }
    long total = 0L;
    try {
      if (minK == 2) {
        // Two-opt selects endpoint pairs, also allowing a tail swap between two singleton routes.
        // The single-tour edge formula below would incorrectly report zero for that neighborhood.
        total = Math.multiplyExact(originSelectorSize, valueSelectorSize);
      }
      for (int k = Math.max(3, minK); k <= maxK && k < valueSelectorSize; k++) {
        // The number of pure 18-opt reconnections already exceeds Long.MAX_VALUE. The shared
        // utility uses long arithmetic, so do not ask it to calculate an unrepresentable count.
        if (k >= 18 || valueSelectorSize > Integer.MAX_VALUE) {
          return Long.MAX_VALUE;
        }
        // Approximate the number of ways to remove k edges from a tour's n - 1 edges.
        long edgeChoices = MathUtils.binomialCoefficient((int) (valueSelectorSize - 1), k);
        long kOptMoveTypes = KOptUtils.getPureKOptMoveTypes(k);
        total = Math.addExact(total, Math.multiplyExact(kOptMoveTypes, edgeChoices));
      }
    } catch (ArithmeticException overflow) {
      // Size is an estimate used by filtering bailout; wrapping negative could disable all moves.
      return Long.MAX_VALUE;
    }
    return total;
  }

  @Override
  public Iterator<Move<Solution_>> iterator() {
    return new KOptListMoveIterator<>(
        workingRandom,
        listVariableDescriptor,
        listVariableState,
        originSelector,
        valueSelector,
        minK,
        maxK,
        pickedKDistribution);
  }

  @Override
  public boolean isNeverEnding() {
    return true;
  }
}
