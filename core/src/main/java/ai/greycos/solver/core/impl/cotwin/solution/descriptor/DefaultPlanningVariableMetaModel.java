package ai.greycos.solver.core.impl.cotwin.solution.descriptor;

import java.util.Objects;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningEntityMetaModel;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningVariableMetaModel;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record DefaultPlanningVariableMetaModel<Solution_, Entity_, Value_>(
    PlanningEntityMetaModel<Solution_, Entity_> entity,
    BasicVariableDescriptor<Solution_> variableDescriptor)
    implements PlanningVariableMetaModel<Solution_, Entity_, Value_>,
        InnerGenuineVariableMetaModel<Solution_> {

  @SuppressWarnings("unchecked")
  @Override
  public Class<Value_> type() {
    return (Class<Value_>) variableDescriptor.getVariablePropertyType();
  }

  @Override
  public String name() {
    return variableDescriptor.getVariableName();
  }

  @Override
  public boolean allowsUnassigned() {
    return variableDescriptor.allowsUnassigned();
  }

  @Override
  public boolean equals(Object o) {
    // Do not use entity in equality checks;
    // If an entity is subclassed, that subclass will have it
    // own distinct VariableMetaModel
    if (o instanceof DefaultPlanningVariableMetaModel<?, ?, ?> that) {
      return Objects.equals(variableDescriptor, that.variableDescriptor);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(variableDescriptor);
  }

  @Override
  public String toString() {
    return "Genuine Variable '%s %s.%s' (allowsUnassigned: %b)"
        .formatted(type(), entity.getClass().getSimpleName(), name(), allowsUnassigned());
  }
}
