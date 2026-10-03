package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.termination.TerminationCompositionStyle;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.MockClock;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(15)
class SerialTerminationIntegrationTest {

  @ParameterizedTest
  @ValueSource(
      strings = {"step", "move", "unimprovedStep", "calculation", "time", "unimprovedTime"})
  void zeroBudgetInAndDoesNotPoisonSimulatedAnnealing(String kind) {
    var termination =
        new TerminationConfig()
            .withSpentLimit(Duration.ofMillis(10))
            .withTerminationCompositionStyle(TerminationCompositionStyle.AND);
    switch (kind) {
      case "step" -> termination.withStepCountLimit(0);
      case "move" -> termination.withMoveCountLimit(0L);
      case "unimprovedStep" -> termination.withUnimprovedStepCountLimit(0);
      case "calculation" -> termination.withScoreCalculationCountLimit(0L);
      case "time" -> termination.withSpentLimit(Duration.ZERO).withStepCountLimit(10);
      case "unimprovedTime" -> termination.withUnimprovedSpentLimit(Duration.ZERO);
      default -> throw new IllegalArgumentException(kind);
    }
    var phase =
        new LocalSearchPhaseConfig()
            .withAcceptorConfig(
                new LocalSearchAcceptorConfig().withSimulatedAnnealingStartingTemperature("10"))
            .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1))
            .withTerminationConfig(termination);
    var clock = new MockClock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withMoveThreadCount("NONE")
            .withPhases(phase);
    config.setClock(clock);
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var steps = new AtomicInteger();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> scope) {
            clock.tick(Duration.ofMillis(1));
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            steps.incrementAndGet();
          }
        });
    var result = solver.solve(TestdataSolution.generateSolution(2, 2));
    assertThat(result.getScore()).isNotNull();
    assertThat(steps.get()).isEqualTo(10);
  }

  @Test
  void globalAndOfPhaseOnlyConditionsWaitsUntilBothAreReached() {
    var config =
        new TerminationConfig()
            .withStepCountLimit(3)
            .withUnimprovedStepCountLimit(2)
            .withTerminationCompositionStyle(TerminationCompositionStyle.AND);
    assertThat(solve(config, false)).isEqualTo(3);
    config.withTerminationCompositionStyle(TerminationCompositionStyle.OR);
    assertThat(solve(config, false)).isEqualTo(2);
  }

  @Test
  void globalReachedScoreDoesNotRemoveAnUnsatisfiedPhaseCondition() {
    var config =
        new TerminationConfig()
            .withBestScoreLimit("0")
            .withUnimprovedStepCountLimit(2)
            .withTerminationCompositionStyle(TerminationCompositionStyle.AND);
    assertThat(solve(config, false)).isEqualTo(2);
  }

  @Test
  void nestedGlobalPhaseOnlyConditionsRetainTheirBooleanMeaning() {
    var config =
        new TerminationConfig()
            .withTerminationCompositionStyle(TerminationCompositionStyle.OR)
            .withTerminationConfigList(
                List.of(
                    new TerminationConfig()
                        .withStepCountLimit(3)
                        .withUnimprovedStepCountLimit(2)
                        .withTerminationCompositionStyle(TerminationCompositionStyle.AND),
                    new TerminationConfig().withStepCountLimit(5)));
    assertThat(solve(config, false)).isEqualTo(3);
  }

  @Test
  void globalAndDoesNotTerminatePartialConstructionWhenAnotherConditionRequiresLocalSearch() {
    var config =
        new TerminationConfig()
            .withSpentLimit(Duration.ZERO)
            .withUnimprovedStepCountLimit(2)
            .withTerminationCompositionStyle(TerminationCompositionStyle.AND);
    assertThat(solve(config, true)).isEqualTo(2);
  }

  private static int solve(TerminationConfig termination, boolean construct) {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withMoveThreadCount("NONE")
            .withTerminationConfig(termination);
    config.withPhases(
        construct
            ? new greycos.solver.core.config.phase.PhaseConfig[] {
              new ConstructionHeuristicPhaseConfig(), new LocalSearchPhaseConfig()
            }
            : new greycos.solver.core.config.phase.PhaseConfig[] {new LocalSearchPhaseConfig()});
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var steps = new AtomicInteger();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            if (scope instanceof LocalSearchStepScope<?>) steps.incrementAndGet();
          }
        });
    var input =
        construct
            ? PlannerTestUtils.generateTestdataSolution("uninitialized", 2)
            : TestdataSolution.generateSolution(2, 2);
    var result = solver.solve(input);
    assertThat(result.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
    return steps.get();
  }
}
