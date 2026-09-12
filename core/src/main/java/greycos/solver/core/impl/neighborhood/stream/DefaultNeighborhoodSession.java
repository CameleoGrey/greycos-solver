package greycos.solver.core.impl.neighborhood.stream;

import java.util.Objects;

import greycos.solver.core.impl.bavet.common.tuple.UniTuple;
import greycos.solver.core.impl.neighborhood.stream.enumerating.DatasetSession;
import greycos.solver.core.impl.neighborhood.stream.enumerating.common.AbstractLeftDataset;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.UniLeftDataset;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.UniLeftDatasetInstance;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.UniRightDataset;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.UniRightDatasetInstance;
import greycos.solver.core.preview.api.move.SolutionView;
import greycos.solver.core.preview.api.neighborhood.MoveIteratorSession;
import greycos.solver.core.preview.api.neighborhood.NeighborhoodSession;
import greycos.solver.core.preview.api.neighborhood.stream.dataset.BiDataset;
import greycos.solver.core.preview.api.neighborhood.stream.dataset.BiDatasetInstance;
import greycos.solver.core.preview.api.neighborhood.stream.dataset.UniDataset;
import greycos.solver.core.preview.api.neighborhood.stream.dataset.UniDatasetInstance;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DefaultNeighborhoodSession<Solution_>
    implements NeighborhoodSession, MoveIteratorSession<Solution_> {

  private final DatasetSession<Solution_> datasetSession;
  private final SolutionView<Solution_> solutionView;

  public DefaultNeighborhoodSession(
      DatasetSession<Solution_> datasetSession, SolutionView<Solution_> solutionView) {
    this.datasetSession = Objects.requireNonNull(datasetSession);
    this.solutionView = Objects.requireNonNull(solutionView);
  }

  public <A> UniLeftDatasetInstance<Solution_, A> getLeftDatasetInstance(
      UniLeftDataset<Solution_, A> dataset) {
    return datasetSession.getInstance((AbstractLeftDataset<Solution_, UniTuple<A>>) dataset);
  }

  public <A, B> UniRightDatasetInstance<Solution_, A, B> getRightDatasetInstance(
      UniRightDataset<Solution_, A, B> dataset) {
    return datasetSession.getInstance(dataset);
  }

  public void insert(Object fact) {
    datasetSession.insert(fact);
  }

  public void update(Object fact) {
    datasetSession.update(fact);
  }

  public void retract(Object fact) {
    datasetSession.retract(fact);
  }

  public void settle() {
    datasetSession.settle();
  }

  @Override
  public SolutionView<Solution_> getSolutionView() {
    return solutionView;
  }

  @Override
  public <A> UniDatasetInstance<A> getInstance(UniDataset<Solution_, A> dataset) {
    return datasetSession.getInstance(dataset);
  }

  @Override
  public <A, B> BiDatasetInstance<A, B> getInstance(BiDataset<Solution_, A, B> dataset) {
    return datasetSession.getInstance(dataset);
  }
}
