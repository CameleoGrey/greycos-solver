package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.list.SubList;
import greycos.solver.core.impl.score.director.ScoreDirector;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedSubListUnassignMove<Solution_> extends SubListUnassignMove<Solution_> {

  public SelectorBasedSubListUnassignMove(
      ListVariableDescriptor<Solution_> variableDescriptor, SubList subList) {
    super(variableDescriptor, subList);
  }

  @Override
  public SelectorBasedSubListUnassignMove<Solution_> rebase(
      ScoreDirector<Solution_> destinationScoreDirector) {
    var rebased = super.rebase(destinationScoreDirector);
    return new SelectorBasedSubListUnassignMove<>(
        rebased.getVariableDescriptor(),
        new SubList(rebased.getSourceEntity(), rebased.getFromIndex(), rebased.getSubListSize()));
  }
}
