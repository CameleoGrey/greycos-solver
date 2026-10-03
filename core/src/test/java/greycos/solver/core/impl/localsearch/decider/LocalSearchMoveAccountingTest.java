package greycos.solver.core.impl.localsearch.decider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BiConsumer;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.localsearch.decider.acceptor.AbstractAcceptor;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.move.PreparableMove;
import greycos.solver.core.impl.move.PreparedMoveEvaluation;
import greycos.solver.core.impl.move.PreparedMoveFilters;
import greycos.solver.core.impl.neighborhood.MoveSelectorBasedMoveRepository;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;
import greycos.solver.core.preview.api.move.SolutionView;

import org.junit.jupiter.api.Test;

class LocalSearchMoveAccountingTest {

  @Test
  void countsAcceptedRejectedAndStructurallyInvalidEvaluationsAfterAcceptorFeedback() {
    var fixture = new Fixture();
    assertThat(fixture.evaluate(new Candidate("accepted"))).isTrue();
    assertThat(fixture.evaluate(new Candidate("rejected"))).isFalse();
    assertThat(fixture.evaluate(new Candidate("invalid"))).isFalse();
    assertThat(fixture.solver.getMoveEvaluationCount()).isEqualTo(3L);
    assertThat(fixture.feedbackCounts).containsExactly(0L, 1L, 2L);
    assertThat(fixture.foragerCounts).containsExactly(1L, 2L, 3L);
    assertThat(fixture.solver.getMoveEvaluationCountPerType())
        .containsEntry("accepted", 1L)
        .containsEntry("rejected", 1L)
        .containsEntry("invalid", 1L);
  }

  @Test
  void countsOnlyPreparedResultsThatReachForagingAndUsesTheRealizedMoveType() {
    var fixture = new Fixture();
    var realized = new Candidate("realized");
    var request =
        new Request(
            new PreparedMoveEvaluation<>(
                PreparedMoveEvaluation.Status.EVALUATED,
                realized,
                InnerScore.fullyAssigned(SimpleScore.ZERO),
                7L));
    assertThat(fixture.evaluate(request)).isTrue();
    assertThat(fixture.evaluate(PreparedMoveFilters.defer(request, (director, move) -> false)))
        .isFalse();
    assertThat(
            fixture.evaluate(
                new Request(
                    new PreparedMoveEvaluation<>(
                        PreparedMoveEvaluation.Status.EMPTY, null, null, 2L))))
        .isFalse();
    assertThat(
            fixture.evaluate(
                new Request(
                    new PreparedMoveEvaluation<>(
                        PreparedMoveEvaluation.Status.CANCELLED, null, null, 3L))))
        .isFalse();
    AbstractSelectorBasedMove<Object> notDoable = mock(AbstractSelectorBasedMove.class);
    assertThat(fixture.evaluate(notDoable)).isFalse();
    assertThat(fixture.solver.getMoveEvaluationCount()).isEqualTo(1L);
    assertThat(fixture.solver.getMoveEvaluationCountPerType())
        .containsExactlyEntriesOf(java.util.Map.of("realized", 1L));
    assertThat(fixture.feedbackCounts).containsExactly(0L);
    assertThat(fixture.foragerCounts).containsExactly(1L);
    verify(fixture.director, never()).executeTemporaryMove(any(), anyBoolean());
  }

  @Test
  void pendingMovesRetainTheirDirectSingleCount() {
    var fixture = new Fixture();
    var pending = new Candidate("pending");
    fixture.solver.setPendingMove(pending);
    fixture.decider.decideNextStep(fixture.step);
    assertThat(fixture.solver.getMoveEvaluationCount()).isEqualTo(1L);
    assertThat(fixture.step.getStep()).isSameAs(pending);
    assertThat(fixture.step.getSelectedMoveCount()).isEqualTo(1L);
    assertThat(fixture.step.getAcceptedMoveCount()).isEqualTo(1L);
    verify(fixture.forager, never()).addMove(any());
  }

  private static final class Fixture {
    private final SolverScope<Object> solver = new SolverScope<>();
    private final InnerScoreDirector<Object, SimpleScore> director = mock(InnerScoreDirector.class);
    private final LocalSearchForager<Object> forager = mock(LocalSearchForager.class);
    private final LocalSearchStepScope<Object> step =
        new LocalSearchStepScope<>(new LocalSearchPhaseScope<>(solver, 0));
    private final List<Long> feedbackCounts = new ArrayList<>();
    private final List<Long> foragerCounts = new ArrayList<>();
    private final LocalSearchDecider<Object> decider;

    private Fixture() {
      solver.setScoreDirector(director);
      solver.setSolverMetricSet(EnumSet.of(SolverMetric.MOVE_COUNT_PER_TYPE));
      when(director.executeTemporaryMove(any(), anyBoolean()))
          .thenAnswer(
              invocation ->
                  InnerScore.fullyAssigned(
                      ((Candidate) invocation.getArgument(0)).name().equals("invalid")
                          ? new SimpleScore(-1, 0)
                          : SimpleScore.ZERO));
      org.mockito.Mockito.doAnswer(
              invocation -> {
                foragerCounts.add(solver.getMoveEvaluationCount());
                return null;
              })
          .when(forager)
          .addMove(any());
      var acceptor =
          new AbstractAcceptor<Object>() {
            @Override
            protected boolean isStructurallyValidSolutionAccepted(
                LocalSearchMoveScope<Object> scope) {
              return !scope.getMove().describe().equals("rejected");
            }

            @Override
            public void moveEvaluated(LocalSearchMoveScope<Object> scope) {
              feedbackCounts.add(solver.getMoveEvaluationCount());
            }
          };
      decider =
          new LocalSearchDecider<>(
              "",
              PhaseTermination.bridge(new BasicPlumbingTermination<>(false)),
              mock(MoveSelectorBasedMoveRepository.class),
              acceptor,
              forager);
    }

    private boolean evaluate(Move<Object> move) {
      return decider.doMove(new LocalSearchMoveScope<>(step, 0, move));
    }
  }

  private record Candidate(String name) implements Move<Object> {
    @Override
    public void execute(MutableSolutionView<Object> solutionView) {
      throw new UnsupportedOperationException("The fixture supplies scores directly.");
    }

    @Override
    public String describe() {
      return name;
    }
  }

  private record Request(PreparedMoveEvaluation<Object, SimpleScore> result)
      implements PreparableMove<Object> {
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <Score_ extends Score<Score_>> PreparedMoveEvaluation<Object, Score_> prepare(
        InnerScoreDirector<Object, Score_> director,
        Runnable checkTermination,
        boolean assertFromScratch,
        BiConsumer<SolutionView<Object>, Move<Object>> finalStateConsumer) {
      return (PreparedMoveEvaluation) result;
    }

    @Override
    public void execute(MutableSolutionView<Object> solutionView) {
      throw new UnsupportedOperationException("Prepare this request before execution.");
    }
  }
}
