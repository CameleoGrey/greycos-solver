package ai.greycos.solver.core.impl.cotwin.variable.anchor;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.inverserelation.SingletonInverseVariableDemand;
import ai.greycos.solver.core.impl.cotwin.variable.inverserelation.SingletonInverseVariableSupply;
import ai.greycos.solver.core.impl.cotwin.variable.supply.AbstractVariableDescriptorBasedDemand;
import ai.greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;

public final class AnchorVariableDemand<Solution_>
    extends AbstractVariableDescriptorBasedDemand<Solution_, AnchorVariableSupply> {

  public AnchorVariableDemand(VariableDescriptor<Solution_> sourceVariableDescriptor) {
    super(sourceVariableDescriptor);
  }

  // ************************************************************************
  // Creation method
  // ************************************************************************

  @Override
  public AnchorVariableSupply createExternalizedSupply(SupplyManager supplyManager) {
    SingletonInverseVariableSupply inverseVariableSupply =
        supplyManager.demand(new SingletonInverseVariableDemand<>(variableDescriptor));
    return new ExternalizedAnchorVariableSupply<>(variableDescriptor, inverseVariableSupply);
  }
}
