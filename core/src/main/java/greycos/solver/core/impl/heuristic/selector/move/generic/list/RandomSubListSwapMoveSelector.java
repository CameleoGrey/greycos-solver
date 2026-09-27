package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import java.util.Iterator;

import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.common.iterator.AbstractRandomSwapIterator;
import greycos.solver.core.impl.heuristic.selector.list.SubList;
import greycos.solver.core.impl.heuristic.selector.list.SubListSelector;
import greycos.solver.core.impl.heuristic.selector.move.generic.AbstractGenericMoveSelector;
import greycos.solver.core.preview.api.move.Move;

public class RandomSubListSwapMoveSelector<Solution_>
    extends AbstractGenericMoveSelector<Solution_> {

  private final SubListSelector<Solution_> leftSubListSelector;
  private final SubListSelector<Solution_> rightSubListSelector;
  private final ListVariableDescriptor<Solution_> listVariableDescriptor;
  private final boolean selectReversingMoveToo;
  private final boolean originDependentRightSelection;

  public RandomSubListSwapMoveSelector(
      SubListSelector<Solution_> leftSubListSelector,
      SubListSelector<Solution_> rightSubListSelector,
      boolean selectReversingMoveToo) {
    this(leftSubListSelector, rightSubListSelector, selectReversingMoveToo, false);
  }

  public RandomSubListSwapMoveSelector(
      SubListSelector<Solution_> leftSubListSelector,
      SubListSelector<Solution_> rightSubListSelector,
      boolean selectReversingMoveToo,
      boolean originDependentRightSelection) {
    this.leftSubListSelector = leftSubListSelector;
    this.rightSubListSelector = rightSubListSelector;
    this.listVariableDescriptor = leftSubListSelector.getVariableDescriptor();
    if (leftSubListSelector.getVariableDescriptor()
        != rightSubListSelector.getVariableDescriptor()) {
      throw new IllegalStateException(
          "The selector ("
              + this
              + ") has a leftSubListSelector's variableDescriptor ("
              + leftSubListSelector.getVariableDescriptor()
              + ") which is not equal to the rightSubListSelector's variableDescriptor ("
              + rightSubListSelector.getVariableDescriptor()
              + ").");
    }
    this.selectReversingMoveToo = selectReversingMoveToo;
    this.originDependentRightSelection = originDependentRightSelection;

    phaseLifecycleSupport.addEventListener(leftSubListSelector);
    phaseLifecycleSupport.addEventListener(rightSubListSelector);
  }

  @Override
  public Iterator<Move<Solution_>> iterator() {
    return new AbstractRandomSwapIterator<>(
        leftSubListSelector,
        rightSubListSelector,
        originDependentRightSelection && rightSubListSelector.getSize() > 0
            ? SelectorBasedNoChangeMove::getInstance
            : null) {
      @Override
      protected Move<Solution_> newSwapSelection(
          SubList leftSubSelection, SubList rightSubSelection) {
        boolean reversing = selectReversingMoveToo && workingRandom.nextBoolean();
        return new SelectorBasedSubListSwapMove<>(
            listVariableDescriptor, leftSubSelection, rightSubSelection, reversing);
      }
    };
  }

  @Override
  public boolean isNeverEnding() {
    return true;
  }

  @Override
  public long getSize() {
    long leftSubListCount = leftSubListSelector.getSize();
    long rightSubListCount = rightSubListSelector.getSize();
    return leftSubListCount * rightSubListCount * (selectReversingMoveToo ? 2 : 1);
  }

  boolean isSelectReversingMoveToo() {
    return selectReversingMoveToo;
  }

  SubListSelector<Solution_> getLeftSubListSelector() {
    return leftSubListSelector;
  }

  SubListSelector<Solution_> getRightSubListSelector() {
    return rightSubListSelector;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "("
        + leftSubListSelector
        + ", "
        + rightSubListSelector
        + ")";
  }
}
