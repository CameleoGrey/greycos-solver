package ai.greycos.solver.core.impl.neighborhood.stream;

import ai.greycos.solver.core.impl.neighborhood.stream.enumerating.uni.UniLeftDataset;
import ai.greycos.solver.core.impl.neighborhood.stream.enumerating.uni.UniLeftDatasetInstance;
import ai.greycos.solver.core.preview.api.move.Move;
import ai.greycos.solver.core.preview.api.neighborhood.UniMoveConstructor;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record UniMoveStreamContext<Solution_, A>(
    DefaultNeighborhoodSession<Solution_> neighborhoodSession,
    UniLeftDataset<Solution_, A> dataset,
    UniMoveConstructor<Solution_, A> moveConstructor) {

  public UniLeftDatasetInstance<Solution_, A> getDatasetInstance() {
    return neighborhoodSession.getLeftDatasetInstance(dataset);
  }

  public Move<Solution_> buildMove(@Nullable A a) {
    return moveConstructor.apply(neighborhoodSession.getSolutionView(), a);
  }
}
