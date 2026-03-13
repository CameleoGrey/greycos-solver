package ai.greycos.solver.core.preview.api.move.test;

import java.util.function.Consumer;

import ai.greycos.solver.core.preview.api.move.Move;
import ai.greycos.solver.core.preview.api.move.SolutionView;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface MoveTestContext<Solution_> {

  void execute(Move<Solution_> move);

  void executeTemporarily(Move<Solution_> move, Consumer<SolutionView<Solution_>> callback);
}
