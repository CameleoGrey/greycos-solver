package greycos.solver.core.preview.api.move;

import greycos.solver.core.impl.move.DefaultMoveRunner;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface MoveRunner<Solution_> {

  static <Solution_> MoveRunner<Solution_> build(
      PlanningSolutionMetaModel<Solution_> solutionMetaModel) {
    return new DefaultMoveRunner<>(solutionMetaModel);
  }

  MoveRunContext<Solution_> using(Solution_ solution);
}
