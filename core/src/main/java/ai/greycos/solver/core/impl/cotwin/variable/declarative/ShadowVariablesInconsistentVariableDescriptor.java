package ai.greycos.solver.core.impl.cotwin.variable.declarative;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import ai.greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.policy.DescriptorPolicy;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ShadowVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.listener.VariableListenerWithSources;
import ai.greycos.solver.core.impl.cotwin.variable.supply.Demand;
import ai.greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;

public class ShadowVariablesInconsistentVariableDescriptor<Solution_>
    extends ShadowVariableDescriptor<Solution_> {
  public ShadowVariablesInconsistentVariableDescriptor(
      int ordinal,
      EntityDescriptor<Solution_> entityDescriptor,
      MemberAccessor variableMemberAccessor) {
    super(ordinal, entityDescriptor, variableMemberAccessor);
  }

  @Override
  public void processAnnotations(DescriptorPolicy descriptorPolicy) {
    // no action needed
  }

  @Override
  public List<VariableDescriptor<Solution_>> getSourceVariableDescriptorList() {
    return Collections.emptyList();
  }

  @Override
  public Collection<Class<?>> getVariableListenerClasses() {
    return Collections.emptyList();
  }

  @Override
  public Demand<?> getProvidedDemand() {
    return null;
  }

  @Override
  public Iterable<VariableListenerWithSources> buildVariableListeners(SupplyManager supplyManager) {
    return Collections.emptyList();
  }

  @Override
  public void linkVariableDescriptors(DescriptorPolicy descriptorPolicy) {
    // no action needed
  }

  @Override
  public boolean isListVariableSource() {
    return false;
  }
}
