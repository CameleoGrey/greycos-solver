package ai.greycos.solver.core.preview.api.move.builtin;

import java.util.List;

import ai.greycos.solver.core.impl.cotwin.solution.descriptor.DefaultPlanningListVariableMetaModel;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.DefaultPlanningVariableMetaModel;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.InnerVariableMetaModel;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.GenuineVariableMetaModel;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningListVariableMetaModel;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningVariableMetaModel;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.VariableMetaModel;
import ai.greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.NullMarked;

@NullMarked
public abstract class AbstractMove<Solution_> implements Move<Solution_> {

  private static final char OPENING_PARENTHESES = '(';
  private static final char CLOSING_PARENTHESES = ')';

  @Override
  public final String describe() {
    var metaModels = variableMetaModels();
    var substring =
        switch (metaModels.size()) {
          case 0 -> "";
          case 1 ->
              OPENING_PARENTHESES
                  + getVariableDescriptor(metaModels.get(0)).getSimpleEntityAndVariableName()
                  + CLOSING_PARENTHESES;
          default -> {
            var stringBuilder = new StringBuilder().append(OPENING_PARENTHESES);
            var first = true;
            for (var variableMetaModel : metaModels) {
              if (first) {
                first = false;
              } else {
                stringBuilder.append(", ");
              }
              stringBuilder.append(
                  getVariableDescriptor(variableMetaModel).getSimpleEntityAndVariableName());
            }
            stringBuilder.append(CLOSING_PARENTHESES);
            yield stringBuilder.toString();
          }
        };
    return getClass().getSimpleName() + substring;
  }

  public abstract String toString();

  public abstract List<? extends GenuineVariableMetaModel<Solution_, ?, ?>> variableMetaModels();

  @SuppressWarnings("unchecked")
  protected static <Solution_> VariableDescriptor<Solution_> getVariableDescriptor(
      VariableMetaModel<Solution_, ?, ?> variableMetaModel) {
    return ((InnerVariableMetaModel<Solution_>) variableMetaModel).variableDescriptor();
  }

  protected static <Solution_> GenuineVariableDescriptor<Solution_> getVariableDescriptor(
      PlanningVariableMetaModel<Solution_, ?, ?> variableMetaModel) {
    return ((DefaultPlanningVariableMetaModel<Solution_, ?, ?>) variableMetaModel)
        .variableDescriptor();
  }

  protected static <Solution_> ListVariableDescriptor<Solution_> getVariableDescriptor(
      PlanningListVariableMetaModel<Solution_, ?, ?> variableMetaModel) {
    return ((DefaultPlanningListVariableMetaModel<Solution_, ?, ?>) variableMetaModel)
        .variableDescriptor();
  }
}
