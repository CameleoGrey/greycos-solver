package greycos.solver.core.impl.localsearch.decider.acceptor.lateacceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Random;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchPickEarlyType;
import greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.CompositeAcceptor;
import greycos.solver.core.impl.localsearch.decider.forager.AcceptedLocalSearchForager;
import greycos.solver.core.impl.localsearch.decider.forager.finalist.HighestScoreFinalistPodium;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DiversifiedLateAcceptanceAcceptorTest {

  private static final Move<Object> TEST_MOVE = solutionView -> {};

  @Test
  void acceptanceQueryDoesNotChangeHistory() {
    var phase = phase(-100);
    var acceptor = acceptor(phase, 3);
    var step = new LocalSearchStepScope<>(phase);

    assertThat(acceptor.isAccepted(candidate(step, -100))).isTrue();
    assertThat(acceptor.isAccepted(candidate(step, -90))).isTrue();
    assertThat(acceptor.isAccepted(candidate(step, -90))).isTrue();
    assertThat(acceptor.isAccepted(candidate(step, -101))).isFalse();
    assertHistory(acceptor, 0, -100, -100, -100);

    // Matching the worst history score is not enough unless it also matches the incumbent.
    phase.getLastCompletedStepScope().setInitializedScore(SimpleScore.of(-90));
    assertThat(acceptor.isAccepted(candidate(step, -100))).isFalse();
    assertHistory(acceptor, 0, -100, -100, -100);
  }

  @ParameterizedTest
  @CsvSource({
    "-1998, -2000, -1999, -2000",
    "-2000, -2001, -1999, -2001",
    "-2001, -2001, -1999, -2001",
    "-1999, -1998, -2000, -1998",
    "-2000, -1998, -1999, -1998",
    "-2001, -2000, -1999, -2000",
    "-1999, -2000, -2001, -2001",
    "-2000, -2000, -2001, -2001"
  })
  void replacementCriterion(long previous, long proposed, long late, long replacement) {
    var phase = phase(previous);
    var acceptor = acceptor(phase, 3);
    acceptor.previousScores[0] = inner(late);
    acceptor.previousScores[1] = inner(-2005);
    acceptor.previousScores[2] = inner(-1990);
    acceptor.lateWorseScore = inner(-2005);
    acceptor.lateWorseOccurrences = 1;
    var step = new LocalSearchStepScope<>(phase);

    var move = evaluate(acceptor, step, proposed);
    assertThat(move.getAccepted()).isTrue();
    assertHistory(acceptor, 0, late, -2005, -1990);
    commit(acceptor, step, move);
    assertHistory(acceptor, 1, replacement, -2005, -1990);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 5, 17})
  void firstAcceptedStreamMatchesPaperReference(int size) {
    var phase = phase(-100);
    var acceptor = acceptor(phase, size);
    var reference = new Reference(size, -100);
    var random = new Random(749);
    var step = new LocalSearchStepScope<>(phase);
    acceptor.stepStarted(step);

    for (int i = 0; i < 2_000; i++) {
      long proposed = reference.current + random.nextInt(21) - 10;
      boolean expectedAccepted = reference.accepts(proposed);
      var move = evaluate(acceptor, step, proposed);
      assertThat(move.getAccepted()).isEqualTo(expectedAccepted);
      if (expectedAccepted) {
        commit(acceptor, step, move);
        step = new LocalSearchStepScope<>(phase);
        acceptor.stepStarted(step);
      }
      reference.iterate(proposed, expectedAccepted);
      assertHistory(acceptor, reference.index, reference.history);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void foragerRecordsOnlyItsWinner(boolean bestFirst) {
    var phase = phase(-100);
    var acceptor = acceptor(phase, 2);
    var forager = forager(2);
    forager.phaseStarted(phase);
    var step = new LocalSearchStepScope<>(phase);
    forager.stepStarted(step);
    long[] scores = bestFirst ? new long[] {-80, -90} : new long[] {-90, -80};
    for (long score : scores) {
      var move = evaluate(acceptor, step, score);
      assertThat(move.getAccepted()).isTrue();
      forager.addMove(move);
      assertHistory(acceptor, 0, -100, -100);
    }

    assertThat(forager.isQuitEarly()).isTrue();
    var picked = forager.pickMove(step);
    assertThat(picked.<SimpleScore>getScore()).isEqualTo(inner(-80));
    commit(acceptor, step, picked);
    assertHistory(acceptor, 1, -80, -100);
    assertThat(acceptor.isAccepted(candidate(new LocalSearchStepScope<>(phase), -95))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void finalCompositeRejectionRecordsIncumbent(boolean vetoFirst) {
    var phase = phase(-100);
    var acceptor = acceptor(phase, 2);
    Acceptor<Object> veto = mock(Acceptor.class);
    when(veto.isAccepted(any())).thenReturn(true);
    var composite =
        vetoFirst
            ? new CompositeAcceptor<>(veto, acceptor)
            : new CompositeAcceptor<>(acceptor, veto);
    commitScore(composite, phase, -90);
    commitScore(composite, phase, -95);
    assertHistory(acceptor, 0, -90, -100);

    when(veto.isAccepted(any())).thenReturn(false);
    var step = new LocalSearchStepScope<>(phase);
    var move = evaluate(composite, step, -80);
    assertThat(move.getAccepted()).isFalse();
    assertHistory(acceptor, 1, -95, -100);
    assertThat(phase.getLastCompletedStepScope().<SimpleScore>getScore()).isEqualTo(inner(-95));
  }

  @Test
  void rejectedFallbackRecordsTheExecutedTransition() {
    var phase = phase(-100);
    var acceptor = acceptor(phase, 3);
    var forager = forager(1);
    forager.phaseStarted(phase);
    var step = new LocalSearchStepScope<>(phase);
    forager.stepStarted(step);
    forager.addMove(evaluate(acceptor, step, -120));
    forager.addMove(evaluate(acceptor, step, -110));
    assertHistory(acceptor, 2, -100, -100, -100);

    var picked = forager.pickMove(step);
    assertThat(picked.getAccepted()).isFalse();
    assertThat(picked.<SimpleScore>getScore()).isEqualTo(inner(-110));
    // The rejected proposal and the subsequent forced execution are separate history events.
    commit(acceptor, step, picked);
    assertHistory(acceptor, 0, -100, -100, -110);
    assertThat(acceptor.isAccepted(candidate(new LocalSearchStepScope<>(phase), -105))).isTrue();
  }

  @Test
  void structurallyFlawedCandidateDoesNotAdvanceHistoryOrProduceWinner() {
    var phase = phase(-100);
    var acceptor = acceptor(phase, 3);
    var forager = forager(1);
    forager.phaseStarted(phase);
    var step = new LocalSearchStepScope<>(phase);
    forager.stepStarted(step);
    var move = candidate(step, -90);
    move.setInitializedScore(new SimpleScore(-1, -90));
    move.setAccepted(acceptor.isAccepted(move));
    acceptor.moveEvaluated(move);
    forager.addMove(move);

    assertThat(move.getAccepted()).isFalse();
    assertThat(forager.pickMove(step)).isNull();
    assertHistory(acceptor, 0, -100, -100, -100);
  }

  @Test
  void acceptedProposalWithoutExecutedStepDoesNotChangeHistory() {
    var phase = phase(-100);
    var acceptor = acceptor(phase, 3);
    var step = new LocalSearchStepScope<>(phase);
    assertThat(evaluate(acceptor, step, -90).getAccepted()).isTrue();
    assertHistory(acceptor, 0, -100, -100, -100);
    acceptor.phaseEnded(phase);
    assertThat(acceptor.previousScores).isNull();
    assertThat(acceptor.lateWorseScore).isNull();
    assertThat(acceptor.lateWorseOccurrences).isEqualTo(-1);
    assertThat(acceptor.lateScoreIndex).isEqualTo(-1);
  }

  @Test
  void migrationRestartAndRepeatedPhaseDiscardPreviousHistory() {
    var phase = phase(-100);
    var acceptor = acceptor(phase, 3);
    commitScore(acceptor, phase, -90);
    var migrationStep = new LocalSearchStepScope<>(phase);
    migrationStep.setInitializedScore(SimpleScore.of(-50));
    acceptor.stepEnded(migrationStep);
    phase.setLastCompletedStepScope(migrationStep);
    phase.getSolverScope().setInitializedBestScore(SimpleScore.of(-50));
    phase.setBestSolutionStepIndex(7);
    acceptor.phaseEnded(phase);
    acceptor.phaseStarted(phase);
    assertHistory(acceptor, 0, -50, -50, -50);
    assertThat(acceptor.isAccepted(candidate(new LocalSearchStepScope<>(phase), -51))).isFalse();

    acceptor.phaseEnded(phase);
    acceptor.phaseStarted(phase(-200));
    assertHistory(acceptor, 0, -200, -200, -200);
  }

  @Test
  void acceptanceUsesLexicographicScoreComparison() {
    var solver = new SolverScope<Object>();
    solver.setInitializedBestScore(HardSoftScore.of(-1, -10));
    var phase = new LocalSearchPhaseScope<>(solver, 0);
    phase.reset();
    var acceptor = acceptor(phase, 2);
    var step = new LocalSearchStepScope<>(phase);
    var move = new LocalSearchMoveScope<>(step, 0, TEST_MOVE);
    move.setInitializedScore(HardSoftScore.of(0, Long.MIN_VALUE));
    move.setAccepted(acceptor.isAccepted(move));
    acceptor.moveEvaluated(move);
    assertThat(move.getAccepted()).isTrue();
    commit(acceptor, step, move);
    assertThat(acceptor.previousScores[0]).isEqualTo(move.getScore());
    assertThat(acceptor.lateWorseScore)
        .isEqualTo(InnerScore.fullyAssigned(HardSoftScore.of(-1, -10)));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void invalidHistorySize(int size) {
    var acceptor = new DiversifiedLateAcceptanceAcceptor<>();
    acceptor.setLateAcceptanceSize(size);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> acceptor.phaseStarted(null))
        .withMessageContaining("lateAcceptanceSize (" + size + ")");
  }

  private static LocalSearchPhaseScope<Object> phase(long initialScore) {
    var solver = new SolverScope<Object>();
    solver.setInitializedBestScore(SimpleScore.of(initialScore));
    var phase = new LocalSearchPhaseScope<>(solver, 0);
    phase.reset();
    return phase;
  }

  private static DiversifiedLateAcceptanceAcceptor<Object> acceptor(
      LocalSearchPhaseScope<Object> phase, int size) {
    var acceptor = new DiversifiedLateAcceptanceAcceptor<Object>();
    acceptor.setLateAcceptanceSize(size);
    acceptor.phaseStarted(phase);
    return acceptor;
  }

  private static AcceptedLocalSearchForager<Object> forager(int acceptedCountLimit) {
    return new AcceptedLocalSearchForager<>(
        new HighestScoreFinalistPodium<>(),
        LocalSearchPickEarlyType.NEVER,
        acceptedCountLimit,
        false);
  }

  private static LocalSearchMoveScope<Object> candidate(
      LocalSearchStepScope<Object> step, long score) {
    var move = new LocalSearchMoveScope<>(step, 0, TEST_MOVE);
    move.setInitializedScore(SimpleScore.of(score));
    return move;
  }

  private static LocalSearchMoveScope<Object> evaluate(
      Acceptor<Object> acceptor, LocalSearchStepScope<Object> step, long score) {
    var move = candidate(step, score);
    move.setAccepted(acceptor.isAccepted(move));
    acceptor.moveEvaluated(move);
    return move;
  }

  private static void commitScore(
      Acceptor<Object> acceptor, LocalSearchPhaseScope<Object> phase, long score) {
    var step = new LocalSearchStepScope<>(phase);
    var move = evaluate(acceptor, step, score);
    assertThat(move.getAccepted()).isTrue();
    commit(acceptor, step, move);
  }

  private static void commit(
      Acceptor<Object> acceptor,
      LocalSearchStepScope<Object> step,
      LocalSearchMoveScope<Object> move) {
    step.setStep(move.getMove());
    step.setScore(move.getScore());
    acceptor.stepEnded(step);
    step.getPhaseScope().setLastCompletedStepScope(step);
  }

  private static InnerScore<SimpleScore> inner(long score) {
    return InnerScore.fullyAssigned(SimpleScore.of(score));
  }

  private static void assertHistory(
      DiversifiedLateAcceptanceAcceptor<?> acceptor, int index, long... history) {
    assertThat(acceptor.previousScores)
        .containsExactly(
            Arrays.stream(history)
                .mapToObj(DiversifiedLateAcceptanceAcceptorTest::inner)
                .toArray(InnerScore[]::new));
    assertThat(acceptor.lateScoreIndex).isEqualTo(index);
    long worst = Arrays.stream(history).min().orElseThrow();
    assertThat(acceptor.lateWorseScore).isEqualTo(inner(worst));
    assertThat(acceptor.lateWorseOccurrences)
        .isEqualTo((int) Arrays.stream(history).filter(s -> s == worst).count());
  }

  /** Independent scalar implementation of the paper's one-candidate iteration. */
  private static final class Reference {

    private final long[] history;
    private int index;
    private long current;

    private Reference(int size, long initial) {
      history = new long[size];
      Arrays.fill(history, initial);
      current = initial;
    }

    private boolean accepts(long proposed) {
      return proposed == current || proposed > Arrays.stream(history).min().orElseThrow();
    }

    private void iterate(long proposed, boolean accepted) {
      long previous = current;
      if (accepted) {
        current = proposed;
      }
      if (current < history[index] || (current > history[index] && current > previous)) {
        history[index] = current;
      }
      index = (index + 1) % history.length;
    }
  }
}
