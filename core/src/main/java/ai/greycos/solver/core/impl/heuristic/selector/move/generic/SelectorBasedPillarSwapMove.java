package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import java.util.List;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedPillarSwapMove<Solution_> extends PillarSwapMove<Solution_> {

  public SelectorBasedPillarSwapMove(
      List<GenuineVariableDescriptor<Solution_>> variableDescriptorList,
      List<Object> leftPillar,
      List<Object> rightPillar) {
    super(variableDescriptorList, leftPillar, rightPillar);
  }
}
