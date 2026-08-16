package greycos.solver.core.impl.heuristic.move;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedCollection;
import java.util.stream.Collectors;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;

import org.jspecify.annotations.NullMarked;

/**
 * A selector-generated composite move made up of one or more child moves.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public final class SelectorBasedCompositeMove<Solution_>
    extends AbstractSelectorBasedMove<Solution_> {

  @SafeVarargs
  public static <Solution_, Move_ extends Move<Solution_>> Move<Solution_> buildMove(
      Move_... moves) {
    return switch (moves.length) {
      case 0 -> SelectorBasedNoChangeMove.getInstance();
      case 1 -> moves[0];
      default -> new SelectorBasedCompositeMove<>(moves);
    };
  }

  @SuppressWarnings("unchecked")
  public static <Solution_, Move_ extends Move<Solution_>> Move<Solution_> buildMove(
      List<Move_> moveList) {
    return buildMove(moveList.toArray(new Move[0]));
  }

  private final Move<Solution_>[] moves;

  @SafeVarargs
  SelectorBasedCompositeMove(Move<Solution_>... moves) {
    this.moves = moves;
  }

  public Move<Solution_>[] getMoves() {
    return moves;
  }

  @Override
  public boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector) {
    for (var move : moves) {
      if (!(move instanceof AbstractSelectorBasedMove<Solution_> selectorBasedMove)
          || selectorBasedMove.isMoveDoable(scoreDirector)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void execute(
      MutableSolutionView<Solution_> solutionView,
      VariableDescriptorAwareScoreDirector<Solution_> scoreDirector) {
    for (var move : moves) {
      if (move instanceof AbstractSelectorBasedMove<Solution_> selectorBasedMove) {
        if (selectorBasedMove.isMoveDoable(scoreDirector)) {
          selectorBasedMove.execute(solutionView, scoreDirector);
        }
      } else {
        // Keep preview child mutations on the active solution view, including its undo recorder.
        move.execute(solutionView);
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public SelectorBasedCompositeMove<Solution_> rebase(Lookup lookup) {
    var rebasedMoves = new Move[moves.length];
    for (var i = 0; i < moves.length; i++) {
      rebasedMoves[i] = moves[i].rebase(lookup);
    }
    return new SelectorBasedCompositeMove<>(rebasedMoves);
  }

  @Override
  public String describe() {
    return "CompositeMove"
        + Arrays.stream(moves)
            .map(Move::describe)
            .sorted()
            .map(childMoveTypeDescription -> "* " + childMoveTypeDescription)
            .collect(Collectors.joining(",", "(", ")"));
  }

  @Override
  public SequencedCollection<Object> getPlanningEntities() {
    var entities = LinkedHashSet.newLinkedHashSet(moves.length * 2);
    for (var move : moves) {
      entities.addAll(move.getPlanningEntities());
    }
    return entities;
  }

  @Override
  public SequencedCollection<Object> getPlanningValues() {
    var values = LinkedHashSet.newLinkedHashSet(moves.length * 2);
    for (var move : moves) {
      values.addAll(move.getPlanningValues());
    }
    return values;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof SelectorBasedCompositeMove<?> otherCompositeMove
        && Arrays.equals(moves, otherCompositeMove.moves);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(moves);
  }

  @Override
  public String toString() {
    return Arrays.toString(moves);
  }
}
