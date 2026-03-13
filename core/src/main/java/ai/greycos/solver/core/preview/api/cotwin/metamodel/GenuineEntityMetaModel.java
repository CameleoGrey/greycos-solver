package ai.greycos.solver.core.preview.api.cotwin.metamodel;

import java.util.List;

import org.jspecify.annotations.NullMarked;

/**
 * Represents the meta-model of a genuine planning entity, an entity that has at least one genuine
 * planning variable, and may also have shadow variables.
 */
@NullMarked
public non-sealed interface GenuineEntityMetaModel<Solution_, Entity_>
    extends PlanningEntityMetaModel<Solution_, Entity_> {

  @SuppressWarnings({"unchecked", "rawtypes"})
  default List<GenuineVariableMetaModel<Solution_, Entity_, ?>> genuineVariables() {
    return (List)
        variables().stream()
            .filter(v -> v instanceof GenuineVariableMetaModel)
            .map(v -> (GenuineVariableMetaModel<Solution_, Entity_, ?>) v)
            .toList();
  }

  <Value_> GenuineVariableMetaModel<Solution_, Entity_, Value_> genuineVariable();

  <Value_> GenuineVariableMetaModel<Solution_, Entity_, Value_> genuineVariable(
      String variableName);

  <Value_> GenuineVariableMetaModel<Solution_, Entity_, Value_> genuineVariable(
      String variableName, Class<Value_> variableClass);

  <Value_> PlanningVariableMetaModel<Solution_, Entity_, Value_> basicVariable();

  <Value_> PlanningVariableMetaModel<Solution_, Entity_, Value_> basicVariable(String variableName);

  <Value_> PlanningVariableMetaModel<Solution_, Entity_, Value_> basicVariable(
      String variableName, Class<Value_> variableClass);

  <Value_> PlanningListVariableMetaModel<Solution_, Entity_, Value_> listVariable();

  <Value_> PlanningListVariableMetaModel<Solution_, Entity_, Value_> listVariable(
      String variableName);

  <Value_> PlanningListVariableMetaModel<Solution_, Entity_, Value_> listVariable(
      String variableName, Class<Value_> variableClass);

  <Value_> ShadowVariableMetaModel<Solution_, Entity_, Value_> shadowVariable(String variableName);

  <Value_> ShadowVariableMetaModel<Solution_, Entity_, Value_> shadowVariable(
      String variableName, Class<Value_> variableClass);
}
