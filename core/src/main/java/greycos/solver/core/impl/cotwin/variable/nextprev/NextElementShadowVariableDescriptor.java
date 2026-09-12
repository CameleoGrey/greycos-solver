package greycos.solver.core.impl.cotwin.variable.nextprev;

import java.util.Collection;

import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.ListVariableStateSupply;

public final class NextElementShadowVariableDescriptor<Solution_>
    extends AbstractNextPrevElementShadowVariableDescriptor<Solution_> {

  public NextElementShadowVariableDescriptor(
      int ordinal,
      EntityDescriptor<Solution_> entityDescriptor,
      MemberAccessor variableMemberAccessor) {
    super(ordinal, entityDescriptor, variableMemberAccessor);
  }

  @Override
  String getSourceVariableName() {
    return variableMemberAccessor
        .getAnnotation(NextElementShadowVariable.class)
        .sourceVariableName();
  }

  @Override
  String getAnnotationName() {
    return NextElementShadowVariable.class.getSimpleName();
  }

  @Override
  public Collection<Class<?>> getUpdaterClasses() {
    throw new UnsupportedOperationException(
        "Impossible state: Handled by %s."
            .formatted(ListVariableStateSupply.class.getSimpleName()));
  }
}
