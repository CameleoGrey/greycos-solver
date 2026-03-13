package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import java.util.List;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedSwapMove<Solution_> extends SwapMove<Solution_> {

  public SelectorBasedSwapMove(
      List<? extends GenuineVariableDescriptor<Solution_>> variableDescriptorList,
      Object leftEntity,
      Object rightEntity) {
    super(variableDescriptorList, leftEntity, rightEntity);
  }
}
