package ai.greycos.solver.core.impl.neighborhood.stream;

import ai.greycos.solver.core.preview.api.neighborhood.NeighborhoodSession;
import ai.greycos.solver.core.preview.api.neighborhood.stream.MoveStream;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface InnerMoveStream<Solution_> extends MoveStream<Solution_> {

  @Override
  MoveIterable<Solution_> getMoveIterable(NeighborhoodSession neighborhoodSession);
}
