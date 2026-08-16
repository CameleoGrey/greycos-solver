package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.score.director.ScoreDirector;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedListUnassignMove<Solution_> extends ListUnassignMove<Solution_> {

  public SelectorBasedListUnassignMove(
      ListVariableDescriptor<Solution_> variableDescriptor, Object sourceEntity, int sourceIndex) {
    super(variableDescriptor, sourceEntity, sourceIndex);
  }

  @Override
  public SelectorBasedListUnassignMove<Solution_> rebase(
      ScoreDirector<Solution_> destinationScoreDirector) {
    var rebased = (ListUnassignMove<Solution_>) super.rebase(destinationScoreDirector);
    return new SelectorBasedListUnassignMove<>(
        getVariableDescriptor(), rebased.getSourceEntity(), rebased.getSourceIndex());
  }
}
