package greycos.solver.core.impl.neighborhood.stream.picking;

import greycos.solver.core.impl.neighborhood.stream.enumerating.common.AbstractDataset;
import greycos.solver.core.preview.api.neighborhood.stream.picking.PickingStream;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface InnerPickingStream<Solution_> extends PickingStream {

  AbstractDataset<Solution_> getDataset();
}
