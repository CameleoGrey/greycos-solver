package greycos.solver.core.impl.cotwin.variable;

import java.util.Collection;

import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.policy.DescriptorPolicy;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;

public final class IndexShadowVariableDescriptor<Solution_>
    extends ShadowVariableDescriptor<Solution_> {

  private ListVariableDescriptor<Solution_> sourceVariableDescriptor;

  public IndexShadowVariableDescriptor(
      int ordinal,
      EntityDescriptor<Solution_> entityDescriptor,
      MemberAccessor variableMemberAccessor) {
    super(ordinal, entityDescriptor, variableMemberAccessor);
    if (!variableMemberAccessor.getType().equals(Integer.class)
        && !variableMemberAccessor.getType().equals(Long.class)) {
      throw new IllegalStateException(
          """
          The entityClass (%s) has an @%s-annotated member (%s) of type (%s) which cannot represent an index in a list.
          The @%s-annotated member type must be %s or %s.\
          """
              .formatted(
                  entityDescriptor.getEntityClass().getName(),
                  IndexShadowVariable.class.getSimpleName(),
                  variableMemberAccessor,
                  variableMemberAccessor.getType(),
                  IndexShadowVariable.class.getSimpleName(),
                  Integer.class,
                  Long.class));
    }
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
    String sourceVariableName =
        variableMemberAccessor.getAnnotation(IndexShadowVariable.class).sourceVariableName();
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
                  IndexShadowVariable.class.getSimpleName(),
                  variableMemberAccessor,
                  sourceVariableName,
                  candidates));
    }
    sourceVariableDescriptor = candidates.getFirst();
  }

  @Override
  public VariableDescriptor<Solution_> getSourceVariableDescriptor() {
    return sourceVariableDescriptor;
  }

  @Override
  public Collection<Class<?>> getUpdaterClasses() {
    throw new UnsupportedOperationException(
        "Impossible state: Handled by %s.".formatted(ListVariableState.class.getSimpleName()));
  }

  @SuppressWarnings("unchecked")
  @Override
  public Integer getValue(Object entity) {
    return super.getValue(entity);
  }
}
