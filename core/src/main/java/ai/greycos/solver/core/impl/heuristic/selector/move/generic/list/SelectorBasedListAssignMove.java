package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class SelectorBasedListAssignMove<Solution_> extends ListAssignMove<Solution_> {

  public SelectorBasedListAssignMove(
      ListVariableDescriptor<Solution_> variableDescriptor,
      @Nullable Object planningValue,
      Object destinationEntity,
      int destinationIndex) {
    super(variableDescriptor, planningValue, destinationEntity, destinationIndex);
  }

  @Override
  public SelectorBasedListAssignMove<Solution_> rebase(
      ScoreDirector<Solution_> destinationScoreDirector) {
    var rebased = super.rebase(destinationScoreDirector);
    return new SelectorBasedListAssignMove<>(
        getVariableDescriptor(),
        rebased.getMovedValue(),
        rebased.getDestinationEntity(),
        rebased.getDestinationIndex());
  }
}
