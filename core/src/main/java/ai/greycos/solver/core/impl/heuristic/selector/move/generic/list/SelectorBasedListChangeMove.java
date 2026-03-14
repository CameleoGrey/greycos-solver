package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedListChangeMove<Solution_> extends ListChangeMove<Solution_> {

  public SelectorBasedListChangeMove(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object sourceEntity,
      int sourceIndex,
      Object destinationEntity,
      int destinationIndex) {
    super(variableDescriptor, sourceEntity, sourceIndex, destinationEntity, destinationIndex);
  }

  @Override
  public SelectorBasedListChangeMove<Solution_> rebase(
      ScoreDirector<Solution_> destinationScoreDirector) {
    var rebased = super.rebase(destinationScoreDirector);
    return new SelectorBasedListChangeMove<>(
        getVariableDescriptor(),
        rebased.getSourceEntity(),
        rebased.getSourceIndex(),
        rebased.getDestinationEntity(),
        rebased.getDestinationIndex());
  }
}
