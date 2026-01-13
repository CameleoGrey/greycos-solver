package ai.greycos.solver.core.impl.cotwin.variable.inverserelation;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.supply.AbstractVariableDescriptorBasedDemand;
import ai.greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;

public final class SingletonInverseVariableDemand<Solution_>
    extends AbstractVariableDescriptorBasedDemand<Solution_, SingletonInverseVariableSupply> {

  public SingletonInverseVariableDemand(VariableDescriptor<Solution_> sourceVariableDescriptor) {
    super(sourceVariableDescriptor);
  }

  // ************************************************************************
  // Creation method
  // ************************************************************************

  @Override
  public SingletonInverseVariableSupply createExternalizedSupply(SupplyManager supplyManager) {
    return new ExternalizedSingletonInverseVariableSupply<>(variableDescriptor);
  }
}
