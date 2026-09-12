package greycos.solver.core.impl.cotwin.variable.declarative;

import java.util.Collection;
import java.util.Collections;

import greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.policy.DescriptorPolicy;
import greycos.solver.core.impl.cotwin.variable.descriptor.ShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.supply.Demand;

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
  public Collection<Class<?>> getUpdaterClasses() {
    return Collections.emptyList();
  }

  @Override
  public Demand<?> getProvidedDemand() {
    return null;
  }

  @Override
  public void linkVariableDescriptors(DescriptorPolicy descriptorPolicy) {
    // no action needed
  }
}
