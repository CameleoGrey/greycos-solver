package ai.greycos.solver.core.impl.heuristic.move;

import java.util.Iterator;
import java.util.function.Predicate;

import ai.greycos.solver.core.impl.move.MoveDirector;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;
import ai.greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.NullMarked;

/**
 * While the Neighborhoods API and Move Selectors API need to coexist, there are places where moves
 * need to be converted between the old and new types. This class provides static methods to perform
 * these conversions.
 */
@NullMarked
public final class MoveAdapters {

  public static <Solution_> ai.greycos.solver.core.impl.heuristic.move.Move<Solution_> toLegacyMove(
      Move<Solution_> move) {
    if (move instanceof ai.greycos.solver.core.impl.heuristic.move.Move<Solution_> legacyMove) {
      return legacyMove;
    }
    return new NewMoveAdapter<>(move);
  }

  public static <Solution_> Move<Solution_> unadapt(Move<Solution_> possibleLegacyMove) {
    if (possibleLegacyMove instanceof NewMoveAdapter<Solution_> newMoveAdapter) {
      return newMoveAdapter.newMove();
    }
    return possibleLegacyMove;
  }

  public static <Solution_>
      Iterator<ai.greycos.solver.core.impl.heuristic.move.Move<Solution_>> toLegacyMoveIterator(
          Iterator<Move<Solution_>> moveIterator) {
    return new NewIteratorAdapter<>(moveIterator);
  }

  /**
   * Used to determine if a move is doable. A move is only doable if:
   *
   * <ul>
   *   <li>It is a non-selector-based preview {@link Move}.
   *   <li>It is a selector-based move and its {@link
   *       AbstractSelectorBasedMove#isMoveDoable(ScoreDirector)} returns {@code true}.
   * </ul>
   *
   * @param moveDirector never null
   * @param move never null
   * @return true if the move is doable
   */
  public static <Solution_> boolean isDoable(
      MoveDirector<Solution_, ?> moveDirector, Move<Solution_> move) {
    if (move instanceof AbstractSelectorBasedMove<Solution_> selectorBasedMove) {
      return selectorBasedMove.isMoveDoable(moveDirector.getScoreDirector());
    }
    return true;
  }

  public static <Solution_> boolean testWhenLegacyMove(
      Move<Solution_> move, Predicate<Move<Solution_>> predicate) {
    if (move instanceof ai.greycos.solver.core.impl.heuristic.move.Move<Solution_> legacyMove) {
      return predicate.test(legacyMove);
    }
    return false;
  }

  private MoveAdapters() {
    // No instantiation.
  }
}
