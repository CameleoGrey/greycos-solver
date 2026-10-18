package greycos.solver.core.impl.cotwin.variable.nextprev;

import greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.policy.DescriptorPolicy;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;

abstract class AbstractNextPrevElementShadowVariableDescriptor<Solution_>
    extends ShadowVariableDescriptor<Solution_> {

  protected ListVariableDescriptor<Solution_> sourceVariableDescriptor;

  protected AbstractNextPrevElementShadowVariableDescriptor(
      int ordinal,
      EntityDescriptor<Solution_> entityDescriptor,
      MemberAccessor variableMemberAccessor) {
    super(ordinal, entityDescriptor, variableMemberAccessor);
  }

  abstract String getSourceVariableName();

  abstract String getAnnotationName();

  @Override
  public void processAnnotations(DescriptorPolicy descriptorPolicy) {
    // Do nothing
  }

  @Override
  public void linkVariableDescriptors(DescriptorPolicy descriptorPolicy) {
    linkShadowSources(descriptorPolicy);
  }

  private void linkShadowSources(DescriptorPolicy descriptorPolicy) {
    String sourceVariableName = getSourceVariableName();
    var candidates =
        entityDescriptor.getSolutionDescriptor().getListVariableDescriptorList().stream()
            .filter(descriptor -> descriptor.getVariableName().equals(sourceVariableName))
            .filter(
                descriptor ->
                    descriptor.acceptsValueType(entityDescriptor.getEntityClass())
                        || entityDescriptor
                            .getEntityClass()
                            .isAssignableFrom(descriptor.getElementType()))
            .toList();
    if (candidates.size() != 1) {
      throw new IllegalArgumentException(
          "The entityClass (%s) has an @%s-annotated property (%s) with sourceVariableName (%s), which does not identify a unique compatible planning list variable. Candidates: %s."
              .formatted(
                  entityDescriptor.getEntityClass(),
                  getAnnotationName(),
                  variableMemberAccessor.getName(),
                  sourceVariableName,
                  candidates));
    }
    sourceVariableDescriptor = candidates.getFirst();
    if (!variableMemberAccessor.getType().equals(sourceVariableDescriptor.getElementType())) {
      throw new IllegalStateException(
          "The entityClass ("
              + entityDescriptor.getEntityClass()
              + ") has a @"
              + getAnnotationName()
              + " annotated property ("
              + variableMemberAccessor.getName()
              + ") of type ("
              + variableMemberAccessor.getType()
              + ") which is not the type of elements ("
              + sourceVariableDescriptor.getElementType()
              + ") of the source list variable ("
              + sourceVariableDescriptor
              + ").");
    }
  }

  @Override
  public VariableDescriptor<Solution_> getSourceVariableDescriptor() {
    return sourceVariableDescriptor;
  }
}
