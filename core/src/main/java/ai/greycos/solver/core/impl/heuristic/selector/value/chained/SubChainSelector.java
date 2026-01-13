package ai.greycos.solver.core.impl.heuristic.selector.value.chained;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.selector.ListIterableSelector;

public interface SubChainSelector<Solution_> extends ListIterableSelector<Solution_, SubChain> {

  /**
   * @return never null
   */
  GenuineVariableDescriptor<Solution_> getVariableDescriptor();
}
