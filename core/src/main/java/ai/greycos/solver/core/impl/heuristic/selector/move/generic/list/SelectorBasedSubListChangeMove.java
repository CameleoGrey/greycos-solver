package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.selector.list.SubList;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedSubListChangeMove<Solution_> extends SubListChangeMove<Solution_> {

  public SelectorBasedSubListChangeMove(
      ListVariableDescriptor<Solution_> variableDescriptor,
      SubList subList,
      Object destinationEntity,
      int destinationIndex,
      boolean reversing) {
    super(variableDescriptor, subList, destinationEntity, destinationIndex, reversing);
  }

  public SelectorBasedSubListChangeMove(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object sourceEntity,
      int sourceIndex,
      int length,
      Object destinationEntity,
      int destinationIndex,
      boolean reversing) {
    super(
        variableDescriptor,
        sourceEntity,
        sourceIndex,
        length,
        destinationEntity,
        destinationIndex,
        reversing);
  }

  @Override
  public SelectorBasedSubListChangeMove<Solution_> rebase(
      ScoreDirector<Solution_> destinationScoreDirector) {
    var rebased = super.rebase(destinationScoreDirector);
    return new SelectorBasedSubListChangeMove<>(
        getVariableDescriptor(),
        rebased.getSourceEntity(),
        rebased.getFromIndex(),
        rebased.getSubListSize(),
        rebased.getDestinationEntity(),
        rebased.getDestinationIndex(),
        rebased.isReversing());
  }
}
