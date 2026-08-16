package greycos.solver.core.impl.neighborhood.stream.enumerating.uni;

import greycos.solver.core.impl.bavet.common.tuple.UniTuple;
import greycos.solver.core.impl.neighborhood.stream.enumerating.common.AbstractDataset;
import greycos.solver.core.impl.neighborhood.stream.enumerating.common.AbstractLeftDatasetInstance;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class UniLeftDatasetInstance<Solution_, A>
    extends AbstractLeftDatasetInstance<Solution_, UniTuple<A>> {

  public UniLeftDatasetInstance(AbstractDataset<Solution_> parent, int entryStoreIndex) {
    super(parent, entryStoreIndex);
  }
}
