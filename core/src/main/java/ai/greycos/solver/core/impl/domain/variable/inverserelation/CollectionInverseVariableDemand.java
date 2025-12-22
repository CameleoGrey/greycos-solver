package ai.greycos.solver.core.impl.domain.variable.inverserelation;

import ai.greycos.solver.core.impl.domain.variable.descriptor.VariableDescriptor;
import ai.greycos.solver.core.impl.domain.variable.supply.AbstractVariableDescriptorBasedDemand;
import ai.greycos.solver.core.impl.domain.variable.supply.SupplyManager;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;

/**
 * To get an instance, demand a {@link CollectionInverseVariableDemand} from {@link
 * InnerScoreDirector#getSupplyManager()}.
 */
public final class CollectionInverseVariableDemand<Solution_>
    extends AbstractVariableDescriptorBasedDemand<Solution_, CollectionInverseVariableSupply> {

  public CollectionInverseVariableDemand(VariableDescriptor<Solution_> sourceVariableDescriptor) {
    super(sourceVariableDescriptor);
  }

  // ************************************************************************
  // Creation method
  // ************************************************************************

  @Override
  public CollectionInverseVariableSupply createExternalizedSupply(SupplyManager supplyManager) {
    return new ExternalizedCollectionInverseVariableSupply<>(variableDescriptor);
  }
}
