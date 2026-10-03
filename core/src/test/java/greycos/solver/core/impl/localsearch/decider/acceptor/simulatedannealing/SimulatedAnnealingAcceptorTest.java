package greycos.solver.core.impl.localsearch.decider.acceptor.simulatedannealing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.stream.Stream;

import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.HardMediumSoftScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleBigDecimalScore;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.localsearch.decider.acceptor.AbstractAcceptorTest;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testutil.TestRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SimulatedAnnealingAcceptorTest extends AbstractAcceptorTest {

  @Test
  void smallFloatingTemperaturesKeepTheirScale() {
    assertAcceptance(
        SimpleDoubleScore.ZERO,
        SimpleDoubleScore.of(-1e-200),
        SimpleDoubleScore.of(1e-200),
        0.0,
        0.3,
        true);
    assertAcceptance(
        SimpleDoubleScore.ZERO,
        SimpleDoubleScore.of(-1e-200),
        SimpleDoubleScore.of(1e-200),
        0.0,
        0.4,
        false);
    assertAcceptance(
        SimpleDoubleScore.ZERO,
        SimpleDoubleScore.of(-Double.MIN_VALUE),
        SimpleDoubleScore.of(Double.MIN_VALUE),
        0.5,
        0.13,
        true);
    assertAcceptance(
        SimpleDoubleScore.ZERO,
        SimpleDoubleScore.of(-Double.MIN_VALUE),
        SimpleDoubleScore.of(Double.MIN_VALUE),
        0.5,
        0.14,
        false);
    assertAcceptance(
        SimpleDoubleScore.ZERO,
        SimpleDoubleScore.of(-Double.MIN_VALUE),
        SimpleDoubleScore.of(Double.MIN_VALUE),
        0.25,
        0.3,
        false);
  }

  @Test
  void zeroTemperatureRejectsLossAndStillConsumesRandom() {
    assertAcceptance(
        SimpleDoubleScore.ZERO,
        SimpleDoubleScore.of(-1e-110),
        SimpleDoubleScore.ZERO,
        0.0,
        0.0,
        false);
    assertAcceptance(
        SimpleDoubleScore.ZERO,
        SimpleDoubleScore.of(-1e-110),
        SimpleDoubleScore.ONE,
        1.0,
        0.0,
        false);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1E400", "1E-400", "1E-200"})
  void decimalTemperatureAndLossOutsideDoubleRange(String value) {
    var temperature = SimpleBigDecimalScore.of(new BigDecimal(value));
    assertAcceptance(SimpleBigDecimalScore.ZERO, temperature.negate(), temperature, 0.0, 0.3, true);
    assertAcceptance(
        SimpleBigDecimalScore.ZERO, temperature.negate(), temperature, 0.0, 0.4, false);
    assertAcceptance(
        SimpleBigDecimalScore.ZERO, temperature.negate(), temperature, 1.0, 0.0, false);
  }

  @Test
  void integralLossMayExceedLongRange() {
    assertAcceptance(
        SimpleScore.of(Long.MAX_VALUE),
        SimpleScore.of(Long.MIN_VALUE),
        SimpleScore.ONE,
        0.0,
        0.5,
        false);
    assertAcceptance(
        SimpleScore.ZERO,
        SimpleScore.of(Long.MIN_VALUE),
        SimpleScore.of(Long.MAX_VALUE),
        0.0,
        0.3,
        true);
    assertAcceptance(
        SimpleScore.ZERO,
        SimpleScore.of(Long.MIN_VALUE),
        SimpleScore.of(Long.MAX_VALUE),
        0.0,
        0.4,
        false);
  }

  static Stream<Score<?>> integralMaximums() {
    return Stream.of(
        SimpleScore.of(Long.MAX_VALUE),
        HardSoftScore.of(Long.MAX_VALUE, Long.MAX_VALUE),
        HardMediumSoftScore.of(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE),
        BendableScore.of(new long[] {Long.MAX_VALUE}, new long[] {Long.MAX_VALUE}));
  }

  @ParameterizedTest
  @MethodSource("integralMaximums")
  <Score_ extends Score<Score_>> void overflowingLossesPreserveLevelProbabilities(Score_ maximum) {
    // Each level loses twice its temperature, and positive losses multiply their probabilities.
    double probability = Math.exp(-2.0 * maximum.toLevelNumbers().length);
    assertAcceptance(maximum, maximum.negate(), maximum, 0.0, probability / 2.0, true);
    assertAcceptance(maximum, maximum.negate(), maximum, 0.0, probability * 2.0, false);
  }

  @Test
  void tinyNegativeDecimalTemperatureIsRejected() {
    var acceptor = new SimulatedAnnealingAcceptor<>();
    acceptor.setStartingTemperature(SimpleBigDecimalScore.of(new BigDecimal("-1E-400")));
    assertThatIllegalArgumentException().isThrownBy(() -> acceptor.phaseStarted(null));
  }

  @ParameterizedTest
  @ValueSource(doubles = {-1.0, -0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY})
  void unsupportedOrInvalidTimeGradientIsRejected(double gradient) {
    var acceptor = new SimulatedAnnealingAcceptor<>();
    acceptor.setStartingTemperature(SimpleScore.ONE);
    acceptor.phaseStarted(null);
    var step = new LocalSearchStepScope<>(new LocalSearchPhaseScope<>(new SolverScope<>(), 0));
    step.setTimeGradient(gradient);
    assertThatIllegalStateException()
        .isThrownBy(() -> acceptor.stepStarted(step))
        .withMessageContaining("timeGradient");
  }

  private static <Score_ extends Score<Score_>> void assertAcceptance(
      Score_ lastScore,
      Score_ candidate,
      Score_ temperature,
      double gradient,
      double randomValue,
      boolean expected) {
    var acceptor = new SimulatedAnnealingAcceptor<>();
    acceptor.setStartingTemperature(temperature);
    var solver = new SolverScope<>();
    solver.setInitializedBestScore(lastScore);
    var random = new TestRandom(randomValue, 0.987654321);
    solver.setWorkingRandom(random);
    var phase = new LocalSearchPhaseScope<>(solver, 0);
    var lastStep = new LocalSearchStepScope<>(phase, -1);
    lastStep.setInitializedScore(lastScore);
    phase.setLastCompletedStepScope(lastStep);
    acceptor.phaseStarted(phase);
    var step = new LocalSearchStepScope<>(phase);
    step.setTimeGradient(gradient);
    acceptor.stepStarted(step);
    var move = new LocalSearchMoveScope<>(step, 0, mock(Move.class));
    move.setInitializedScore(candidate);
    assertThat(acceptor.isAccepted(move)).isEqualTo(expected);
    assertThat(random.nextDouble()).isEqualTo(0.987654321);
    acceptor.phaseEnded(phase);
  }

  @Test
  void finiteEndpointsWithOverflowingDifferenceUseFiniteProbability() {
    var acceptor = new SimulatedAnnealingAcceptor<>();
    acceptor.setStartingTemperature(SimpleDoubleScore.of(Double.MAX_VALUE));
    var solverScope = new SolverScope<>();
    solverScope.setInitializedBestScore(SimpleDoubleScore.of(Double.MAX_VALUE));
    var phaseScope = new LocalSearchPhaseScope<>(solverScope, 0);
    var lastStep = new LocalSearchStepScope<>(phaseScope, -1);
    lastStep.setInitializedScore(SimpleDoubleScore.of(Double.MAX_VALUE));
    phaseScope.setLastCompletedStepScope(lastStep);
    acceptor.phaseStarted(phaseScope);
    var step = new LocalSearchStepScope<>(phaseScope);
    step.setTimeGradient(0.0);
    acceptor.stepStarted(step);
    var move = new LocalSearchMoveScope<>(step, 0, mock(Move.class));
    move.setInitializedScore(SimpleDoubleScore.of(-Double.MAX_VALUE));
    // The loss divided by temperature is 2, so acceptance probability is exp(-2).
    solverScope.setWorkingRandom(new TestRandom(0.13));
    assertThat(acceptor.isAccepted(move)).isTrue();
    solverScope.setWorkingRandom(new TestRandom(0.14));
    assertThat(acceptor.isAccepted(move)).isFalse();
  }

  @Test
  void lateAcceptanceSize() {
    var acceptor = new SimulatedAnnealingAcceptor<>();
    acceptor.setStartingTemperature(SimpleScore.of(200));

    var solverScope = new SolverScope<>();
    solverScope.setInitializedBestScore(SimpleScore.of(-1000));
    var phaseScope = new LocalSearchPhaseScope<>(solverScope, 0);
    var lastCompletedStepScope = new LocalSearchStepScope<>(phaseScope, -1);
    lastCompletedStepScope.setInitializedScore(SimpleScore.of(-1000));
    phaseScope.setLastCompletedStepScope(lastCompletedStepScope);
    acceptor.phaseStarted(phaseScope);

    var stepScope0 = new LocalSearchStepScope<>(phaseScope);
    stepScope0.setTimeGradient(0.0);
    acceptor.stepStarted(stepScope0);
    var moveScope0 = buildMoveScope(stepScope0, -500);
    solverScope.setWorkingRandom(new TestRandom(0.3));
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope0, -1300))).isFalse();
    solverScope.setWorkingRandom(new TestRandom(0.3));
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope0, -1200))).isTrue();
    solverScope.setWorkingRandom(new TestRandom(0.4));
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope0, -1200))).isFalse();
    assertThat(acceptor.isAccepted(moveScope0)).isTrue();
    stepScope0.setStep(moveScope0.getMove());
    stepScope0.setScore(moveScope0.getScore());
    solverScope.setBestScore((InnerScore) moveScope0.getScore());
    acceptor.stepEnded(stepScope0);
    phaseScope.setLastCompletedStepScope(stepScope0);

    var stepScope1 = new LocalSearchStepScope<>(phaseScope);
    stepScope1.setTimeGradient(0.5);
    acceptor.stepStarted(stepScope1);
    var moveScope1 = buildMoveScope(stepScope1, -800);
    solverScope.setWorkingRandom(new TestRandom(0.13));
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope1, -700))).isTrue();
    solverScope.setWorkingRandom(new TestRandom(0.14));
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope1, -700))).isFalse();
    solverScope.setWorkingRandom(new TestRandom(0.04));
    assertThat(acceptor.isAccepted(moveScope1)).isTrue();
    stepScope1.setStep(moveScope1.getMove());
    stepScope1.setScore(moveScope1.getScore());
    // bestScore unchanged
    acceptor.stepEnded(stepScope1);
    phaseScope.setLastCompletedStepScope(stepScope1);

    solverScope.setWorkingRandom(new TestRandom(0.01, 0.01));
    var stepScope2 = new LocalSearchStepScope<>(phaseScope);
    stepScope2.setTimeGradient(1.0);
    acceptor.stepStarted(stepScope2);
    var moveScope2 = buildMoveScope(stepScope1, -400);
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope2, -800))).isTrue();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope2, -801))).isFalse();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope2, -1200))).isFalse();
    assertThat(acceptor.isAccepted(buildMoveScope(stepScope2, -700))).isTrue();
    assertThat(acceptor.isAccepted(moveScope2)).isTrue();
    stepScope2.setStep(moveScope2.getMove());
    stepScope2.setScore(moveScope2.getScore());
    solverScope.setBestScore((InnerScore) moveScope2.getScore());
    acceptor.stepEnded(stepScope2);
    phaseScope.setLastCompletedStepScope(stepScope2);

    acceptor.phaseEnded(phaseScope);
  }

  @Test
  void negativeSimulatedAnnealingSize() {
    var acceptor = new SimulatedAnnealingAcceptor<>();
    acceptor.setStartingTemperature(HardMediumSoftScore.of(1, -1, 2));
    assertThatIllegalArgumentException().isThrownBy(() -> acceptor.phaseStarted(null));
  }
}
