package ai.greycos.solver.core.preview.api.neighborhood.test;

import ai.greycos.solver.core.impl.neighborhood.DefaultNeighborhoodTester;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;
import ai.greycos.solver.core.preview.api.neighborhood.MoveProvider;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface NeighborhoodTester<Solution_> {

  static <Solution_> NeighborhoodTester<Solution_> build(
      MoveProvider<Solution_> moveProvider,
      PlanningSolutionMetaModel<Solution_> solutionMetaModel) {
    return new DefaultNeighborhoodTester<>(moveProvider, solutionMetaModel);
  }

  NeighborhoodTestContext<Solution_> using(Solution_ solution);
}
