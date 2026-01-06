package ai.greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.config.solver.termination.TerminationConfig;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataSolution;
import ai.greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

/** Integration tests for {@link DefaultIslandModelPhase}. */
class DefaultIslandModelPhaseTest {

  /** Simple score calculator for testing. Penalizes unassigned entities. */
  public static class TestdataSimpleScoreCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {

    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      var score = 0;
      for (var entity : solution.getEntityList()) {
        if (entity.getValue() == null) {
          score -= 10;
        } else {
          score -= 1;
        }
      }
      return SimpleScore.of(score);
    }
  }

  @Test
  void solveWithIslandModelLocalSearch() {
    var islandConfig = baseIslandConfig(2);
    var solverConfig = buildSolverConfig(islandConfig);

    var solution = TestdataSolution.generateUninitializedSolution(2, 2);

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isNotNull();
    assertAllEntitiesAssigned(bestSolution);
  }

  @Test
  void solveWithSingleIsland() {
    var islandConfig = baseIslandConfig(1);
    var solverConfig = buildSolverConfig(islandConfig);

    var solution = TestdataSolution.generateUninitializedSolution(2, 2);

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isNotNull();
    assertAllEntitiesAssigned(bestSolution);
  }

  @Test
  void solveWithCompareGlobalEnabled() {
    var islandConfig = baseIslandConfig(2).withCompareGlobalEnabled(true);
    var solverConfig = buildSolverConfig(islandConfig);

    var solution = TestdataSolution.generateUninitializedSolution(2, 2);

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isNotNull();
    assertAllEntitiesAssigned(bestSolution);
  }

  @Test
  void solveWithCompareGlobalDisabled() {
    var islandConfig = baseIslandConfig(2).withCompareGlobalEnabled(false);
    var solverConfig = buildSolverConfig(islandConfig);

    var solution = TestdataSolution.generateUninitializedSolution(2, 2);

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isNotNull();
    assertAllEntitiesAssigned(bestSolution);
  }

  @Test
  void solveWithHighMigrationFrequency() {
    var islandConfig = baseIslandConfig(2).withMigrationFrequency(1000);
    var solverConfig = buildSolverConfig(islandConfig);

    var solution = TestdataSolution.generateUninitializedSolution(2, 2);

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isNotNull();
    assertAllEntitiesAssigned(bestSolution);
  }

  @Test
  void solveWithInvalidIslandCount() {
    var islandConfig = baseIslandConfig(1).withIslandCount(0);
    var solverConfig = buildSolverConfig(islandConfig);
    var solution = new TestdataSolution("s1");

    assertThatThrownBy(() -> PlannerTestUtils.solve(solverConfig, solution))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Island count must be at least 1, but was: 0");
  }

  @Test
  void solveWithExcessiveIslandCount() {
    var islandConfig = baseIslandConfig(1).withIslandCount(101);
    var solverConfig = buildSolverConfig(islandConfig);
    var solution = new TestdataSolution("s1");

    assertThatThrownBy(() -> PlannerTestUtils.solve(solverConfig, solution))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Island count must not exceed 100, but was: 101");
  }

  @Test
  void solveWithInvalidMigrationFrequency() {
    var islandConfig = baseIslandConfig(2).withMigrationFrequency(0);
    var solverConfig = buildSolverConfig(islandConfig);
    var solution = new TestdataSolution("s1");

    assertThatThrownBy(() -> PlannerTestUtils.solve(solverConfig, solution))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Migration frequency must be at least 1, but was: 0");
  }

  @Test
  void solveWithInvalidMigrationTimeout() {
    var islandConfig = baseIslandConfig(2).withMigrationTimeout(0L);
    var solverConfig = buildSolverConfig(islandConfig);
    var solution = new TestdataSolution("s1");

    assertThatThrownBy(() -> PlannerTestUtils.solve(solverConfig, solution))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Migration timeout must be at least 1, but was: 0");
  }

  private SolverConfig buildSolverConfig(IslandModelPhaseConfig islandConfig) {
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
        .withPhases(
            new ConstructionHeuristicPhaseConfig()
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(5)),
            islandConfig);
  }

  private IslandModelPhaseConfig baseIslandConfig(int islandCount) {
    return new IslandModelPhaseConfig()
        .withIslandCount(islandCount)
        .withMigrationFrequency(5)
        .withReceiveGlobalUpdateFrequency(2)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(10));
  }

  private void assertAllEntitiesAssigned(TestdataSolution bestSolution) {
    assertThat(bestSolution.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
  }
}
