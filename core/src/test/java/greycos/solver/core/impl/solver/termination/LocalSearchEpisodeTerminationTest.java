package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.heuristic.selector.common.SelectionAttemptLedger;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.junit.jupiter.api.Test;

class LocalSearchEpisodeTerminationTest {

  @Test
  void attemptGradientUsesConsumedWorkAndExcludesParentDeadline() {
    var ledger = new SelectionAttemptLedger(4);
    var phase = phase(ledger);
    var parentStopped = new AtomicBoolean();
    var termination = new LocalSearchEpisodeTermination<Object>(null);
    termination.setEnclosingTermination(parentStopped::get, () -> false);
    try (var cursor = ledger.openCursor(List.of("a", "b").iterator())) {
      var reserved = cursor.next();
      assertThat(termination.calculatePhaseTimeGradient(phase)).isZero();
      ledger.consume(reserved);
      assertThat(termination.calculatePhaseTimeGradient(phase)).isEqualTo(0.25);
      parentStopped.set(true);
      assertThat(termination.isPhaseTerminated(phase)).isTrue();
      assertThat(termination.calculatePhaseTimeGradient(phase)).isEqualTo(0.25);
    }
  }

  @Test
  void preservesAndOrTreeBeforeCombiningIndependentAttemptGradient() {
    var phase = phase(new SelectionAttemptLedger(4));
    var slow = configured(0.2);
    var fast = configured(0.8);
    var unavailable = configured(-1.0);
    var tree = UniversalTermination.<Object>and(slow, UniversalTermination.or(fast, unavailable));
    assertThat(new LocalSearchEpisodeTermination<>(tree).calculatePhaseTimeGradient(phase))
        .isEqualTo(0.2);
    assertThat(new LocalSearchEpisodeTermination<>(unavailable).calculatePhaseTimeGradient(phase))
        .isZero();
    when(slow.isPhaseTerminated(phase)).thenReturn(true);
    assertThat(new LocalSearchEpisodeTermination<>(tree).isPhaseTerminated(phase)).isFalse();
    when(fast.isPhaseTerminated(phase)).thenReturn(true);
    assertThat(new LocalSearchEpisodeTermination<>(tree).isPhaseTerminated(phase)).isTrue();
  }

  @Test
  void rejectsInvalidConfiguredGradient() {
    var phase = phase(new SelectionAttemptLedger(4));
    for (double gradient : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -0.5, 1.1}) {
      var termination = new LocalSearchEpisodeTermination<>(configured(gradient));
      assertThatThrownBy(() -> termination.calculatePhaseTimeGradient(phase))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("episode time gradient");
    }
  }

  @Test
  void episodeStartsFromWorkingScoreAndRetainsItsOwnBest() {
    var solver = new SolverScope<Object>();
    solver.setBestScore(InnerScore.fullyAssigned(SimpleScore.of(100)));
    var phase =
        new LocalSearchPhaseScope<>(
            solver,
            0,
            InnerScore.fullyAssigned(SimpleScore.of(-10)),
            DefaultRandomSource.seeded(3),
            new SelectionAttemptLedger(4));
    phase.reset();
    assertThat(phase.getStartingScore()).isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-10)));
    assertThat(phase.getLastCompletedStepScope().getScore()).isEqualTo(phase.getStartingScore());
    var step = new LocalSearchStepScope<>(phase, 0);
    step.setScore(InnerScore.fullyAssigned(SimpleScore.of(-5)));
    assertThat(phase.recordEpisodeBest(step)).isTrue();
    assertThat(phase.getBestScore()).isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-5)));
    assertThat(solver.getBestScore()).isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(100)));
    assertThat(phase.recordEpisodeBest(step)).isFalse();
  }

  private static LocalSearchPhaseScope<Object> phase(SelectionAttemptLedger ledger) {
    return new LocalSearchPhaseScope<>(
        new SolverScope<>(),
        0,
        InnerScore.fullyAssigned(SimpleScore.ZERO),
        DefaultRandomSource.seeded(3),
        ledger);
  }

  @SuppressWarnings("unchecked")
  private static MockablePhaseTermination<Object> configured(double gradient) {
    MockablePhaseTermination<Object> termination = mock(MockablePhaseTermination.class);
    when(termination.isApplicableTo(any())).thenReturn(true);
    when(termination.calculatePhaseTimeGradient(any())).thenReturn(gradient);
    return termination;
  }
}
