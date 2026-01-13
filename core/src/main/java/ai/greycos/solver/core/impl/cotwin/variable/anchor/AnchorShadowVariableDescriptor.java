package ai.greycos.solver.core.impl.cotwin.variable.anchor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.variable.AnchorShadowVariable;
import ai.greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.policy.DescriptorPolicy;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ShadowVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.inverserelation.SingletonInverseVariableDemand;
import ai.greycos.solver.core.impl.cotwin.variable.inverserelation.SingletonInverseVariableSupply;
import ai.greycos.solver.core.impl.cotwin.variable.listener.VariableListenerWithSources;
import ai.greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public final class AnchorShadowVariableDescriptor<Solution_>
    extends ShadowVariableDescriptor<Solution_> {

  private VariableDescriptor<Solution_> sourceVariableDescriptor;

  public AnchorShadowVariableDescriptor(
      int ordinal,
      EntityDescriptor<Solution_> entityDescriptor,
      MemberAccessor variableMemberAccessor) {
    super(ordinal, entityDescriptor, variableMemberAccessor);
  }

  @Override
  public void processAnnotations(DescriptorPolicy descriptorPolicy) {
    // Do nothing
  }

  @Override
  public void linkVariableDescriptors(DescriptorPolicy descriptorPolicy) {
    linkShadowSources(descriptorPolicy);
  }

  private void linkShadowSources(DescriptorPolicy descriptorPolicy) {
    AnchorShadowVariable shadowVariableAnnotation =
        variableMemberAccessor.getAnnotation(AnchorShadowVariable.class);
    String sourceVariableName = shadowVariableAnnotation.sourceVariableName();
    sourceVariableDescriptor = entityDescriptor.getVariableDescriptor(sourceVariableName);
    if (sourceVariableDescriptor == null) {
      throw new IllegalArgumentException(
          "The entityClass ("
              + entityDescriptor.getEntityClass()
              + ") has an @"
              + AnchorShadowVariable.class.getSimpleName()
              + " annotated property ("
              + variableMemberAccessor.getName()
              + ") with sourceVariableName ("
              + sourceVariableName
              + ") which is not a valid planning variable on entityClass ("
              + entityDescriptor.getEntityClass()
              + ").\n"
              + entityDescriptor.buildInvalidVariableNameExceptionMessage(sourceVariableName));
    }
    if (!(sourceVariableDescriptor
            instanceof BasicVariableDescriptor<Solution_> basicVariableDescriptor)
        || !basicVariableDescriptor.isChained()) {
      throw new IllegalArgumentException(
          "The entityClass ("
              + entityDescriptor.getEntityClass()
              + ") has an @"
              + AnchorShadowVariable.class.getSimpleName()
              + " annotated property ("
              + variableMemberAccessor.getName()
              + ") with sourceVariableName ("
              + sourceVariableName
              + ") which is not chained.");
    }
    sourceVariableDescriptor.registerSinkVariableDescriptor(this);
  }

  @Override
  public List<VariableDescriptor<Solution_>> getSourceVariableDescriptorList() {
    return Collections.singletonList(sourceVariableDescriptor);
  }

  @Override
  public Collection<Class<?>> getVariableListenerClasses() {
    return Collections.singleton(AnchorVariableListener.class);
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  @Override
  public AnchorVariableDemand<Solution_> getProvidedDemand() {
    return new AnchorVariableDemand<>(sourceVariableDescriptor);
  }

  @Override
  public Iterable<VariableListenerWithSources> buildVariableListeners(SupplyManager supplyManager) {
    SingletonInverseVariableSupply inverseVariableSupply =
        supplyManager.demand(new SingletonInverseVariableDemand<>(sourceVariableDescriptor));
    return new VariableListenerWithSources<>(
            new AnchorVariableListener<>(this, sourceVariableDescriptor, inverseVariableSupply),
            sourceVariableDescriptor)
        .toCollection();
  }

  @Override
  public boolean isListVariableSource() {
    return false;
  }
}
