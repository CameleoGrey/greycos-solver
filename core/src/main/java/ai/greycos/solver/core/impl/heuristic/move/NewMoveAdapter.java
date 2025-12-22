package ai.greycos.solver.core.impl.heuristic.move;

import java.util.Collection;
import java.util.Objects;

import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.move.MoveDirector;
import ai.greycos.solver.core.impl.move.VariableChangeRecordingScoreDirector;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.NullMarked;

/**
 * Adapts {@link Move a new move} to {@link ai.greycos.solver.core.impl.heuristic.move.Move a legacy
 * move}. Once the move selector framework is removed, this may be removed as well.
 *
 * @param newMove the move to adapt
 * @param <Solution_>
 */
@NullMarked
record NewMoveAdapter<Solution_>(Move<Solution_> newMove)
    implements ai.greycos.solver.core.impl.heuristic.move.Move<Solution_> {

  @Override
  public boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector) {
    return true; // New moves are always doable.
  }

  @Override
  public void doMoveOnly(ScoreDirector<Solution_> scoreDirector) {
    newMove.execute(getMoveDirector(scoreDirector));
  }

  private MoveDirector<Solution_, ?> getMoveDirector(ScoreDirector<Solution_> scoreDirector) {
    if (scoreDirector
        instanceof VariableChangeRecordingScoreDirector<Solution_, ?> recordingScoreDirector) {
      return recordingScoreDirector.getBacking().getMoveDirector();
    }
    return ((InnerScoreDirector<Solution_, ?>) scoreDirector).getMoveDirector();
  }

  @Override
  public ai.greycos.solver.core.impl.heuristic.move.Move<Solution_> rebase(
      ScoreDirector<Solution_> destinationScoreDirector) {
    return MoveAdapters.toLegacyMove(newMove.rebase(getMoveDirector(destinationScoreDirector)));
  }

  @Override
  public String getSimpleMoveTypeDescription() {
    return newMove.describe();
  }

  @Override
  public Collection<?> getPlanningEntities() {
    return newMove.getPlanningEntities();
  }

  @Override
  public Collection<?> getPlanningValues() {
    return newMove.getPlanningValues();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof NewMoveAdapter<?> other && Objects.equals(newMove, other.newMove);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(newMove);
  }

  @Override
  public String toString() {
    return "Adapted(%s)".formatted(newMove);
  }
}
