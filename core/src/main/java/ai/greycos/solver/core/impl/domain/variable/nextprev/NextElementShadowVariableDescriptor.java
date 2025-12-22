package ai.greycos.solver.core.impl.domain.variable.nextprev;

import java.util.Collection;

import ai.greycos.solver.core.api.domain.variable.NextElementShadowVariable;
import ai.greycos.solver.core.impl.domain.common.accessor.MemberAccessor;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.ListVariableStateSupply;
import ai.greycos.solver.core.impl.domain.variable.listener.VariableListenerWithSources;
import ai.greycos.solver.core.impl.domain.variable.supply.SupplyManager;

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
