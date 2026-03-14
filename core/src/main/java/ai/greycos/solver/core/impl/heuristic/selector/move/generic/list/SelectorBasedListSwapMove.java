package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedListSwapMove<Solution_> extends ListSwapMove<Solution_> {

  public SelectorBasedListSwapMove(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object leftEntity,
      int leftIndex,
      Object rightEntity,
      int rightIndex) {
    super(variableDescriptor, leftEntity, leftIndex, rightEntity, rightIndex);
  }

  @Override
  public SelectorBasedListSwapMove<Solution_> rebase(
      ScoreDirector<Solution_> destinationScoreDirector) {
    var rebased = super.rebase(destinationScoreDirector);
    return new SelectorBasedListSwapMove<>(
        getVariableDescriptor(),
        rebased.getLeftEntity(),
        rebased.getLeftIndex(),
        rebased.getRightEntity(),
        rebased.getRightIndex());
  }
}
