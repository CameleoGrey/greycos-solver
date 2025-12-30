package ai.greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.config.solver.termination.TerminationConfig;
import ai.greycos.solver.core.impl.event.BestScoreChangedEvent;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataSolution;
import ai.greycos.solver.core.testdomain.TestdataValue;
import ai.greycos.solver.core.testutil.PlannerTestUtils;

import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Integration tests for {@link DefaultIslandModelPhase}.
 *
 * <p>These tests verify that island model phase works correctly with different configurations,
 * including migration, termination and global best tracking.
 */
@Execution(ExecutionMode.CONCURRENT)
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
  void solveWithConstructionHeuristic() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withMigrationFrequency(10)
                    .withPhaseConfig(
                        new ConstructionHeuristicPhaseConfig()
                            .withTerminationConfig(new TerminationConfig().withStepCountLimit(5))));

    var solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    solution.setValueList(List.of(v1, v2));
    solution.setEntityList(List.of(new TestdataEntity("e1", null), new TestdataEntity("e2", null)));

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isNotNull();
    // Both entities should be assigned
    assertThat(bestSolution.getEntityList().stream().filter(e -> e.getValue() == null)).isEmpty();
    // Filter out BestScoreChangedEvent from SOLVING_STARTED (initial solution adds these)
    List<BestScoreChangedEvent> bestScoreEvents =
        bestSolution.getSolverEventList().stream()
            .filter(e -> e instanceof BestScoreChangedEvent)
            .map(e -> (BestScoreChangedEvent) e)
            .filter(e -> !"SOLVING_STARTED".equals(e.getEventProducerId()))
            .toList();
    assertThat(bestScoreEvents).isEmpty();
  }

  @Test
  void solveWithLocalSearch() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withMigrationFrequency(10)
                    .withPhaseConfig(
                        new LocalSearchPhaseConfig()
                            .withTerminationConfig(
                                new TerminationConfig().withStepCountLimit(20))));

    var solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    solution.setValueList(List.of(v1, v2));
    solution.setEntityList(List.of(new TestdataEntity("e1", v1), new TestdataEntity("e2", v2)));

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isNotNull();
    assertThat(bestSolution.getScore().compareTo(SimpleScore.of(-20))).isLessThan(0);
  }

  @Test
  void solveWithMultipleIslands() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(4)
                    .withMigrationFrequency(10)
                    .withPhaseConfig(
                        new ConstructionHeuristicPhaseConfig()
                            .withTerminationConfig(new TerminationConfig().withStepCountLimit(5))));

    var solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    solution.setValueList(List.of(v1, v2));
    solution.setEntityList(
        List.of(
            new TestdataEntity("e1", null),
            new TestdataEntity("e2", null),
            new TestdataEntity("e3", null),
            new TestdataEntity("e4", null)));

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isNotNull();
    // With more islands and more entities, should find better solution
    assertThat(bestSolution.getScore().compareTo(SimpleScore.of(-40))).isLessThan(0);
    // Filter out BestScoreChangedEvent from SOLVING_STARTED (initial solution adds these)
    List<BestScoreChangedEvent> bestScoreEvents =
        bestSolution.getSolverEventList().stream()
            .filter(e -> e instanceof BestScoreChangedEvent)
            .map(e -> (BestScoreChangedEvent) e)
            .filter(e -> !"SOLVING_STARTED".equals(e.getEventProducerId()))
            .toList();
    assertThat(bestScoreEvents).isEmpty();
  }

  @Test
  void solveWithHighMigrationFrequency() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withMigrationFrequency(1000)
                    .withPhaseConfig(
                        new ConstructionHeuristicPhaseConfig()
                            .withTerminationConfig(
                                new TerminationConfig().withStepCountLimit(10))));

    var solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    solution.setValueList(List.of(v1, v2));
    solution.setEntityList(List.of(new TestdataEntity("e1", null), new TestdataEntity("e2", null)));

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isNotNull();
    // Filter out BestScoreChangedEvent from SOLVING_STARTED (initial solution adds these)
    List<BestScoreChangedEvent> bestScoreEvents =
        bestSolution.getSolverEventList().stream()
            .filter(e -> e instanceof BestScoreChangedEvent)
            .map(e -> (BestScoreChangedEvent) e)
            .filter(e -> !"SOLVING_STARTED".equals(e.getEventProducerId()))
            .toList();
    assertThat(bestScoreEvents).isEmpty();
  }

  @Test
  void solveWithInvalidIslandCount() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(0)
                    .withPhaseConfig(new ConstructionHeuristicPhaseConfig()));

    var solution = new TestdataSolution("s1");

    assertThatThrownBy(() -> PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Island count must be at least 1");
  }

  @Test
  void solveWithExcessiveIslandCount() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(101)
                    .withPhaseConfig(new ConstructionHeuristicPhaseConfig()));

    var solution = new TestdataSolution("s1");

    assertThatThrownBy(() -> PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Island count must not exceed 100");
  }

  @Test
  void solveWithInvalidMigrationRate() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withMigrationRate(-0.1)
                    .withPhaseConfig(new ConstructionHeuristicPhaseConfig()));

    var solution = new TestdataSolution("s1");

    assertThatThrownBy(() -> PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Migration rate must be between 0.0 and 1.0");
  }

  @Test
  void solveWithInvalidMigrationFrequency() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withMigrationFrequency(0)
                    .withPhaseConfig(new ConstructionHeuristicPhaseConfig()));

    var solution = new TestdataSolution("s1");

    assertThatThrownBy(() -> PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Migration frequency must be at least 1");
  }

  @Test
  void solveWithNoWrappedPhaseConfig() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(new IslandModelPhaseConfig().withIslandCount(2));

    var solution = new TestdataSolution("s1");
    // Initialize valueList to avoid null pointer exception
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    solution.setValueList(List.of(v1, v2));
    solution.setEntityList(List.of(new TestdataEntity("e1", null), new TestdataEntity("e2", null)));

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty);

    // Should complete without error but with no phases
    assertThat(bestSolution).isNotNull();
    // Filter out BestScoreChangedEvent from SOLVING_STARTED (initial solution adds these)
    List<BestScoreChangedEvent> bestScoreEvents =
        bestSolution.getSolverEventList().stream()
            .filter(e -> e instanceof BestScoreChangedEvent)
            .map(e -> (BestScoreChangedEvent) e)
            .filter(e -> !"SOLVING_STARTED".equals(e.getEventProducerId()))
            .toList();
    assertThat(bestScoreEvents).isEmpty();
  }

  @Test
  void verifyGlobalBestTracking() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withMigrationFrequency(5)
                    .withPhaseConfig(
                        new ConstructionHeuristicPhaseConfig()
                            .withTerminationConfig(
                                new TerminationConfig().withStepCountLimit(10))));

    var solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    solution.setValueList(List.of(v1, v2));
    solution.setEntityList(List.of(new TestdataEntity("e1", null), new TestdataEntity("e2", null)));

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isNotNull();
    // Both entities should be assigned
    assertThat(bestSolution.getEntityList().stream().filter(e -> e.getValue() == null)).isEmpty();
    // Filter out BestScoreChangedEvent from SOLVING_STARTED (initial solution adds these)
    List<BestScoreChangedEvent> bestScoreEvents =
        bestSolution.getSolverEventList().stream()
            .filter(e -> e instanceof BestScoreChangedEvent)
            .map(e -> (BestScoreChangedEvent) e)
            .filter(e -> !"SOLVING_STARTED".equals(e.getEventProducerId()))
            .toList();
    assertThat(bestScoreEvents).isEmpty();
  }

  @Test
  void verifyTerminationWithBestScoreLimit() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withMigrationFrequency(10)
                    .withPhaseConfig(
                        new ConstructionHeuristicPhaseConfig()
                            .withTerminationConfig(
                                new TerminationConfig().withBestScoreLimit("0"))));

    var solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    solution.setValueList(List.of(v1, v2));
    solution.setEntityList(List.of(new TestdataEntity("e1", null), new TestdataEntity("e2", null)));

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isEqualTo(SimpleScore.ZERO);
    // Filter out BestScoreChangedEvent from SOLVING_STARTED (initial solution adds these)
    List<BestScoreChangedEvent> bestScoreEvents =
        bestSolution.getSolverEventList().stream()
            .filter(e -> e instanceof BestScoreChangedEvent)
            .map(e -> (BestScoreChangedEvent) e)
            .filter(e -> !"SOLVING_STARTED".equals(e.getEventProducerId()))
            .toList();
    assertThat(bestScoreEvents).isEmpty();
  }

  @Test
  void solveWithSingleIsland() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(1)
                    .withMigrationFrequency(10)
                    .withPhaseConfig(
                        new ConstructionHeuristicPhaseConfig()
                            .withTerminationConfig(new TerminationConfig().withStepCountLimit(5))));

    var solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    solution.setValueList(List.of(v1, v2));
    solution.setEntityList(List.of(new TestdataEntity("e1", null), new TestdataEntity("e2", null)));

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isNotNull();
    // With single island, should behave like regular phase
    assertThat(bestSolution.getEntityList().stream().filter(e -> e.getValue() == null)).isEmpty();
    // Filter out BestScoreChangedEvent from SOLVING_STARTED (initial solution adds these)
    List<BestScoreChangedEvent> bestScoreEvents =
        bestSolution.getSolverEventList().stream()
            .filter(e -> e instanceof BestScoreChangedEvent)
            .map(e -> (BestScoreChangedEvent) e)
            .filter(e -> !"SOLVING_STARTED".equals(e.getEventProducerId()))
            .toList();
    assertThat(bestScoreEvents).isEmpty();
  }

  @Test
  void solveWithCompareGlobalEnabled() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withMigrationFrequency(10)
                    .withCompareGlobalEnabled(true)
                    .withCompareGlobalFrequency(5)
                    .withPhaseConfig(
                        new LocalSearchPhaseConfig()
                            .withTerminationConfig(
                                new TerminationConfig().withStepCountLimit(20))));

    var solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    solution.setValueList(List.of(v1, v2));
    solution.setEntityList(List.of(new TestdataEntity("e1", v1), new TestdataEntity("e2", v2)));

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isNotNull();
    // Compare-to-global should help find better solution
    assertThat(bestSolution.getScore().compareTo(SimpleScore.of(-20))).isLessThan(0);
    // Filter out BestScoreChangedEvent from SOLVING_STARTED (compare-to-global feature adds these)
    List<BestScoreChangedEvent> bestScoreEvents =
        bestSolution.getSolverEventList().stream()
            .filter(e -> e instanceof BestScoreChangedEvent)
            .map(e -> (BestScoreChangedEvent) e)
            .filter(e -> !"SOLVING_STARTED".equals(e.getEventProducerId()))
            .toList();
    assertThat(bestScoreEvents).isEmpty();
  }

  @Test
  void solveWithCompareGlobalDisabled() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withMigrationFrequency(10)
                    .withCompareGlobalEnabled(false)
                    .withPhaseConfig(
                        new LocalSearchPhaseConfig()
                            .withTerminationConfig(
                                new TerminationConfig().withStepCountLimit(20))));

    var solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    solution.setValueList(List.of(v1, v2));
    solution.setEntityList(List.of(new TestdataEntity("e1", v1), new TestdataEntity("e2", v2)));

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isNotNull();
    // Should still work with compare-to-global disabled
    assertThat(bestSolution.getScore().compareTo(SimpleScore.of(-20))).isLessThan(0);
    // Filter out BestScoreChangedEvent from SOLVING_STARTED (compare-to-global feature adds these)
    List<BestScoreChangedEvent> bestScoreEvents =
        bestSolution.getSolverEventList().stream()
            .filter(e -> e instanceof BestScoreChangedEvent)
            .map(e -> (BestScoreChangedEvent) e)
            .filter(e -> !"SOLVING_STARTED".equals(e.getEventProducerId()))
            .toList();
    assertThat(bestScoreEvents).isEmpty();
  }

  @Test
  void solveWithCompareGlobalAndMigration() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(3)
                    .withMigrationFrequency(10)
                    .withCompareGlobalEnabled(true)
                    .withCompareGlobalFrequency(5)
                    .withPhaseConfig(
                        new LocalSearchPhaseConfig()
                            .withTerminationConfig(
                                new TerminationConfig().withStepCountLimit(30))));

    var solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    solution.setValueList(List.of(v1, v2));
    solution.setEntityList(List.of(new TestdataEntity("e1", v1), new TestdataEntity("e2", v2)));

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isNotNull();
    // Both mechanisms should work together
    assertThat(bestSolution.getScore().compareTo(SimpleScore.of(-20))).isLessThan(0);
    // Filter out BestScoreChangedEvent from SOLVING_STARTED (compare-to-global feature adds these)
    List<BestScoreChangedEvent> bestScoreEvents =
        bestSolution.getSolverEventList().stream()
            .filter(e -> e instanceof BestScoreChangedEvent)
            .map(e -> (BestScoreChangedEvent) e)
            .filter(e -> !"SOLVING_STARTED".equals(e.getEventProducerId()))
            .toList();
    assertThat(bestScoreEvents).isEmpty();
  }

  @Test
  void solveWithCompareGlobalDifferentFrequencies() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withMigrationFrequency(20)
                    .withCompareGlobalEnabled(true)
                    .withCompareGlobalFrequency(3)
                    .withPhaseConfig(
                        new LocalSearchPhaseConfig()
                            .withTerminationConfig(
                                new TerminationConfig().withStepCountLimit(25))));

    var solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    solution.setValueList(List.of(v1, v2));
    solution.setEntityList(List.of(new TestdataEntity("e1", v1), new TestdataEntity("e2", v2)));

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isNotNull();
    // Different frequencies should work
    assertThat(bestSolution.getScore().compareTo(SimpleScore.of(-20))).isLessThan(0);
    // Filter out BestScoreChangedEvent from SOLVING_STARTED (compare-to-global feature adds these)
    List<BestScoreChangedEvent> bestScoreEvents =
        bestSolution.getSolverEventList().stream()
            .filter(e -> e instanceof BestScoreChangedEvent)
            .map(e -> (BestScoreChangedEvent) e)
            .filter(e -> !"SOLVING_STARTED".equals(e.getEventProducerId()))
            .toList();
    assertThat(bestScoreEvents).isEmpty();
  }

  @Test
  void solveWithCompareGlobalWithConstructionHeuristic() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataSimpleScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withCompareGlobalEnabled(true)
                    .withCompareGlobalFrequency(5)
                    .withPhaseConfig(
                        new ConstructionHeuristicPhaseConfig()
                            .withTerminationConfig(
                                new TerminationConfig().withStepCountLimit(10))));

    var solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    solution.setValueList(List.of(v1, v2));
    solution.setEntityList(
        List.of(
            new TestdataEntity("e1", null),
            new TestdataEntity("e2", null),
            new TestdataEntity("e3", null),
            new TestdataEntity("e4", null)));

    var bestSolution = PlannerTestUtils.solve(solverConfig, solution, ListAssert::isEmpty);

    assertThat(bestSolution).isNotNull();
    assertThat(bestSolution.getScore()).isNotNull();
    // Compare-to-global should not break construction heuristic
    assertThat(bestSolution.getEntityList().stream().filter(e -> e.getValue() == null)).isEmpty();
    // Filter out BestScoreChangedEvent from SOLVING_STARTED (compare-to-global feature adds these)
    List<BestScoreChangedEvent> bestScoreEvents =
        bestSolution.getSolverEventList().stream()
            .filter(e -> e instanceof BestScoreChangedEvent)
            .map(e -> (BestScoreChangedEvent) e)
            .filter(e -> !"SOLVING_STARTED".equals(e.getEventProducerId()))
            .toList();
    assertThat(bestScoreEvents).isEmpty();
  }
}
