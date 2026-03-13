package ai.greycos.solver.core.preview.api.neighborhood.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import ai.greycos.solver.core.preview.api.move.Move;
import ai.greycos.solver.core.preview.api.move.test.MoveTestContext;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface NeighborhoodTestContext<Solution_> {

  default Iterator<Move<Solution_>> getMovesAsIterator() {
    return getMovesAsIterator(Function.identity());
  }

  default Stream<Move<Solution_>> getMovesAsStream() {
    var iterator = getMovesAsIterator();
    Iterable<Move<Solution_>> iterable = () -> iterator;
    return StreamSupport.stream(iterable.spliterator(), false);
  }

  default List<Move<Solution_>> getMovesAsList() {
    return getMovesAsList(Function.identity());
  }

  <Move_ extends Move<Solution_>> Iterator<Move_> getMovesAsIterator(
      Function<Move<Solution_>, Move_> moveCaster);

  default <Move_ extends Move<Solution_>> Stream<Move_> getMovesAsStream(
      Function<Move<Solution_>, Move_> moveCaster) {
    var iterator = getMovesAsIterator(moveCaster);
    Iterable<Move_> iterable = () -> iterator;
    return StreamSupport.stream(iterable.spliterator(), false);
  }

  default <Move_ extends Move<Solution_>> List<Move_> getMovesAsList(
      Function<Move<Solution_>, Move_> moveCaster) {
    var moveIterator = getMovesAsIterator(moveCaster);
    var result = new ArrayList<Move_>();
    while (moveIterator.hasNext()) {
      result.add(moveIterator.next());
    }
    return result.isEmpty() ? Collections.emptyList() : result;
  }

  MoveTestContext<Solution_> getMoveTestContext();
}
