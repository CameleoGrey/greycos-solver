package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class SelectorBasedChangeMove<Solution_> extends ChangeMove<Solution_> {

  public SelectorBasedChangeMove(
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      Object entity,
      @Nullable Object toPlanningValue) {
    super(variableDescriptor, entity, toPlanningValue);
  }

  @Override
  public SelectorBasedChangeMove<Solution_> rebase(
      ScoreDirector<Solution_> destinationScoreDirector) {
    var rebased = super.rebase(destinationScoreDirector);
    return new SelectorBasedChangeMove<>(
        variableDescriptor, rebased.getEntity(), rebased.getToPlanningValue());
  }
}
