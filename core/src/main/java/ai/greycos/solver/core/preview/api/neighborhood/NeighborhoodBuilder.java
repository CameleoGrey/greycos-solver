package ai.greycos.solver.core.preview.api.neighborhood;

import ai.greycos.solver.core.preview.api.domain.metamodel.PlanningSolutionMetaModel;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface NeighborhoodBuilder<Solution_> {

  PlanningSolutionMetaModel<Solution_> getSolutionMetaModel();

  NeighborhoodBuilder<Solution_> add(MoveDefinition<Solution_> moveDefinition);

  Neighborhood build();
}
