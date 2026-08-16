package greycos.solver.core.preview.api.neighborhood;

import greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface NeighborhoodBuilder<Solution_> {

  PlanningSolutionMetaModel<Solution_> getSolutionMetaModel();

  NeighborhoodBuilder<Solution_> add(MoveProvider<Solution_> moveProvider);

  Neighborhood build();
}
