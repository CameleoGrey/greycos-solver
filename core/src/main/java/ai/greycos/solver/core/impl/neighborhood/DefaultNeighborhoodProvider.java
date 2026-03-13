package ai.greycos.solver.core.impl.neighborhood;

import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningListVariableMetaModel;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningVariableMetaModel;
import ai.greycos.solver.core.preview.api.move.builtin.ChangeMoveProvider;
import ai.greycos.solver.core.preview.api.move.builtin.ListChangeMoveProvider;
import ai.greycos.solver.core.preview.api.move.builtin.ListSwapMoveProvider;
import ai.greycos.solver.core.preview.api.move.builtin.SwapMoveProvider;
import ai.greycos.solver.core.preview.api.neighborhood.Neighborhood;
import ai.greycos.solver.core.preview.api.neighborhood.NeighborhoodBuilder;
import ai.greycos.solver.core.preview.api.neighborhood.NeighborhoodProvider;

import org.jspecify.annotations.NullMarked;

/**
 * Currently only includes change and swap moves.
 *
 * @param <Solution_>
 */
@NullMarked
public final class DefaultNeighborhoodProvider<Solution_>
    implements NeighborhoodProvider<Solution_> {

  @Override
  public Neighborhood defineNeighborhood(NeighborhoodBuilder<Solution_> builder) {
    var solutionMetaModel = builder.getSolutionMetaModel();
    for (var entityMetaModel : solutionMetaModel.genuineEntities()) {
      var hasBasicVariable = false;
      for (var variableMetaModel : entityMetaModel.genuineVariables()) {
        if (variableMetaModel
            instanceof PlanningListVariableMetaModel<Solution_, ?, ?> listVariableMetaModel) {
          builder.add(new ListChangeMoveProvider<>(listVariableMetaModel));
          builder.add(new ListSwapMoveProvider<>(listVariableMetaModel));
        } else if (variableMetaModel
            instanceof PlanningVariableMetaModel<Solution_, ?, ?> basicVariableMetaModel) {
          hasBasicVariable = true;
          builder.add(new ChangeMoveProvider<>(basicVariableMetaModel));
        }
      }
      if (hasBasicVariable) {
        builder.add(new SwapMoveProvider<>(entityMetaModel));
      }
    }
    return builder.build();
  }
}
