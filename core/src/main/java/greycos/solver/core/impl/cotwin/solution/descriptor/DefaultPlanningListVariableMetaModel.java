package greycos.solver.core.impl.cotwin.solution.descriptor;

import static greycos.solver.core.impl.cotwin.solution.descriptor.DefaultPlanningVariableMetaModel.VARIABLE_META_MODEL_COMPARATOR;

import java.util.Objects;

import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.preview.api.cotwin.metamodel.GenuineEntityMetaModel;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningListVariableMetaModel;
import greycos.solver.core.preview.api.cotwin.metamodel.VariableMetaModel;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record DefaultPlanningListVariableMetaModel<Solution_, Entity_, Value_>(
    GenuineEntityMetaModel<Solution_, Entity_> entity,
    ListVariableDescriptor<Solution_> variableDescriptor)
    implements PlanningListVariableMetaModel<Solution_, Entity_, Value_>,
        InnerGenuineVariableMetaModel<Solution_> {

  @SuppressWarnings("unchecked")
  @Override
  public Class<Value_> type() {
    return (Class<Value_>) variableDescriptor.getElementType();
  }

  @Override
  public String name() {
    return variableDescriptor.getVariableName();
  }

  @Override
  public boolean allowsUnassignedValues() {
    return variableDescriptor.allowsUnassignedValues();
  }

  @Override
  public boolean isValueRangeOnSolution() {
    return variableDescriptor.canExtractValueRangeFromSolution();
  }

  @Override
  public boolean equals(Object o) {
    // Do not use entity in equality checks;
    // If an entity is subclassed, that subclass will have it own distinct VariableMetaModel
    if (o instanceof DefaultPlanningListVariableMetaModel<?, ?, ?> that) {
      return Objects.equals(variableDescriptor, that.variableDescriptor);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(variableDescriptor);
  }

  @Override
  public int compareTo(VariableMetaModel<Solution_, Entity_, Value_> other) {
    return VARIABLE_META_MODEL_COMPARATOR.compare(this, other);
  }

  @Override
  public String toString() {
    return "Genuine List Variable '%s %s.%s' (allowsUnassignedValues: %b)"
        .formatted(type(), entity.getClass().getSimpleName(), name(), allowsUnassignedValues());
  }
}
