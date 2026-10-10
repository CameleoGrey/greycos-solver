package greycos.solver.core.impl.localsearch.decider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.heuristic.selector.common.KnownExhaustionIterator;
import greycos.solver.core.impl.heuristic.selector.common.SelectionAttemptLedger;
import greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.neighborhood.MoveSelectorBasedMoveRepository;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.LocalSearchEpisodeTermination;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.api.Test;

class LocalSearchEpisodeDecisionTest {

  @Test
  void finalAttemptFinishesFeedbackButDoesNotPickAnUnfinishedDecision() {
    var fixture = new Fixture(false);
    fixture.decide();
    assertThat(fixture.ledger.getConsumedCount()).isEqualTo(1L);
    assertThat(fixture.step.getStep()).isNull();
    assertThat(fixture.step.getNoStepReason())
        .isEqualTo(LocalSearchStepScope.NoStepReason.EPISODE_ATTEMPT_LIMIT);
    verify(fixture.forager).addMove(any());
    verify(fixture.forager, never()).pickMove(any());
    verify(fixture.director).executeTemporaryMove(any(), anyBoolean());
  }

  @Test
  void finalAttemptMayCompleteTheConfiguredForager() {
    var fixture = new Fixture(true);
    fixture.decide();
    assertThat(fixture.ledger.getConsumedCount()).isEqualTo(1L);
    assertThat(fixture.step.getStep()).isSameAs(fixture.move);
    assertThat(fixture.step.getScore()).isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(1)));
  }

  @Test
  void finalAttemptMayNaturallyExhaustAKnownFiniteRepository() {
    var fixture = new Fixture(false, true);
    fixture.decide();
    assertThat(fixture.step.getStep()).isSameAs(fixture.move);
    assertThat(fixture.ledger.getConsumedCount()).isEqualTo(1L);
  }

  @Test
  void enclosingStopStillWinsWhenTheLastAttemptCompletesTheForager() {
    var fixture = new Fixture(true);
    fixture.stopInFeedback = true;
    fixture.decide();
    assertThat(fixture.step.getStep()).isNull();
    verify(fixture.forager, never()).pickMove(any());
  }

  @Test
  void pendingMigrationIsLeftForTheOuterController() {
    var fixture = new Fixture(true);
    fixture.solver.setPendingMove(fixture.move);
    fixture.decide();
    assertThat(fixture.solver.hasPendingMove()).isTrue();
    assertThat(fixture.step.getStep()).isNull();
    assertThat(fixture.ledger.getConsumedCount()).isZero();
  }

  @SuppressWarnings("unchecked")
  private static final class Fixture {
    final SolverScope<Object> solver = new SolverScope<>();
    final InnerScoreDirector<Object, SimpleScore> director = mock(InnerScoreDirector.class);
    final Move<Object> move = mock(Move.class);
    final LocalSearchForager<Object> forager = mock(LocalSearchForager.class);
    final SelectionAttemptLedger ledger = new SelectionAttemptLedger(1);
    final LocalSearchStepScope<Object> step;
    final LocalSearchDecider<Object> decider;
    final AtomicBoolean stopped = new AtomicBoolean();
    boolean stopInFeedback;

    Fixture(boolean quitEarly) {
      this(quitEarly, false);
    }

    Fixture(boolean quitEarly, boolean exactlyOne) {
      solver.setScoreDirector(director);
      var phase =
          new LocalSearchPhaseScope<>(
              solver,
              0,
              InnerScore.fullyAssigned(SimpleScore.ZERO),
              DefaultRandomSource.seeded(1),
              ledger);
      step = new LocalSearchStepScope<>(phase);
      var termination = new LocalSearchEpisodeTermination<Object>(null);
      termination.setEnclosingTermination(stopped::get, solver::hasPendingMove);
      phase.setTermination(termination);
      MoveSelectorBasedMoveRepository<Object> repository =
          mock(MoveSelectorBasedMoveRepository.class);
      when(repository.iterator())
          .thenReturn(
              exactlyOne
                  ? KnownExhaustionIterator.ofList(java.util.List.of(move))
                  : Collections.nCopies(3, move).iterator());
      Acceptor<Object> acceptor = mock(Acceptor.class);
      when(acceptor.isAccepted(any())).thenReturn(true);
      when(director.executeTemporaryMove(any(), anyBoolean()))
          .thenReturn(InnerScore.fullyAssigned(SimpleScore.of(1)));
      var selected = new AtomicReference<LocalSearchMoveScope<Object>>();
      doAnswer(
              invocation -> {
                assertThat(ledger.getConsumedCount()).isZero();
                selected.set(invocation.getArgument(0));
                if (stopInFeedback) stopped.set(true);
                return null;
              })
          .when(forager)
          .addMove(any());
      when(forager.isQuitEarly()).thenReturn(quitEarly);
      when(forager.pickMove(any())).thenAnswer(ignored -> selected.get());
      decider = new LocalSearchDecider<>("", termination, repository, acceptor, forager);
    }

    void decide() {
      decider.stepStarted(step);
      decider.decideNextStep(step);
    }
  }
}
