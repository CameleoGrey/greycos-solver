package ai.greycos.solver.core.preview.api.move;

import ai.greycos.solver.core.impl.move.DefaultMoveRunner;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface MoveRunner<Solution_> {

  static <Solution_> MoveRunner<Solution_> build(
      PlanningSolutionMetaModel<Solution_> solutionMetaModel) {
    return new DefaultMoveRunner<>(solutionMetaModel);
  }

  MoveRunContext<Solution_> using(Solution_ solution);
}
