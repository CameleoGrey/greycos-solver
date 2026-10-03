package greycos.solver.core.impl.localsearch.decider.acceptor.greatdeluge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.stream.Stream;

import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.HardMediumSoftScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleBigDecimalScore;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.localsearch.decider.acceptor.AbstractAcceptorTest;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class GreatDelugeAcceptorTest extends AbstractAcceptorTest {

  static Stream<Score<?>> ordinaryIntegralScores() {
    return Stream.of(
        SimpleScore.of(-8),
        HardSoftScore.of(0, -8),
        HardMediumSoftScore.of(0, 0, -8),
        BendableScore.of(new long[] {0}, new long[] {-8}));
  }

  @ParameterizedTest
  @MethodSource("ordinaryIntegralScores")
  <Score_ extends Score<Score_>> void cachedIntegralBoundsPreserveFixedAndRatioDecisions(
      Score_ initial) {
    var increment = initial.divide(-8.0);
    var raised = initial.add(increment);
    var fixed = new GreatDelugeAcceptor<>();
    fixed.setInitialWaterLevel(initial);
    fixed.setWaterLevelIncrementScore(increment);
    var fixedStep = start(fixed, initial.zero());
    fixed.stepEnded(fixedStep);
    assertAccepted(fixed, fixedStep, initial, false);
    assertAccepted(fixed, fixedStep, raised, true);

    var ratio = new GreatDelugeAcceptor<>();
    ratio.setInitialWaterLevel(initial);
    ratio.setWaterLevelIncrementRatio(0.1);
    var ratioStep = start(ratio, initial.zero());
    ratio.stepEnded(ratioStep);
    assertAccepted(ratio, ratioStep, initial, true);
    ratio.stepEnded(ratioStep);
    assertAccepted(ratio, ratioStep, initial, false);
    assertAccepted(ratio, ratioStep, raised, true);
  }

  @Test
  void integralFixedBoundMayExceedLongRange() {
    var acceptor = new GreatDelugeAcceptor<>();
    acceptor.setWaterLevelIncrementScore(SimpleScore.ONE);
    var step = start(acceptor, SimpleScore.of(Long.MAX_VALUE));
    acceptor.stepEnded(step);
    assertAccepted(acceptor, step, SimpleScore.of(Long.MAX_VALUE), false);
    assertAccepted(acceptor, step, SimpleScore.ZERO, false);
    acceptor.stepEnded(step);
    assertAccepted(acceptor, step, SimpleScore.of(Long.MAX_VALUE), false);
    acceptor.phaseEnded(step.getPhaseScope());
    var restarted = start(acceptor, SimpleScore.ZERO);
    assertAccepted(acceptor, restarted, SimpleScore.ZERO, true);
  }

  @Test
  void integralLowerLevelCanCrossItsMinimum() {
    var acceptor = new GreatDelugeAcceptor<>();
    acceptor.setInitialWaterLevel(HardSoftScore.of(0, Long.MIN_VALUE));
    acceptor.setWaterLevelIncrementScore(HardSoftScore.of(1, -1));
    var step = start(acceptor, HardSoftScore.of(2, 0));
    acceptor.stepEnded(step);
    assertAccepted(acceptor, step, HardSoftScore.of(1, Long.MIN_VALUE), true);
    assertAccepted(acceptor, step, HardSoftScore.of(0, Long.MAX_VALUE), false);
  }

  @Test
  void integralRatioHandlesMinimumAndOverflowingIntermediate() {
    var acceptor = new GreatDelugeAcceptor<>();
    acceptor.setInitialWaterLevel(SimpleScore.of(Long.MIN_VALUE));
    acceptor.setWaterLevelIncrementRatio(0.5);
    var step = start(acceptor, SimpleScore.of(Long.MAX_VALUE));
    acceptor.stepEnded(step);
    assertAccepted(acceptor, step, SimpleScore.of(-4_611_686_018_427_387_904L), true);
    assertAccepted(acceptor, step, SimpleScore.of(-4_611_686_018_427_387_905L), false);

    acceptor.phaseEnded(step.getPhaseScope());
    acceptor.setInitialWaterLevel(SimpleScore.of(-Long.MAX_VALUE));
    acceptor.setWaterLevelIncrementRatio(1.5);
    step = start(acceptor, SimpleScore.of(Long.MAX_VALUE));
    acceptor.stepEnded(step);
    assertAccepted(acceptor, step, SimpleScore.of(4_611_686_018_427_387_903L), true);
    assertAccepted(acceptor, step, SimpleScore.of(4_611_686_018_427_387_902L), false);
    acceptor.stepEnded(step);
    assertAccepted(acceptor, step, SimpleScore.of(Long.MAX_VALUE), false);
  }

  @Test
  void ratioKeepsOrdinaryIntegralAndDecimalRounding() {
    var integral = new GreatDelugeAcceptor<>();
    integral.setInitialWaterLevel(SimpleScore.of(-Long.MAX_VALUE));
    integral.setWaterLevelIncrementRatio(0.5);
    var integralStep = start(integral, SimpleScore.of(Long.MAX_VALUE));
    integral.stepEnded(integralStep);
    // The established in-range double product rounds MAX * .5 to 2^62.
    assertAccepted(integral, integralStep, SimpleScore.of(-4_611_686_018_427_387_903L), true);
    assertAccepted(integral, integralStep, SimpleScore.of(-4_611_686_018_427_387_904L), false);

    var decimal = new GreatDelugeAcceptor<>();
    decimal.setInitialWaterLevel(SimpleBigDecimalScore.of(new BigDecimal("-8.00")));
    decimal.setWaterLevelIncrementRatio(0.0001);
    var decimalStep = start(decimal, SimpleBigDecimalScore.ZERO);
    decimal.stepEnded(decimalStep);
    assertAccepted(decimal, decimalStep, SimpleBigDecimalScore.of(new BigDecimal("-8.00")), true);
    assertAccepted(decimal, decimalStep, SimpleBigDecimalScore.of(new BigDecimal("-8.001")), false);
  }

  @Test
  void accumulatedRatioMayExceedDoubleRange() {
    var integral = new GreatDelugeAcceptor<>();
    integral.setWaterLevelIncrementRatio(Double.MAX_VALUE);
    var step = start(integral, SimpleScore.of(-1));
    integral.stepEnded(step);
    integral.stepEnded(step);
    // The last score prevents aspiration from bypassing the bound.
    step.getPhaseScope()
        .getLastCompletedStepScope()
        .setInitializedScore(SimpleScore.of(Long.MAX_VALUE));
    assertAccepted(integral, step, SimpleScore.of(Long.MAX_VALUE), false);

    var decimal = new GreatDelugeAcceptor<>();
    decimal.setInitialWaterLevel(SimpleBigDecimalScore.of(new BigDecimal("-1.00")));
    decimal.setWaterLevelIncrementRatio(Double.MAX_VALUE);
    var decimalStep = start(decimal, SimpleBigDecimalScore.of(new BigDecimal("1E400")));
    decimal.stepEnded(decimalStep);
    decimal.stepEnded(decimalStep);
    assertAccepted(decimal, decimalStep, SimpleBigDecimalScore.of(new BigDecimal("1E308")), false);
    assertAccepted(decimal, decimalStep, SimpleBigDecimalScore.of(new BigDecimal("4E308")), true);
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
  void invalidRatioIsRejected(double ratio) {
    var acceptor = new GreatDelugeAcceptor<>();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> acceptor.setWaterLevelIncrementRatio(ratio));
  }

  @Test
  void invalidOrMissingIncrementIsRejected() {
    var acceptor = new GreatDelugeAcceptor<>();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> acceptor.setWaterLevelIncrementScore(SimpleScore.ZERO));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> acceptor.setWaterLevelIncrementScore(SimpleScore.MINUS_ONE));
    assertThatIllegalArgumentException().isThrownBy(() -> acceptor.phaseStarted(null));
    acceptor.setWaterLevelIncrementScore(SimpleScore.ONE);
    acceptor.setWaterLevelIncrementRatio(0.1);
    assertThatIllegalArgumentException().isThrownBy(() -> acceptor.phaseStarted(null));
  }

  private static <Score_ extends Score<Score_>> LocalSearchStepScope<Object> start(
      GreatDelugeAcceptor<Object> acceptor, Score_ bestScore) {
    var solver = new SolverScope<>();
    solver.setInitializedBestScore(bestScore);
    var phase = new LocalSearchPhaseScope<>(solver, 0);
    var last = new LocalSearchStepScope<>(phase, -1);
    last.setInitializedScore(bestScore);
    phase.setLastCompletedStepScope(last);
    acceptor.phaseStarted(phase);
    return new LocalSearchStepScope<>(phase);
  }

  private static <Score_ extends Score<Score_>> void assertAccepted(
      GreatDelugeAcceptor<Object> acceptor,
      LocalSearchStepScope<Object> step,
      Score_ score,
      boolean expected) {
    var move = new LocalSearchMoveScope<>(step, 0, mock(Move.class));
    move.setInitializedScore(score);
    assertThat(acceptor.isAccepted(move)).isEqualTo(expected);
  }

  static Stream<Score<?>> floatingMaximums() {
    return Stream.of(SimpleDoubleScore.of(Double.MAX_VALUE), SimpleFloatScore.of(Float.MAX_VALUE));
  }

  @ParameterizedTest
  @MethodSource("floatingMaximums")
  <Score_ extends Score<Score_>> void floatingRatioHasNoIntermediateOverflow(Score_ maximum) {
    var acceptor = new GreatDelugeAcceptor<>();
    acceptor.setInitialWaterLevel(maximum.negate());
    acceptor.setWaterLevelIncrementRatio(1.5);
    var solverScope = new SolverScope<>();
    solverScope.setInitializedBestScore(maximum);
    var phase = new LocalSearchPhaseScope<>(solverScope, 0);
    var lastStep = new LocalSearchStepScope<>(phase, -1);
    lastStep.setInitializedScore(maximum);
    phase.setLastCompletedStepScope(lastStep);
    acceptor.phaseStarted(phase);
    var step = new LocalSearchStepScope<>(phase);
    acceptor.stepEnded(step);
    // Internal water = -MAX + MAX * 1.5 = MAX / 2; last score MAX prevents aspiration.
    var move = new LocalSearchMoveScope<>(step, 0, mock(Move.class));
    move.setInitializedScore(maximum.divide(2));
    assertThat(acceptor.isAccepted(move)).isTrue();
    move.setInitializedScore(maximum.zero());
    assertThat(acceptor.isAccepted(move)).isFalse();
    acceptor.stepEnded(step);
    // Water is now 2*MAX and even the greatest finite move cannot clear it.
    move.setInitializedScore(maximum);
    assertThat(acceptor.isAccepted(move)).isFalse();
    acceptor.phaseEnded(phase);
  }

  @ParameterizedTest
  @MethodSource("floatingMaximums")
  <Score_ extends Score<Score_>> void floatingIncrementBoundCanExceedPublicScoreRange(
      Score_ maximum) {
    var acceptor = new GreatDelugeAcceptor<>();
    acceptor.setWaterLevelIncrementScore(maximum);
    var solverScope = new SolverScope<>();
    solverScope.setInitializedBestScore(maximum);
    var phase = new LocalSearchPhaseScope<>(solverScope, 0);
    var lastStep = new LocalSearchStepScope<>(phase, -1);
    lastStep.setInitializedScore(maximum);
    phase.setLastCompletedStepScope(lastStep);
    acceptor.phaseStarted(phase);
    var step = new LocalSearchStepScope<>(phase);
    acceptor.stepEnded(step);
    var move = new LocalSearchMoveScope<>(step, 0, mock(Move.class));
    move.setInitializedScore(maximum);
    assertThat(acceptor.isAccepted(move)).isFalse();
    acceptor.phaseEnded(phase);
  }

  @Test
  void waterLevelIncrementScore_SimpleScore() {
    var acceptor = new GreatDelugeAcceptor<>();
    acceptor.setWaterLevelIncrementScore(SimpleScore.of(100));

    var solverScope = new SolverScope<>();
    solverScope.setInitializedBestScore(SimpleScore.of(-1000));
    var phaseScope = new LocalSearchPhaseScope<>(solverScope, 0);
    var lastCompletedStepScope = new LocalSearchStepScope<>(phaseScope, -1);
    lastCompletedStepScope.setInitializedScore(SimpleScore.of(-1000));
    phaseScope.setLastCompletedStepScope(lastCompletedStepScope);
    acceptor.phaseStarted(phaseScope);

    // lastCompletedStepScore = -1000
    // water level -1000
    var stepScope0 = new LocalSearchStepScope<>(phaseScope);
    acceptor.stepStarted(stepScope0);
    var moveScope0 = buildMoveScope(stepScope0, -500);
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope0, -900))).isTrue();
    assertThat(acceptor.isAccepted(moveScope0)).isTrue();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope0, -800))).isTrue();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope0, -2000))).isFalse();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope0, -1000))).isTrue();
    // Repeated call
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope0, -900))).isTrue();

    stepScope0.setStep(moveScope0.getMove());
    stepScope0.setScore(moveScope0.getScore());
    solverScope.setBestScore((InnerScore) moveScope0.getScore());
    acceptor.stepEnded(stepScope0);
    phaseScope.setLastCompletedStepScope(stepScope0);

    // lastCompletedStepScore = -500
    // water level -900
    var stepScope1 = new LocalSearchStepScope<>(phaseScope);
    acceptor.stepStarted(stepScope1);
    var moveScope1 = buildMoveScope(stepScope1, -600);
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope1, -2000))).isFalse();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope1, -700))).isTrue();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope1, -1000))).isFalse();
    assertThat(acceptor.isAccepted(moveScope1)).isTrue();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope1, -500))).isTrue();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope1, -901))).isFalse();

    stepScope1.setStep(moveScope1.getMove());
    stepScope1.setScore(moveScope1.getScore());
    solverScope.setBestScore((InnerScore) moveScope1.getScore());
    acceptor.stepEnded(stepScope1);
    phaseScope.setLastCompletedStepScope(stepScope1);

    // lastCompletedStepScore = -600
    // water level -800
    var stepScope2 = new LocalSearchStepScope<>(phaseScope);
    acceptor.stepStarted(stepScope2);
    var moveScope2 = buildMoveScope(stepScope1, -350);
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope2, -900))).isFalse();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope2, -2000))).isFalse();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope2, -700))).isTrue();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope2, -801))).isFalse();
    assertThat(acceptor.isAccepted(moveScope2)).isTrue();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope2, -500))).isTrue();

    stepScope1.setStep(moveScope2.getMove());
    stepScope1.setScore(moveScope2.getScore());
    acceptor.stepEnded(stepScope2);
    phaseScope.setLastCompletedStepScope(stepScope2);

    acceptor.phaseEnded(phaseScope);
  }

  @Test
  void waterLevelIncrementScore_HardMediumSoftScore() {
    var acceptor = new GreatDelugeAcceptor<>();
    acceptor.setInitialWaterLevel(HardMediumSoftScore.of(0, -100, -400));
    acceptor.setWaterLevelIncrementScore(HardMediumSoftScore.of(0, 100, 100));

    var solverScope = new SolverScope<>();
    solverScope.setInitializedBestScore(HardMediumSoftScore.of(0, -200, -1000));
    var phaseScope = new LocalSearchPhaseScope<>(solverScope, 0);
    var lastCompletedStepScope = new LocalSearchStepScope<>(phaseScope, -1);
    lastCompletedStepScope.setInitializedScore(HardMediumSoftScore.of(0, -200, -1000));
    phaseScope.setLastCompletedStepScope(lastCompletedStepScope);
    acceptor.phaseStarted(phaseScope);

    // lastCompletedStepScore = 0/-200/-1000
    // water level 0/-100/-400
    var stepScope0 = new LocalSearchStepScope<>(phaseScope);
    acceptor.stepStarted(stepScope0);
    var moveScope0 = new LocalSearchMoveScope<>(stepScope0, 0, mock(Move.class));
    moveScope0.setInitializedScore(HardMediumSoftScore.of(0, -100, -300));
    assertThat(acceptor.isAccepted(moveScope0)).isTrue();
    var moveScope1 = new LocalSearchMoveScope<>(stepScope0, 0, mock(Move.class));
    moveScope1.setInitializedScore(HardMediumSoftScore.of(0, -100, -500));
    // Aspiration
    assertThat(acceptor.isAccepted(moveScope1)).isTrue();
    var moveScope2 = new LocalSearchMoveScope<>(stepScope0, 0, mock(Move.class));
    moveScope2.setInitializedScore(HardMediumSoftScore.of(0, -50, -800));
    assertThat(acceptor.isAccepted(moveScope2)).isTrue();
    var moveScope3 = new LocalSearchMoveScope<>(stepScope0, 0, mock(Move.class));
    moveScope3.setInitializedScore(HardMediumSoftScore.of(-5, -50, -100));
    assertThat(acceptor.isAccepted(moveScope3)).isFalse();
    var moveScope4 = new LocalSearchMoveScope<>(stepScope0, 0, mock(Move.class));
    moveScope4.setInitializedScore(HardMediumSoftScore.of(0, -22, -200));
    assertThat(acceptor.isAccepted(moveScope4)).isTrue();

    stepScope0.setStep(moveScope4.getMove());
    stepScope0.setScore(moveScope4.getScore());
    solverScope.setBestScore(moveScope4.getScore());
    acceptor.stepEnded(stepScope0);
    phaseScope.setLastCompletedStepScope(stepScope0);

    acceptor.phaseEnded(phaseScope);
  }

  @Test
  void waterLevelIncrementRatio() {
    var acceptor = new GreatDelugeAcceptor<>();
    acceptor.setWaterLevelIncrementRatio(0.1);

    var solverScope = new SolverScope<>();
    solverScope.setInitializedBestScore(SimpleScore.of(-8));
    var phaseScope = new LocalSearchPhaseScope<>(solverScope, 0);
    var lastCompletedStepScope = new LocalSearchStepScope<>(phaseScope, -1);
    lastCompletedStepScope.setInitializedScore(SimpleScore.of(-8));
    phaseScope.setLastCompletedStepScope(lastCompletedStepScope);
    acceptor.phaseStarted(phaseScope);

    // lastCompletedStepScore = -8
    // water level -8
    var stepScope0 = new LocalSearchStepScope<>(phaseScope);
    acceptor.stepStarted(stepScope0);
    var moveScope0 = buildMoveScope(stepScope0, -5);
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope0, -8))).isTrue();
    assertThat(acceptor.isAccepted(moveScope0)).isTrue();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope0, -7))).isTrue();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope0, -9))).isFalse();

    stepScope0.setStep(moveScope0.getMove());
    stepScope0.setScore(moveScope0.getScore());
    solverScope.setBestScore((InnerScore) moveScope0.getScore());
    acceptor.stepEnded(stepScope0);
    phaseScope.setLastCompletedStepScope(stepScope0);

    // lastCompletedStepScore = -5
    // water level -8 (rounded down from -7.2)
    var stepScope1 = new LocalSearchStepScope<>(phaseScope);
    acceptor.stepStarted(stepScope1);
    var moveScope1 = buildMoveScope(stepScope1, -6);
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope1, -10))).isFalse();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope1, -7))).isTrue();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope1, -9))).isFalse();
    assertThat(acceptor.isAccepted(moveScope1)).isTrue();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope1, -8))).isTrue();

    stepScope1.setStep(moveScope1.getMove());
    stepScope1.setScore(moveScope1.getScore());
    solverScope.setBestScore((InnerScore) moveScope1.getScore());
    acceptor.stepEnded(stepScope1);
    phaseScope.setLastCompletedStepScope(stepScope1);

    // lastCompletedStepScore = -6
    // water level -7 (rounded down from -6.4)
    var stepScope2 = new LocalSearchStepScope<>(phaseScope);
    acceptor.stepStarted(stepScope2);
    var moveScope2 = buildMoveScope(stepScope1, -4);
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope2, -9))).isFalse();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope2, -8))).isFalse();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope2, -7))).isTrue();
    assertThat(acceptor.isAccepted(moveScope2)).isTrue();

    stepScope1.setStep(moveScope2.getMove());
    stepScope1.setScore(moveScope2.getScore());
    acceptor.stepEnded(stepScope2);
    phaseScope.setLastCompletedStepScope(stepScope2);

    acceptor.phaseEnded(phaseScope);
  }
}
