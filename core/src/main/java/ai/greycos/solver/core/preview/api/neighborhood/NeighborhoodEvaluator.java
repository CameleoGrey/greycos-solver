package ai.greycos.solver.core.preview.api.neighborhood;

import ai.greycos.solver.core.impl.neighborhood.DefaultNeighborhoodEvaluator;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface NeighborhoodEvaluator<Solution_> {

  static <Solution_> NeighborhoodEvaluator<Solution_> build(
      MoveProvider<Solution_> moveProvider,
      PlanningSolutionMetaModel<Solution_> solutionMetaModel) {
    return new DefaultNeighborhoodEvaluator<>(moveProvider, solutionMetaModel);
  }

  NeighborhoodEvaluationContext<Solution_> using(Solution_ solution);
}
