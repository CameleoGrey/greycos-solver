package greycos.solver.core.preview.api.cotwin.metamodel;

import java.util.List;

import org.jspecify.annotations.NullMarked;

/** Represents the meta-model of a shadow entity, an entity which only has shadow variables. */
@NullMarked
public non-sealed interface ShadowEntityMetaModel<Solution_, Entity_>
    extends PlanningEntityMetaModel<Solution_, Entity_> {

  @Override
  List<ShadowVariableMetaModel<Solution_, Entity_, ?>> variables();

  /**
   * Returns a {@link ShadowVariableMetaModel} for a variable with the given name.
   *
   * @return A variable declared by the entity.
   */
  @Override
  default <Value_> ShadowVariableMetaModel<Solution_, Entity_, Value_> variable(
      String variableName) {
    return (ShadowVariableMetaModel<Solution_, Entity_, Value_>)
        PlanningEntityMetaModel.super.<Value_>variable(variableName);
  }

  /**
   * As defined by {@link #variable(String)}, but only succeeds if the variable is of a given type.
   *
   * @return A variable declared by the entity.
   */
  @Override
  default <Value_> ShadowVariableMetaModel<Solution_, Entity_, Value_> variable(
      String variableName, Class<Value_> variableClass) {
    return (ShadowVariableMetaModel<Solution_, Entity_, Value_>)
        PlanningEntityMetaModel.super.variable(variableName, variableClass);
  }
}
