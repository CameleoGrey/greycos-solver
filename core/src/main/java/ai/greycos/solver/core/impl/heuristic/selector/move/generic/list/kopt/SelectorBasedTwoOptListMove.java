package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedTwoOptListMove<Solution_> extends TwoOptListMove<Solution_> {

  public SelectorBasedTwoOptListMove(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object firstEntity,
      Object secondEntity,
      int firstEdgeEndpoint,
      int secondEdgeEndpoint) {
    super(variableDescriptor, firstEntity, secondEntity, firstEdgeEndpoint, secondEdgeEndpoint);
  }

  @Override
  public SelectorBasedTwoOptListMove<Solution_> rebase(
      ScoreDirector<Solution_> destinationScoreDirector) {
    return new SelectorBasedTwoOptListMove<>(
        getVariableDescriptor(),
        destinationScoreDirector.lookUpWorkingObject(getFirstEntity()),
        destinationScoreDirector.lookUpWorkingObject(getSecondEntity()),
        (Integer) getFirstEdgeEndpoint(),
        (Integer) getSecondEdgeEndpoint());
  }
}
