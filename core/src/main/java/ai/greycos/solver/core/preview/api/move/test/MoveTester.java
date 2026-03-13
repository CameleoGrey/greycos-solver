package ai.greycos.solver.core.preview.api.move.test;

import ai.greycos.solver.core.impl.move.DefaultMoveTester;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface MoveTester<Solution_> {

  static <Solution_> MoveTester<Solution_> build(
      PlanningSolutionMetaModel<Solution_> solutionMetaModel) {
    return new DefaultMoveTester<>(solutionMetaModel);
  }

  MoveTestContext<Solution_> using(Solution_ solution);
}
