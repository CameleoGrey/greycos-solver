package ai.greycos.solver.core.impl.cotwin.variable;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.supply.AbstractVariableDescriptorBasedDemand;
import ai.greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;

public final class ListVariableStateDemand<Solution_>
    extends AbstractVariableDescriptorBasedDemand<
        Solution_, ListVariableStateSupply<Solution_, Object, Object>> {

  public ListVariableStateDemand(ListVariableDescriptor<Solution_> variableDescriptor) {
    super(variableDescriptor);
  }

  @Override
  public ListVariableStateSupply<Solution_, Object, Object> createExternalizedSupply(
      SupplyManager supplyManager) {
    var listVariableDescriptor = (ListVariableDescriptor<Solution_>) variableDescriptor;
    return new ExternalizedListVariableStateSupply<>(listVariableDescriptor);
  }
}
