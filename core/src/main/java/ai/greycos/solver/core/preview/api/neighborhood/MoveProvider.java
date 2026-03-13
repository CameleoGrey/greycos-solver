package ai.greycos.solver.core.preview.api.neighborhood;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.preview.api.neighborhood.stream.MoveStream;
import ai.greycos.solver.core.preview.api.neighborhood.stream.MoveStreamFactory;

import org.jspecify.annotations.NullMarked;

/**
 * Implement this to provide a definition for one move type.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public interface MoveProvider<Solution_> {

  MoveStream<Solution_> build(MoveStreamFactory<Solution_> moveStreamFactory);
}
