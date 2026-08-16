package greycos.solver.core.preview.api.neighborhood.stream;

import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.neighborhood.NeighborhoodSession;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface MoveStream<Solution_> {

  Iterable<Move<Solution_>> getMoveIterable(NeighborhoodSession neighborhoodSession);
}
