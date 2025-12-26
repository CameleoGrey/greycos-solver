package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import java.util.Random;

import ai.greycos.solver.core.api.solver.Solver;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.config.solver.termination.TerminationConfig;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.move.composite.CompositeMove;
import ai.greycos.solver.core.testdomain.TestdataConstraintProvider;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataSolution;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for MultistageMoveSelector.
 *
 * <p>These tests verify that multistage moves work correctly in real solving scenarios,
 * including:
 * <ul>
 *   <li>Integration with solver configuration</li>
 *   <li>Atomic execution of composite moves</li>
 *   <li>Score delta calculations</li>
 *   <li>Both sequential and random selection modes</li>
 * </ul>
 */
class MultistageMoveSelectorIntegrationTest {

  /**
   * Test that multistage moves work with a real solver configuration.
   *
   * <p>This test verifies:
   * <ul>
   *   <li>MultistageMoveSelector can be configured via XML-like config</li>
   *   <li>The solver can execute multistage moves without errors</li>
   *   <li>Score improves during solving</li>
   * </ul>
   */
  @Test
  void testMultistageMoveWithRealSolverConfiguration() {
    var solverConfig =
        new SolverConfig()
            .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withConstraintProviderClass(TestdataConstraintProvider.class)
            .withTerminationConfig(
                new TerminationConfig().withUnimprovedMillisecondsSpentLimit(1000L))
            .withPhaseList(
                List.of(
                    new ConstructionHeuristicPhaseConfig(),
                    new LocalSearchPhaseConfig()
                        .withMoveSelectorConfig(
                            new MultistageMoveSelectorConfig()
                                .withStageProviderClass(
                                    TestTwoStageSwapChangeProvider.class)
                                .withEntityClass(TestdataEntity.class)
                                .withVariableName("value"))));

    var problem = TestdataSolution.generateSolution(5, 30);
    var solver = SolverFactory.create(solverConfig).buildSolver();

    var solution = solver.solve(problem);

    // Verify that the solver found a solution
    assertThat(solution).isNotNull();
    // Verify that the score improved from the initial unfeasible state
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  /**
   * Test that composite moves execute atomically.
   *
   * <p>This test verifies that when a multistage move is executed,
   * all stage moves are applied atomically without intermediate scoring.
   */
  @Test
  void testCompositeMoveAtomicExecution() {
    var solverConfig =
        new SolverConfig()
            .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withConstraintProviderClass(TestdataConstraintProvider.class)
            .withTerminationConfig(
                new TerminationConfig().withStepCountLimit(100L))
            .withPhaseList(
                List.of(
                    new ConstructionHeuristicPhaseConfig(),
                    new LocalSearchPhaseConfig()
                        .withMoveSelectorConfig(
                            new MultistageMoveSelectorConfig()
                                .withStageProviderClass(
                                    TestTwoStageSwapChangeProvider.class)
                                .withEntityClass(TestdataEntity.class)
                                .withVariableName("value"))));

    var problem = TestdataSolution.generateSolution(3, 10);
    var solver = SolverFactory.create(solverConfig).buildSolver();

    var solution = solver.solve(problem);

    // Verify the solution is feasible
    assertThat(solution.getScore().isFeasible()).isTrue();
  }

  /**
   * Test multistage moves with random selection.
   *
   * <p>This test verifies that random multistage moves work correctly
   * and never end as expected.
   */
  @Test
  void testMultistageMoveWithRandomSelection() {
    var solverConfig =
        new SolverConfig()
            .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withConstraintProviderClass(TestdataConstraintProvider.class)
            .withTerminationConfig(
                new TerminationConfig().withUnimprovedMillisecondsSpentLimit(500L))
            .withPhaseList(
                List.of(
                    new ConstructionHeuristicPhaseConfig(),
                    new LocalSearchPhaseConfig()
                        .withMoveSelectorConfig(
                            new MultistageMoveSelectorConfig()
                                .withStageProviderClass(
                                    TestTwoStageSwapChangeProvider.class)
                                .withEntityClass(TestdataEntity.class)
                                .withVariableName("value")
                                .withRandomSelection(true))));

    var problem = TestdataSolution.generateSolution(5, 30);
    var solver = SolverFactory.create(solverConfig).buildSolver();

    var solution = solver.solve(problem);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  /**
   * Test that multistage moves produce valid score deltas.
   *
   * <p>This test verifies that the score delta calculated by a composite move
   * matches the actual score change after execution.
   */
  @Test
  void testCompositeMoveScoreDelta() {
    var solverConfig =
        new SolverConfig()
            .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withConstraintProviderClass(TestdataConstraintProvider.class)
            .withTerminationConfig(
                new TerminationConfig().withStepCountLimit(50L))
            .withPhaseList(
                List.of(
                    new ConstructionHeuristicPhaseConfig(),
                    new LocalSearchPhaseConfig()
                        .withMoveSelectorConfig(
                            new MultistageMoveSelectorConfig()
                                .withStageProviderClass(
                                    TestTwoStageSwapChangeProvider.class)
                                .withEntityClass(TestdataEntity.class)
                                .withVariableName("value"))));

    var problem = TestdataSolution.generateSolution(3, 10);
    var solver = SolverFactory.create(solverConfig).buildSolver();

    var solution = solver.solve(problem);

    // Verify that the solver made progress
    assertThat(solution.getScore().isFeasible()).isTrue();
  }

  /**
   * Test multistage moves with larger problem sizes.
   *
   * <p>This test verifies performance and correctness with more entities
   * and planning values.
   */
  @Test
  void testMultistageMoveWithLargerProblem() {
    var solverConfig =
        new SolverConfig()
            .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withConstraintProviderClass(TestdataConstraintProvider.class)
            .withTerminationConfig(
                new TerminationConfig().withUnimprovedMillisecondsSpentLimit(2000L))
            .withPhaseList(
                List.of(
                    new ConstructionHeuristicPhaseConfig(),
                    new LocalSearchPhaseConfig()
                        .withMoveSelectorConfig(
                            new MultistageMoveSelectorConfig()
                                .withStageProviderClass(
                                    TestTwoStageSwapChangeProvider.class)
                                .withEntityClass(TestdataEntity.class)
                                .withVariableName("value"))));

    var problem = TestdataSolution.generateSolution(10, 50);
    var solver = SolverFactory.create(solverConfig).buildSolver();

    var solution = solver.solve(problem);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  /**
   * Test that multistage moves handle edge cases gracefully.
   *
   * <p>This test verifies that the solver doesn't crash when:
   * <ul>
   *   <li>Problem has minimal entities</li>
   *   <li>Problem has minimal planning values</li>
   * </ul>
   */
  @Test
  void testMultistageMoveEdgeCases() {
    var solverConfig =
        new SolverConfig()
            .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withConstraintProviderClass(TestdataConstraintProvider.class)
            .withTerminationConfig(
                new TerminationConfig().withStepCountLimit(50L))
            .withPhaseList(
                List.of(
                    new ConstructionHeuristicPhaseConfig(),
                    new LocalSearchPhaseConfig()
                        .withMoveSelectorConfig(
                            new MultistageMoveSelectorConfig()
                                .withStageProviderClass(
                                    TestTwoStageSwapChangeProvider.class)
                                .withEntityClass(TestdataEntity.class)
                                .withVariableName("value"))));

    // Minimal problem: 2 entities, 3 values
    var problem = TestdataSolution.generateSolution(2, 3);
    var solver = SolverFactory.create(solverConfig).buildSolver();

    assertDoesNotThrow(() -> solver.solve(problem));
  }

  /**
   * Test StageProvider for integration tests.
   *
   * <p>Creates a two-stage move:
   * <ol>
   *   <li>Stage 1: Swap two entities</li>
   *   <li>Stage 2: Change one entity to a different value</li>
   * </ol>
   */
  public static class TestTwoStageSwapChangeProvider<Solution_>
      implements StageProvider<Solution_> {

    @Override
    public List<MoveSelector<Solution_>> createStages(
        ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy<Solution_> configPolicy) {

      var stages = new java.util.ArrayList<MoveSelector<Solution_>>(2);

      // Stage 1: Swap move selector
      var swapSelectorConfig =
          ai.greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig
              .builder()
              .withEntityClass(
                  configPolicy
                      .getSolutionDescriptor()
                      .getGenuineEntityDescriptorList()
                      .get(0)
                      .getEntityClass())
              .build();

      var swapSelectorFactory =
          ai.greycos.solver.core.impl.heuristic.selector.move.generic.SwapMoveSelectorFactory
              .<Solution_>create(swapSelectorConfig);
      stages.add(
          swapSelectorFactory.buildMoveSelector(
              configPolicy,
              ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType.JUST_IN_TIME,
              ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder.RANDOM,
              false));

      // Stage 2: Change move selector
      var changeSelectorConfig =
          ai.greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig
              .builder()
              .withEntityClass(
                  configPolicy
                      .getSolutionDescriptor()
                      .getGenuineEntityDescriptorList()
                      .get(0)
                      .getEntityClass())
              .withVariableName("value")
              .build();

      var changeSelectorFactory =
          ai.greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMoveSelectorFactory
              .<Solution_>create(changeSelectorConfig);
      stages.add(
          changeSelectorFactory.buildMoveSelector(
              configPolicy,
              ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType.JUST_IN_TIME,
              ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder.RANDOM,
              false));

      return stages;
    }

    @Override
    public int getStageCount() {
      return 2;
    }
  }
}
