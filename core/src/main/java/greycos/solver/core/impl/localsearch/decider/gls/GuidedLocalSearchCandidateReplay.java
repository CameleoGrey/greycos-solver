package greycos.solver.core.impl.localsearch.decider.gls;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

import greycos.solver.core.impl.move.PreparableMove;
import greycos.solver.core.preview.api.move.Move;

/** Frozen candidates belonging to one decision's unchanged working-state baseline. */
final class GuidedLocalSearchCandidateReplay<Solution_> {
  private final List<Selection<Solution_>> retained = new ArrayList<>();
  private int nextRetainedIndex;
  private boolean retainedFirst = true;

  void retain(Move<Solution_> move, boolean ordinaryOrigin) {
    // Keep filters wrapped for coordinator evaluation on every retry. No score or penalty is
    // cached.
    retained.add(new Selection<>(move, ordinaryOrigin, false));
  }

  Iterator<Selection<Solution_>> round(
      Iterator<Move<Solution_>> fresh, Predicate<Move<Solution_>> ordinaryOrigin) {
    // Entries discovered during this round only become eligible in the next round.
    int retainedSize = retained.size();
    boolean startWithRetained = retainedFirst;
    if (retainedSize > 0) retainedFirst = !retainedFirst;
    return new Iterator<>() {
      private int remaining = retainedSize;
      private boolean preferRetained = startWithRetained;

      @Override
      public boolean hasNext() {
        return remaining > 0 || fresh.hasNext();
      }

      @Override
      public Selection<Solution_> next() {
        if (remaining > 0 && (preferRetained || !fresh.hasNext())) {
          var selection = retained.get(nextRetainedIndex);
          nextRetainedIndex = (nextRetainedIndex + 1) % retainedSize;
          remaining--;
          preferRetained = false;
          return selection;
        }
        if (!fresh.hasNext()) throw new NoSuchElementException();
        var move = fresh.next();
        preferRetained = true;
        return new Selection<>(move, ordinaryOrigin.test(move), move instanceof PreparableMove);
      }
    };
  }

  record Selection<Solution_>(Move<Solution_> move, boolean ordinaryOrigin, boolean retainResult) {}
}
