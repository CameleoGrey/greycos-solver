package ai.greycos.solver.core.preview.api.move;

import java.util.function.Consumer;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface MoveRunContext<Solution_> {

  void execute(Move<Solution_> move);

  void executeTemporarily(Move<Solution_> move, Consumer<SolutionView<Solution_>> callback);
}
