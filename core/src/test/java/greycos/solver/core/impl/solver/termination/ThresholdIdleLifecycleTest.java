package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.AbstractPhase;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.DummySimpleScoreEasyScoreCalculator;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.MockClock;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(15)
class ThresholdIdleLifecycleTest {

  @Test
  void firstImprovementUsesTheInitializedBestWithoutASyntheticBestEvent() {
    var fixture = new Fixture(1000L);
    fixture.start();
    fixture.clock.tick(Duration.ofMillis(500));
    fixture.improve(10);
    fixture.clock.tick(Duration.ofMillis(501));
    assertThat(fixture.termination.isSolverTerminated(fixture.solver)).isFalse();
    assertThat(fixture.termination.isPhaseTerminated(fixture.phase)).isFalse();
    assertThat(fixture.termination.calculateSolverTimeGradient(fixture.solver)).isEqualTo(0.501);
    fixture.clock.tick(Duration.ofMillis(499));
    assertThat(fixture.termination.isSolverTerminated(fixture.solver)).isFalse();
    assertThat(fixture.termination.isPhaseTerminated(fixture.phase)).isFalse();
    fixture.clock.tick(Duration.ofMillis(1));
    assertThat(fixture.termination.isSolverTerminated(fixture.solver)).isTrue();
    assertThat(fixture.termination.isPhaseTerminated(fixture.phase)).isTrue();
  }

  @Test
  void successivePhasesKeepIndependentThresholdHistories() {
    var fixture = new Fixture(1000L);
    fixture.start();
    fixture.clock.tick(Duration.ofMillis(400));
    fixture.improve(4);
    fixture.clock.tick(Duration.ofMillis(100));
    fixture.termination.phaseEnded(fixture.phase);
    var second = new LocalSearchPhaseScope<>(fixture.solver, 1);
    var secondStep = new LocalSearchStepScope<>(second);
    fixture.termination.phaseStarted(second);
    fixture.termination.stepStarted(secondStep);
    fixture.clock.tick(Duration.ofMillis(300));
    fixture.solver.setBestScore(InnerScore.fullyAssigned(SimpleScore.of(8)));
    fixture.solver.setBestSolutionTimeMillis(fixture.clock.millis());
    fixture.termination.bestScoreImproved(secondStep);
    fixture.clock.tick(Duration.ofMillis(701));
    assertThat(fixture.termination.isSolverTerminated(fixture.solver)).isFalse();
    assertThat(fixture.termination.calculateSolverTimeGradient(fixture.solver)).isEqualTo(0.701);
    assertThat(fixture.termination.isPhaseTerminated(second)).isTrue();
    assertThat(fixture.termination.calculatePhaseTimeGradient(second)).isEqualTo(1.0);
  }

  @Test
  void setupAndSolveReuseDoNotConsumePositiveIdleBudgets() {
    var fixture = new Fixture(1000L);
    for (int run = 0; run < 3; run++) {
      fixture.termination.solvingStarted(fixture.solver);
      fixture.clock.tick(Duration.ofSeconds(10));
      assertThat(fixture.termination.isSolverTerminated(fixture.solver)).isFalse();
      assertThat(fixture.termination.calculateSolverTimeGradient(fixture.solver)).isZero();
      fixture.termination.phaseStarted(fixture.phase);
      fixture.clock.tick(Duration.ofSeconds(10));
      assertThat(fixture.termination.isSolverTerminated(fixture.solver)).isFalse();
      assertThat(fixture.termination.isPhaseTerminated(fixture.phase)).isFalse();
      fixture.termination.stepStarted(fixture.step);
      assertThat(fixture.termination.calculatePhaseTimeGradient(fixture.phase)).isZero();
      fixture.clock.tick(Duration.ofMillis(1000));
      assertThat(fixture.termination.isSolverTerminated(fixture.solver)).isFalse();
      fixture.clock.tick(Duration.ofMillis(1));
      assertThat(fixture.termination.isSolverTerminated(fixture.solver)).isTrue();
      fixture.termination.phaseEnded(fixture.phase);
      fixture.termination.solvingEnded(fixture.solver);
      assertThat(fixture.termination.isSolverTerminated(fixture.solver)).isFalse();
      assertThat(fixture.termination.calculatePhaseTimeGradient(fixture.phase)).isZero();
    }
  }

  @Test
  void constructionResetsGlobalHistory() {
    var fixture = new Fixture(1000L);
    fixture.start();
    fixture.clock.tick(Duration.ofMillis(400));
    fixture.improve(4);
    fixture.termination.phaseEnded(fixture.phase);
    var construction = new ConstructionHeuristicPhaseScope<>(fixture.solver, 1);
    fixture.termination.phaseStarted(construction);
    fixture.clock.tick(Duration.ofSeconds(10));
    assertThat(fixture.termination.isSolverTerminated(fixture.solver)).isFalse();
    fixture.termination.phaseEnded(construction);
    fixture.termination.phaseStarted(fixture.phase);
    fixture.termination.stepStarted(fixture.step);
    fixture.clock.tick(Duration.ofMillis(500));
    fixture.improve(8);
    fixture.clock.tick(Duration.ofMillis(501));
    assertThat(fixture.termination.isSolverTerminated(fixture.solver)).isTrue();
    assertThat(fixture.termination.isPhaseTerminated(fixture.phase)).isTrue();
  }

