package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import java.util.List;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedPillarChangeMove<Solution_> extends PillarChangeMove<Solution_> {

  public SelectorBasedPillarChangeMove(
      List<Object> pillar,
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      Object toPlanningValue) {
    super(pillar, variableDescriptor, toPlanningValue);
  }
}
