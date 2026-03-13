package ai.greycos.solver.core.preview.api.cotwin.metamodel;

import java.util.List;

import org.jspecify.annotations.NullMarked;

/** Represents the meta-model of a shadow entity, an entity which only has shadow variables. */
@NullMarked
public non-sealed interface ShadowEntityMetaModel<Solution_, Entity_>
    extends PlanningEntityMetaModel<Solution_, Entity_> {

  @Override
  List<ShadowVariableMetaModel<Solution_, Entity_, ?>> variables();

  @SuppressWarnings("unchecked")
  @Override
  default <Value_> ShadowVariableMetaModel<Solution_, Entity_, Value_> variable(
      String variableName) {
    for (var variableMetaModel : variables()) {
      if (variableMetaModel.name().equals(variableName)) {
        return (ShadowVariableMetaModel<Solution_, Entity_, Value_>) variableMetaModel;
      }
    }
    throw new IllegalArgumentException(
        "The variableName (%s) does not exist in the variables (%s)."
            .formatted(variableName, variables()));
  }

  @SuppressWarnings("unchecked")
  @Override
  default <Value_> ShadowVariableMetaModel<Solution_, Entity_, Value_> variable(
      String variableName, Class<Value_> variableClass) {
    for (var variableMetaModel : variables()) {
      if (variableMetaModel.name().equals(variableName)) {
        if (!variableClass.isAssignableFrom(variableMetaModel.type())) {
          throw new IllegalArgumentException(
              "The variableName (%s) exists among variables (%s) but is not of type (%s)."
                  .formatted(variableName, variables(), variableClass.getCanonicalName()));
        }
        return (ShadowVariableMetaModel<Solution_, Entity_, Value_>) variableMetaModel;
      }
    }
    throw new IllegalArgumentException(
        "The variableName (%s) does not exist in the variables (%s)."
            .formatted(variableName, variables()));
  }
}
