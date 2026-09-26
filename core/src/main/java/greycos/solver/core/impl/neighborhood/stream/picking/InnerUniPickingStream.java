package greycos.solver.core.impl.neighborhood.stream.picking;

import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.UniLeftDataset;
import greycos.solver.core.preview.api.neighborhood.stream.picking.UniPickingStream;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface InnerUniPickingStream<Solution_, A>
    extends InnerPickingStream<Solution_>, UniPickingStream<Solution_, A> {

  @Override
  UniLeftDataset<Solution_, A> getDataset();
}
