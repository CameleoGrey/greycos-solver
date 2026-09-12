package greycos.solver.core.impl.move;

import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;

import org.jspecify.annotations.NullMarked;

@NullMarked
record RecordedUndoMove<Solution_>(List<ChangeAction<Solution_>> variableChangeActionList)
    implements Move<Solution_> {

  RecordedUndoMove(List<ChangeAction<Solution_>> variableChangeActionList) {
    this.variableChangeActionList = Objects.requireNonNull(variableChangeActionList);
  }

  @Override
  public void execute(MutableSolutionView<Solution_> solutionView) {
    var scoreDirector = ((InnerMutableSolutionView<Solution_>) solutionView).getScoreDirector();
    // Undo actions must be replayed in reverse recording order,
    // otherwise repeated changes to the same variable restore a stale value
    // and multi-action list windows replay out of order.
    for (var i = variableChangeActionList.size() - 1; i >= 0; i--) {
      variableChangeActionList.get(i).undo(scoreDirector);
    }
  }

  @Override
  public Move<Solution_> rebase(Lookup lookup) {
    return new RecordedUndoMove<>(
        variableChangeActionList.stream()
            .map(changeAction -> changeAction.rebase(lookup))
            .toList());
  }

  @Override
  public String toString() {
    return "Undo(%s)".formatted(variableChangeActionList);
  }
}
