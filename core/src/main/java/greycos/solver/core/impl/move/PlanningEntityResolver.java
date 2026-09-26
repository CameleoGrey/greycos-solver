package greycos.solver.core.impl.move;

import java.util.SequencedCollection;

import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.SolutionView;

import org.jspecify.annotations.NullMarked;

/** Resolves the entities affected by a candidate against the current, pre-move solution. */
@NullMarked
public interface PlanningEntityResolver<Solution_> extends Move<Solution_> {

  SequencedCollection<Object> resolvePlanningEntities(SolutionView<Solution_> solutionView);

  static <Solution_> SequencedCollection<Object> resolve(
      Move<Solution_> move, SolutionView<Solution_> solutionView) {
    return move instanceof PlanningEntityResolver<Solution_> resolver
        ? resolver.resolvePlanningEntities(solutionView)
        : move.getPlanningEntities();
  }
}
