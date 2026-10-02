package greycos.solver.core.impl.move;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.move.decorator.FilteringMoveSelector;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;
import greycos.solver.core.preview.api.move.SolutionView;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class PreparedMoveFiltersTest {

  @Test
  void filtersRunOnCoordinatorAfterWorkerPreparationAndRebase() throws Exception {
    InnerScoreDirector<TestdataSolution, SimpleScore> workerDirector =
        mock(InnerScoreDirector.class);
    InnerScoreDirector<TestdataSolution, SimpleScore> coordinatorDirector =
        mock(InnerScoreDirector.class);
    Move<TestdataSolution> workerMove = mock(Move.class);
    Move<TestdataSolution> coordinatorMove = mock(Move.class);
    var lookup = mock(Lookup.class);
    when(workerMove.rebase(lookup)).thenReturn(coordinatorMove);
    var calls = new AtomicInteger();
    var coordinatorThread = Thread.currentThread();
    var request = new Request(workerMove, PreparedMoveEvaluation.Status.EVALUATED);
    var deferred =
        PreparedMoveFilters.defer(
            request,
            (director, move) -> {
              assertThat(Thread.currentThread()).isSameAs(coordinatorThread);
              assertThat(director).isSameAs(coordinatorDirector);
              assertThat(move).isSameAs(coordinatorMove);
              calls.incrementAndGet();
              return true;
            });
    PreparedMoveEvaluation<TestdataSolution, SimpleScore> prepared;
    try (var executor = Executors.newSingleThreadExecutor()) {
      prepared =
          executor
              .submit(
                  () ->
                      deferred.prepare(
                          workerDirector,
                          () -> {},
                          false,
                          (view, move) -> assertThat(move).isSameAs(workerMove)))
              .get();
    }
    assertThat(calls).hasValue(0);
    assertThat(prepared.calculationCount()).isEqualTo(7);
    var rebased = prepared.move().rebase(lookup);
    assertThat(PreparedMoveFilters.filter(rebased, coordinatorDirector)).isSameAs(coordinatorMove);
    assertThat(calls).hasValue(1);
  }

  @Test
  void requestSelectionDefersFiltersAndRetainsFiniteExhaustion() {
    var request = new Request(mock(Move.class), PreparedMoveEvaluation.Status.EVALUATED);
    var calls = new AtomicInteger();
    var child = SelectorTestUtils.<TestdataSolution>mockMoveSelector();
    when(child.iterator())
        .thenAnswer(ignored -> List.<Move<TestdataSolution>>of(request).iterator());
    when(child.getSize()).thenReturn(1L);
    var filtered =
        FilteringMoveSelector.of(
            child,
            (director, move) -> {
              calls.incrementAndGet();
              return false;
            });
    var iterator = filtered.iterator();
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isInstanceOf(PreparableMove.class);
    assertThat(iterator.hasNext()).isFalse();
    assertThat(calls).hasValue(0);
  }

  @Test
  void nestedFiltersRejectWithoutPassingRequestToUser() {
    Move<TestdataSolution> realized = mock(Move.class);
    var request = new Request(realized, PreparedMoveEvaluation.Status.EVALUATED);
    var calls = new ArrayList<String>();
    var first =
        PreparedMoveFilters.defer(
            request,
            (director, move) -> {
              assertThat(move).isSameAs(realized);
              calls.add("inner");
              return true;
            });
    var outer =
        PreparedMoveFilters.defer(
            first,
            (director, move) -> {
              assertThat(move).isSameAs(realized);
              calls.add("outer");
              return false;
            });
    InnerScoreDirector<TestdataSolution, SimpleScore> director = mock(InnerScoreDirector.class);
    var prepared = outer.prepare(director, () -> {}, false, (view, move) -> {});
    assertThat(PreparedMoveFilters.filter(prepared.move(), director)).isNull();
    assertThat(calls).containsExactly("inner", "outer");
    assertThat(outer.cleanupKey()).isSameAs(request.cleanupKey());
    outer.closeEvaluationContext(director);
    assertThat(request.closes).isEqualTo(1);
  }

  @Test
  void emptyPreparationDoesNotRunFilters() {
    var request = new Request(mock(Move.class), PreparedMoveEvaluation.Status.EMPTY);
    var deferred =
        PreparedMoveFilters.defer(
            request,
            (director, move) -> {
              throw new AssertionError("An empty candidate must not reach a filter.");
            });
    InnerScoreDirector<TestdataSolution, SimpleScore> director = mock(InnerScoreDirector.class);
    var result = deferred.prepare(director, () -> {}, false, (view, move) -> {});
    assertThat(result.status()).isEqualTo(PreparedMoveEvaluation.Status.EMPTY);
    assertThat(result.move()).isNull();
    assertThat(result.calculationCount()).isEqualTo(7);
  }

  private static final class Request implements PreparableMove<TestdataSolution> {
    private final Move<TestdataSolution> result;
    private final PreparedMoveEvaluation.Status status;
    private int closes;

    private Request(Move<TestdataSolution> result, PreparedMoveEvaluation.Status status) {
      this.result = result;
      this.status = status;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <Score_ extends Score<Score_>> PreparedMoveEvaluation<TestdataSolution, Score_> prepare(
        InnerScoreDirector<TestdataSolution, Score_> director,
        Runnable checkTermination,
        boolean assertFromScratch,
        BiConsumer<SolutionView<TestdataSolution>, Move<TestdataSolution>> consumer) {
      if (status != PreparedMoveEvaluation.Status.EVALUATED) {
        return new PreparedMoveEvaluation<>(status, null, null, 7);
      }
      consumer.accept(director.getMoveDirector(), result);
      return new PreparedMoveEvaluation<>(
          status, result, InnerScore.fullyAssigned((Score_) SimpleScore.ZERO), 7);
    }

    @Override
    public void execute(MutableSolutionView<TestdataSolution> view) {
      throw new AssertionError("Only the prepared result may execute.");
    }

    @Override
    public Move<TestdataSolution> rebase(Lookup lookup) {
      return this;
    }

    @Override
    public void closeEvaluationContext(InnerScoreDirector<TestdataSolution, ?> director) {
      closes++;
    }
  }
}
