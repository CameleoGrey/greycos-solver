package greycos.solver.core.impl.move;

import java.util.SequencedCollection;
import java.util.function.BiConsumer;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;
import greycos.solver.core.preview.api.move.SolutionView;

import org.jspecify.annotations.Nullable;

/** Defers user selection filters until the coordinator can inspect the realized candidate. */
public final class PreparedMoveFilters {

  private PreparedMoveFilters() {}

  public static <Solution_> PreparableMove<Solution_> defer(
      PreparableMove<Solution_> request, SelectionFilter<Solution_, Move<Solution_>> filter) {
    if (request instanceof FilteredRequest<Solution_> filtered) {
      return new FilteredRequest<>(
          filtered.delegate, SelectionFilter.compose(filtered.filter, filter));
    }
    return new FilteredRequest<>(request, filter);
  }

  /**
   * Called only by the coordinator, after the evaluation baseline has been restored. Returns the
   * unwrapped move, or null when a filter rejects it. Filters never run on worker threads.
   */
  public static <Solution_> @Nullable Move<Solution_> filter(
      Move<Solution_> move, ScoreDirector<Solution_> scoreDirector) {
    if (move instanceof FilteredResult<Solution_> filtered) {
      var delegate = filter(filtered.delegate, scoreDirector);
      return delegate != null && filtered.filter.accept(scoreDirector, delegate) ? delegate : null;
    }
    return move;
  }

  private record FilteredRequest<Solution_>(
      PreparableMove<Solution_> delegate, SelectionFilter<Solution_, Move<Solution_>> filter)
      implements PreparableMove<Solution_> {

    @Override
    public <Score_ extends Score<Score_>> PreparedMoveEvaluation<Solution_, Score_> prepare(
        InnerScoreDirector<Solution_, Score_> director,
        Runnable checkTermination,
        boolean assertFromScratch,
        BiConsumer<SolutionView<Solution_>, Move<Solution_>> finalStateConsumer) {
      var result =
          delegate.prepare(director, checkTermination, assertFromScratch, finalStateConsumer);
      if (result.status() != PreparedMoveEvaluation.Status.EVALUATED) {
        return result;
      }
      return new PreparedMoveEvaluation<>(
          result.status(),
          new FilteredResult<>(result.move(), filter),
          result.score(),
          result.calculationCount());
    }

    @Override
    public void execute(MutableSolutionView<Solution_> solutionView) {
      throw new IllegalStateException(
          "A filtered multistage request must be prepared before execution.");
    }

    @Override
    public PreparableMove<Solution_> rebase(Lookup lookup) {
      return new FilteredRequest<>((PreparableMove<Solution_>) delegate.rebase(lookup), filter);
    }

    @Override
    public Object cleanupKey() {
      return delegate.cleanupKey();
    }

    @Override
    public void closeEvaluationContext(InnerScoreDirector<Solution_, ?> director) {
      delegate.closeEvaluationContext(director);
    }

    @Override
    public String describe() {
      return delegate.describe();
    }

    @Override
    public String toString() {
      return delegate.toString();
    }
  }

  private record FilteredResult<Solution_>(
      Move<Solution_> delegate, SelectionFilter<Solution_, Move<Solution_>> filter)
      implements Move<Solution_> {

    @Override
    public void execute(MutableSolutionView<Solution_> solutionView) {
      delegate.execute(solutionView);
    }

    @Override
    public Move<Solution_> rebase(Lookup lookup) {
      return new FilteredResult<>(delegate.rebase(lookup), filter);
    }

    @Override
    public SequencedCollection<Object> getPlanningEntities() {
      return delegate.getPlanningEntities();
    }

    @Override
    public SequencedCollection<Object> getPlanningValues() {
      return delegate.getPlanningValues();
    }

    @Override
    public String describe() {
      return delegate.describe();
    }

    @Override
    public String toString() {
      return delegate.toString();
    }
  }
}