  @Test
  void zeroBudgetTerminatesImmediatelyOnlyWhenAnImprovementPhaseIsApplicable() {
    var fixture = new Fixture(0L);
    fixture.termination.solvingStarted(fixture.solver);
    assertThat(fixture.termination.isSolverTerminated(fixture.solver)).isFalse();
    var construction = new ConstructionHeuristicPhaseScope<>(fixture.solver, 0);
    fixture.termination.phaseStarted(construction);
    assertThat(fixture.termination.isSolverTerminated(fixture.solver)).isFalse();
    assertThat(fixture.termination.isPhaseTerminated(construction)).isFalse();
    fixture.termination.phaseEnded(construction);
    fixture.termination.phaseStarted(fixture.phase);
    assertThat(fixture.termination.isSolverTerminated(fixture.solver)).isTrue();
    assertThat(fixture.termination.isPhaseTerminated(fixture.phase)).isTrue();
    assertThat(fixture.termination.calculateSolverTimeGradient(fixture.solver)).isEqualTo(1.0);
    assertThat(fixture.termination.calculatePhaseTimeGradient(fixture.phase)).isEqualTo(1.0);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void realSolverReuseAndProblemChangeRestartCompleteConstructionAfterLongSetup(boolean restart)
      throws ReflectiveOperationException {
    var clock = new MockClock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(DummySimpleScoreEasyScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withMoveThreadCount("NONE")
            .withTerminationConfig(
                new TerminationConfig()
                    .withUnimprovedSpentLimit(Duration.ofMillis(1000))
                    .withUnimprovedScoreDifferenceThreshold("7"))
            .withPhases(
                new ConstructionHeuristicPhaseConfig(),
                new LocalSearchPhaseConfig()
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));
    config.setClock(clock);
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var threshold =
        findThreshold(
            ((AbstractPhase<TestdataSolution>) solver.getPhaseList().getFirst())
                .getPhaseTermination());
    var clockField = threshold.getClass().getDeclaredField("clock");
    clockField.setAccessible(true);
    clockField.set(threshold, clock);
    var solvingStarts = new AtomicInteger();
    var queued = new AtomicBoolean();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingStarted(SolverScope<TestdataSolution> scope) {
            solvingStarts.incrementAndGet();
            clock.tick(Duration.ofMillis(1001));
          }

          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
            clock.tick(Duration.ofMillis(1001));
          }

          @Override
          public void solvingEnded(SolverScope<TestdataSolution> scope) {
            if (restart && !queued.getAndSet(true)) {
              solver.addProblemChange(
                  (solution, director) ->
                      director.addEntity(new TestdataEntity("new"), solution.getEntityList()::add));
            }
          }
        });
    for (int run = 0; run < (restart ? 1 : 2); run++) {
      var input = TestdataSolution.generateSolution(2, 2);
      input.getEntityList().forEach(entity -> entity.setValue(null));
      var result = solver.solve(input);
      assertThat(result.getEntityList())
          .hasSize(restart ? 3 : 2)
          .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
    }
    assertThat(solvingStarts.get()).isEqualTo(2);
  }

  private static UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<?> findThreshold(
      Termination<?> termination) {
    if (termination
        instanceof UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<?> threshold) {
      return threshold;
    }
    if (termination instanceof SolverBridgePhaseTermination<?> bridge) {
      return findThreshold(bridge.solverTermination);
    }
    if (termination instanceof AbstractCompositeTermination<?> composite) {
      for (var child : composite.terminationList) {
        var result = findThreshold(child);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  private static final class Fixture {
    private final MockClock clock = new MockClock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    private final SolverScope<TestdataSolution> solver = new SolverScope<>();
    private final LocalSearchPhaseScope<TestdataSolution> phase =
        new LocalSearchPhaseScope<>(solver, 0);
    private final LocalSearchStepScope<TestdataSolution> step = new LocalSearchStepScope<>(phase);
    private final UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<TestdataSolution>
        termination;

    private Fixture(long budget) {
      solver.setBestScore(InnerScore.fullyAssigned(SimpleScore.ZERO));
      solver.setBestSolutionTimeMillis(0L);
      termination =
          new UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<>(
              budget, SimpleScore.of(7), clock);
    }

    private void start() {
      termination.solvingStarted(solver);
      termination.phaseStarted(phase);
      termination.stepStarted(step);
    }

    private void improve(int score) {
      solver.setBestScore(InnerScore.fullyAssigned(SimpleScore.of(score)));
      solver.setBestSolutionTimeMillis(clock.millis());
      step.setBestScoreImproved(true);
      termination.stepEnded(step);
    }
  }
}
