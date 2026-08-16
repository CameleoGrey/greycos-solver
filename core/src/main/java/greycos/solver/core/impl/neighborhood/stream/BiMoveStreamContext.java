package greycos.solver.core.impl.neighborhood.stream;

import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.UniLeftDataset;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.UniLeftDatasetInstance;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.UniRightDataset;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.UniRightDatasetInstance;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.neighborhood.BiMoveConstructor;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record BiMoveStreamContext<Solution_, A, B>(
    DefaultNeighborhoodSession<Solution_> neighborhoodSession,
    UniLeftDataset<Solution_, A> leftDataset,
    UniRightDataset<Solution_, A, B> rightDataset,
    BiMoveConstructor<Solution_, A, B> moveConstructor) {

  public UniLeftDatasetInstance<Solution_, A> getLeftDatasetInstance() {
    return neighborhoodSession.getLeftDatasetInstance(leftDataset);
  }

  public UniRightDatasetInstance<Solution_, A, B> getRightDatasetInstance() {
    return neighborhoodSession.getRightDatasetInstance(rightDataset);
  }

  public Move<Solution_> buildMove(@Nullable A left, @Nullable B right) {
    return moveConstructor.apply(neighborhoodSession.getSolutionView(), left, right);
  }
}
