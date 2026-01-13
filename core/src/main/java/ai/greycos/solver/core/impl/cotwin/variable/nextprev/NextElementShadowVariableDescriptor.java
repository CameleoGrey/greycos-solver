package ai.greycos.solver.core.impl.cotwin.variable.nextprev;

import java.util.Collection;

import ai.greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import ai.greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.ListVariableStateSupply;
import ai.greycos.solver.core.impl.cotwin.variable.listener.VariableListenerWithSources;
import ai.greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;

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
  public Collection<Class<?>> getVariableListenerClasses() {
    throw new UnsupportedOperationException(
        "Impossible state: Handled by %s."
            .formatted(ListVariableStateSupply.class.getSimpleName()));
  }

  @Override
  public Iterable<VariableListenerWithSources> buildVariableListeners(SupplyManager supplyManager) {
    throw new UnsupportedOperationException(
        "Impossible state: Handled by %s."
            .formatted(ListVariableStateSupply.class.getSimpleName()));
  }
}
