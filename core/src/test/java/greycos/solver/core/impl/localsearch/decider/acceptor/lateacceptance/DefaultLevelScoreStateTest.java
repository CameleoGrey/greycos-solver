package greycos.solver.core.impl.localsearch.decider.acceptor.lateacceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.stream.Stream;

import greycos.solver.core.api.score.BendableBigDecimalScore;
import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.HardMediumSoftBigDecimalScore;
import greycos.solver.core.api.score.HardMediumSoftScore;
import greycos.solver.core.api.score.HardSoftBigDecimalScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.score.definition.BendableBigDecimalScoreDefinition;
import greycos.solver.core.impl.score.definition.BendableScoreDefinition;
import greycos.solver.core.impl.score.definition.HardMediumSoftBigDecimalScoreDefinition;
import greycos.solver.core.impl.score.definition.HardMediumSoftScoreDefinition;
import greycos.solver.core.impl.score.definition.HardSoftBigDecimalScoreDefinition;
import greycos.solver.core.impl.score.definition.HardSoftScoreDefinition;
import greycos.solver.core.impl.score.definition.ScoreDefinition;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultLevelScoreStateTest {

  @ParameterizedTest
  @MethodSource("decimalLevelChanges")
  <Score_ extends Score<Score_>> void comparesDecimalLevelsNumerically(
      ScoreDefinition<Score_> definition, Score_ initial, Score_ improved, boolean expectedReset) {
    var solver = new SolverScope<>();
    solver.setInitializedBestScore(initial);
    var phase = new LocalSearchPhaseScope<>(solver, 0);
    phase.reset();
    var step = new LocalSearchStepScope<>(phase);
    var state = new DefaultLevelScoreState<>(InnerScore.fullyAssigned(initial), definition);
    state.update(step);
    solver.setInitializedBestScore(improved);
    phase.setBestSolutionStepIndex(0);

    assertThat(state.isNonDominatedLevelChanged(step)).isEqualTo(expectedReset);
  }

  static Stream<Arguments> decimalLevelChanges() {
    return Stream.of(
        Arguments.of(
            new HardSoftBigDecimalScoreDefinition(),
            HardSoftBigDecimalScore.parseScore("0.0hard/-100soft"),
            HardSoftBigDecimalScore.parseScore("0.00hard/-50soft"),
            false),
        Arguments.of(
            new HardSoftBigDecimalScoreDefinition(),
            HardSoftBigDecimalScore.parseScore("-1.0hard/-100soft"),
            HardSoftBigDecimalScore.parseScore("-1.00hard/-50soft"),
            false),
        Arguments.of(
            new HardSoftBigDecimalScoreDefinition(),
            HardSoftBigDecimalScore.parseScore("-1.0000000000000000001hard/-100soft"),
            HardSoftBigDecimalScore.parseScore("-1.0hard/-100soft"),
            true),
        Arguments.of(
            new HardMediumSoftBigDecimalScoreDefinition(),
            HardMediumSoftBigDecimalScore.parseScore("0.0hard/-1.0medium/-100soft"),
            HardMediumSoftBigDecimalScore.parseScore("0.00hard/-1.00medium/-50soft"),
            false),
        Arguments.of(
            new HardMediumSoftBigDecimalScoreDefinition(),
            HardMediumSoftBigDecimalScore.parseScore(
                "0.0hard/-1.0000000000000000001medium/-100soft"),
            HardMediumSoftBigDecimalScore.parseScore("0.00hard/-1.0medium/-100soft"),
            true),
        Arguments.of(
            new BendableBigDecimalScoreDefinition(2, 1),
            BendableBigDecimalScore.of(
                new BigDecimal[] {new BigDecimal("0.0"), new BigDecimal("-1.0")},
                new BigDecimal[] {new BigDecimal("-100")}),
            BendableBigDecimalScore.of(
                new BigDecimal[] {new BigDecimal("0.00"), new BigDecimal("-1.00")},
                new BigDecimal[] {new BigDecimal("-50")}),
            false),
        Arguments.of(
            new BendableBigDecimalScoreDefinition(2, 1),
            BendableBigDecimalScore.of(
                new BigDecimal[] {BigDecimal.ZERO, new BigDecimal("-1.0000000000000000001")},
                new BigDecimal[] {new BigDecimal("-100")}),
            BendableBigDecimalScore.of(
                new BigDecimal[] {BigDecimal.ZERO, new BigDecimal("-1.0")},
                new BigDecimal[] {new BigDecimal("-100")}),
            true));
  }

  @SuppressWarnings("unchecked")
  private static <Solution_> LocalSearchStepScope<Solution_> buildStepScope(
      InnerScore<?> bestScoreAtEnd, int updateStepIndex, int checkStepIndex) {
    var phaseScope = mock(LocalSearchPhaseScope.class);
    var stepScope = mock(LocalSearchStepScope.class);
    when(stepScope.getPhaseScope()).thenReturn(phaseScope);
    when(phaseScope.getBestSolutionStepIndex()).thenReturn(updateStepIndex, checkStepIndex);
    when(phaseScope.getBestScore()).thenReturn(bestScoreAtEnd);
    return stepScope;
  }

  @Test
  void firstStepAlwaysChecksLevel() {
    var initialScore = InnerScore.fullyAssigned(HardSoftScore.of(-1, -1000));
    var state = new DefaultLevelScoreState<>(initialScore, new HardSoftScoreDefinition());
    var hardImprovedScore = InnerScore.fullyAssigned(HardSoftScore.of(0, -200));
    // Simulates the transition from the pre-step state (index -1) to the first real step (index 0).
    var firstStep = buildStepScope(initialScore, -1, -1);
    state.update(firstStep);
    var secondStep = buildStepScope(hardImprovedScore, 0, 0);
    assertThat(state.isNonDominatedLevelChanged(secondStep)).isTrue();
  }

  @Test
  void hardImprovementTriggersReset() {
    var initialScore = InnerScore.fullyAssigned(HardSoftScore.of(-1, -1000));
    var state = new DefaultLevelScoreState<>(initialScore, new HardSoftScoreDefinition());
    var hardImprovedScore = InnerScore.fullyAssigned(HardSoftScore.of(0, -200));
    var stepScope = buildStepScope(hardImprovedScore, 0, 1);
    state.update(stepScope);
    assertThat(state.isNonDominatedLevelChanged(stepScope)).isTrue();
  }

  @Test
  void softOnlyImprovementDoesNotTriggerReset() {
    var initialScore = InnerScore.fullyAssigned(HardSoftScore.of(-1, -1000));
    var state = new DefaultLevelScoreState<>(initialScore, new HardSoftScoreDefinition());
    var softImprovedScore = InnerScore.fullyAssigned(HardSoftScore.of(-1, -200));
    var stepScope = buildStepScope(softImprovedScore, 0, 1);
    state.update(stepScope);
    assertThat(state.isNonDominatedLevelChanged(stepScope)).isFalse();
  }

  @Test
  void skipsCheckWhenStepIndexUnchanged() {
    var initialScore = InnerScore.fullyAssigned(HardSoftScore.of(-1, -1000));
    var state = new DefaultLevelScoreState<>(initialScore, new HardSoftScoreDefinition());
    var hardImprovedScore = InnerScore.fullyAssigned(HardSoftScore.of(0, -200));
    var stepScope0 = buildStepScope(hardImprovedScore, 1, 1);
    state.update(stepScope0);
    // Step index stays 1 on the next step — check must be skipped
    var softImprovedScore = InnerScore.fullyAssigned(HardSoftScore.of(0, -100));
    var stepScope1 = buildStepScope(softImprovedScore, 1, 1);
    state.update(stepScope1);
    assertThat(state.isNonDominatedLevelChanged(stepScope1)).isFalse();
  }

  @Test
  void updateRefreshesBaselineForNextStep() {
    // Step 0: hard improves. Step 1: only soft improves from the new hard baseline.
    // update() for step 1 must refresh the cache so the soft-only change does not trigger a reset.
    var initialScore = InnerScore.fullyAssigned(HardSoftScore.of(-1, -1000));
    var state = new DefaultLevelScoreState<>(initialScore, new HardSoftScoreDefinition());

    var hardImprovedScore = InnerScore.fullyAssigned(HardSoftScore.of(0, -200));
    var stepScope0 = buildStepScope(hardImprovedScore, 1, 1);
    state.update(stepScope0); // previousBestScoreIndex changes to 1 and levels move to [0, 200]
    assertThat(state.isNonDominatedLevelChanged(stepScope0)).isFalse();

    // Step 1 scope: start index 1 (best from step 0), end index 2 (new best after step 1)
    var softImprovedScore = InnerScore.fullyAssigned(HardSoftScore.of(0, -100));
    var phaseScope1 = mock(LocalSearchPhaseScope.class);
    var stepScope1 = mock(LocalSearchStepScope.class);
    when(stepScope1.getPhaseScope()).thenReturn(phaseScope1);
    when(phaseScope1.getBestSolutionStepIndex()).thenReturn(1, 2);
    when(phaseScope1.getBestScore()).thenReturn(hardImprovedScore, softImprovedScore);
    state.update(stepScope1);
    assertThat(state.isNonDominatedLevelChanged(stepScope1))
        .isFalse(); // [0,-100] vs [0,-200]: hard unchanged
  }

  @Test
  void mediumImprovementTriggersResetForHardMediumSoftScore() {
    var initialScore = InnerScore.fullyAssigned(HardMediumSoftScore.of(-1, -100, -1000));
    var state = new DefaultLevelScoreState<>(initialScore, new HardMediumSoftScoreDefinition());
    var mediumImprovedScore = InnerScore.fullyAssigned(HardMediumSoftScore.of(-1, 0, -1000));
    var stepScope = buildStepScope(mediumImprovedScore, 0, 1);
    state.update(stepScope);
    assertThat(state.isNonDominatedLevelChanged(stepScope)).isTrue();
  }

  @Test
  void softOnlyImprovementDoesNotTriggerResetForHardMediumSoftScore() {
    var initialScore = InnerScore.fullyAssigned(HardMediumSoftScore.of(-1, -100, -1000));
    var state = new DefaultLevelScoreState<>(initialScore, new HardMediumSoftScoreDefinition());
    var softImprovedScore = InnerScore.fullyAssigned(HardMediumSoftScore.of(-1, -100, -500));
    var stepScope = buildStepScope(softImprovedScore, 0, 1);
    state.update(stepScope);
    assertThat(state.isNonDominatedLevelChanged(stepScope)).isFalse();
  }

  @Test
  void hardImprovementTriggersResetForBendableScore() {
    var initialScore =
        InnerScore.fullyAssigned(BendableScore.of(new long[] {-1}, new long[] {0, 0}));
    var state = new DefaultLevelScoreState<>(initialScore, new BendableScoreDefinition(1, 2));
    var hardImprovedScore =
        InnerScore.fullyAssigned(BendableScore.of(new long[] {0}, new long[] {-10, -10}));
    var stepScope = buildStepScope(hardImprovedScore, 0, 1);
    state.update(stepScope);
    assertThat(state.isNonDominatedLevelChanged(stepScope)).isTrue();
  }

  @Test
  void softImprovementDoesNotTriggerResetForBendableScore() {
    var initialScore =
        InnerScore.fullyAssigned(BendableScore.of(new long[] {-1}, new long[] {0, 0}));
    var state = new DefaultLevelScoreState<>(initialScore, new BendableScoreDefinition(1, 2));
    var softImprovedScore =
        InnerScore.fullyAssigned(BendableScore.of(new long[] {-1}, new long[] {1, 1}));
    var stepScope = buildStepScope(softImprovedScore, 0, 1);
    state.update(stepScope);
    assertThat(state.isNonDominatedLevelChanged(stepScope)).isFalse();
  }
}
