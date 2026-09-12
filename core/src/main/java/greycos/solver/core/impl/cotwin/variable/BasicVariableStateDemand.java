package greycos.solver.core.impl.cotwin.variable;

import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.supply.AbstractVariableDescriptorBasedDemand;
import greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;
import greycos.solver.core.impl.score.director.InnerScoreDirector;

/**
 * To get an instance, demand a {@link BasicVariableStateDemand} from {@link
 * InnerScoreDirector#getSupplyManager()}.
 */
public final class BasicVariableStateDemand<Solution_>
    extends AbstractVariableDescriptorBasedDemand<Solution_, BasicVariableStateSupply<Solution_>> {

  public BasicVariableStateDemand(VariableDescriptor<Solution_> variableDescriptor) {
    super(variableDescriptor);
  }

  @Override
  public BasicVariableStateSupply<Solution_> createExternalizedSupply(SupplyManager supplyManager) {
    return new ExternalizedBasicVariableStateSupply<>(
        variableDescriptor, supplyManager.getStateChangeNotifier());
  }
}
