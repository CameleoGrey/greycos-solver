package greycos.solver.core.impl.neighborhood.stream.enumerating.common;

import greycos.solver.core.impl.bavet.common.tuple.Tuple;

import org.jspecify.annotations.NullMarked;

@NullMarked
public abstract class AbstractLeftDataset<Solution_, Tuple_ extends Tuple>
    extends AbstractDataset<Solution_> {

  protected AbstractLeftDataset(AbstractEnumeratingStream<Solution_> parent) {
    super(parent);
  }

  public abstract AbstractLeftDatasetInstance<Solution_, Tuple_> instantiate(int entryStoreIndex);
}
