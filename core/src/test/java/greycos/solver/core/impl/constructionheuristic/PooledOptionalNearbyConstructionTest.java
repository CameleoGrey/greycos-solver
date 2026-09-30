package greycos.solver.core.impl.constructionheuristic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedEntity;
import greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedSolution;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PooledOptionalNearbyConstructionTest {

  @ParameterizedTest
  @CsvSource({"true,NONE", "false,NONE", "true,2", "false,2"})
  void choosingOptionalNullEndsThePoolNaturally(boolean automaticNearby, String moveThreads) {
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withConstructionHeuristicType(ConstructionHeuristicType.ALLOCATE_FROM_POOL)
            .withNearbySelectionAutoConfigurationEnabled(automaticNearby)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(5));
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataAllowsUnassignedSolution.class)
            .withEntityClasses(TestdataAllowsUnassignedEntity.class)
            .withEasyScoreCalculatorClass(PenalizeAssignment.class)
            .withNearbyDistanceMeterClass(DistanceMeter.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(moveThreads)
            .withPhases(phase);
    var solver =
        (DefaultSolver<TestdataAllowsUnassignedSolution>)
            SolverFactory.<TestdataAllowsUnassignedSolution>create(config).buildSolver();
    var steps = new AtomicInteger();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataAllowsUnassignedSolution> stepScope) {
            steps.incrementAndGet();
          }
        });
    var solution = solver.solve(TestdataAllowsUnassignedSolution.generateSolution(1, 1));
    assertThat(steps.get()).isEqualTo(1);
    assertThat(solution.getEntityList().getFirst().getValue()).isNull();
    assertThat(solution.getScore()).isEqualTo(new PenalizeAssignment().calculateScore(solution));
    assertThat(solution.getScore()).isEqualTo(SimpleScore.ZERO);
  }

  public static final class PenalizeAssignment
      implements EasyScoreCalculator<TestdataAllowsUnassignedSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataAllowsUnassignedSolution solution) {
      return SimpleScore.of(
          -Math.toIntExact(
              solution.getEntityList().stream()
                  .filter(entity -> entity.getValue() != null)
                  .count()));
    }
  }

  public static final class DistanceMeter
      implements NearbyDistanceMeter<TestdataAllowsUnassignedEntity, TestdataValue> {
    @Override
    public double getNearbyDistance(
        TestdataAllowsUnassignedEntity origin, TestdataValue destination) {
      return 0;
    }
  }
}
