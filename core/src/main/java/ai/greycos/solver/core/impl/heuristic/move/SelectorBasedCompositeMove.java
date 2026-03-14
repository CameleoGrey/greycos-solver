package ai.greycos.solver.core.impl.heuristic.move;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;

import org.jspecify.annotations.NullMarked;

/**
 * Selector-based composite move name used by the upstream refactor.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public final class SelectorBasedCompositeMove<Solution_> extends CompositeMove<Solution_> {

  @SafeVarargs
  public static <Solution_, Move_ extends Move<Solution_>> Move<Solution_> buildMove(
      Move_... moves) {
    return switch (moves.length) {
      case 0 -> SelectorBasedNoChangeMove.getInstance();
      case 1 -> moves[0];
      default -> new SelectorBasedCompositeMove<>(moves);
    };
  }

  @SafeVarargs
  SelectorBasedCompositeMove(Move<Solution_>... moves) {
    super(moves);
  }

  @SuppressWarnings("unchecked")
  public static <Solution_, Move_ extends Move<Solution_>> Move<Solution_> buildMove(
      List<Move_> moveList) {
    return buildMove(moveList.toArray(new Move[0]));
  }

  @Override
  public SelectorBasedCompositeMove<Solution_> rebase(
      ScoreDirector<Solution_> destinationScoreDirector) {
    var moves = getMoves();
    Move<Solution_>[] rebasedMoves = new Move[moves.length];
    for (int i = 0; i < moves.length; i++) {
      rebasedMoves[i] = moves[i].rebase(destinationScoreDirector);
    }
    return new SelectorBasedCompositeMove<>(rebasedMoves);
  }
}
