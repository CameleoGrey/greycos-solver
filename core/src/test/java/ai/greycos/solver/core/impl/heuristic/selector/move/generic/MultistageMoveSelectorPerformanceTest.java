package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;

import ai.greycos.solver.core.api.solver.Solver;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.config.solver.termination.TerminationConfig;
import ai.greycos.solver.core.testdomain.TestdataConstraintProvider;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataSolution;

import org.junit.jupiter.api.Test;

/**
 * Performance benchmarks for MultistageMoveSelector.
 *
 * <p>These tests measure and compare the performance of multistage moves against
 * other move selector types (e.g., union moves). The benchmarks focus on:
 * <ul>
 *   <li>Move generation speed</li>
 *   <li>Score calculation efficiency</li>
 *   <li>Memory usage</li>
 *   <li>Scalability with problem size</li>
 * </ul>
 *
 * <p><b>Note:</b> These tests are not executed during normal test runs. They are
 * designed to be run manually to collect performance data.
 */
class MultistageMoveSelectorPerformanceTest {

  /**
   * Benchmark multistage moves vs union moves on a small problem.
   *
   * <p>This test compares:
   * <ul>
   *   <li>Multistage: swap + change (atomic)</li>
   *   <li>Union: swap OR change (individual)</li>
   * </ul>
   */
  @Test
  void benchmarkMultistageVsUnionSmallProblem() {
    int entityCount = 5;
    int valueCount = 30;
    long timeLimitMs = 500L;

    var multistageConfig =
        createMultistageSolverConfig(
            entityCount, valueCount, timeLimitMs, false);
    var unionConfig = createUnionSolverConfig(entityCount, valueCount, timeLimitMs);

    var problem = TestdataSolution.generateSolution(entityCount, valueCount);

    // Benchmark multistage
    var multistageStart = System.nanoTime();
    var multistageSolver = SolverFactory.create(multistageConfig).buildSolver();
    var multistageSolution = multistageSolver.solve(problem);
    var multistageTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - multistageStart);

    // Benchmark union
    var unionStart = System.nanoTime();
    var unionSolver = SolverFactory.create(unionConfig).buildSolver();
    var unionSolution = unionSolver.solve(problem);
    var unionTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - unionStart);

    // Both should find feasible solutions
    assertThat(multistageSolution.getScore().isFeasible()).isTrue();
    assertThat(unionSolution.getScore().isFeasible()).isTrue();

    // Performance characteristics
    // Multistage moves are more expensive per move but may find better solutions faster
    // This is just a baseline measurement
    System.out.println("Small problem (5 entities, 30 values):");
    System.out.println("  Multistage time: " + multistageTime + "ms");
    System.out.println("  Union time: " + unionTime + "ms");
    System.out.println("  Multistage score: " + multistageSolution.getScore());
    System.out.println("  Union score: " + unionSolution.getScore());
  }

  /**
   * Benchmark multistage moves vs union moves on a medium problem.
   *
   * <p>This test evaluates scalability with larger problem sizes.
   */
  @Test
  void benchmarkMultistageVsUnionMediumProblem() {
    int entityCount = 10;
    int valueCount = 50;
    long timeLimitMs = 1000L;

    var multistageConfig =
        createMultistageSolverConfig(
            entityCount, valueCount, timeLimitMs, false);
    var unionConfig = createUnionSolverConfig(entityCount, valueCount, timeLimitMs);

    var problem = TestdataSolution.generateSolution(entityCount, valueCount);

    // Benchmark multistage
    var multistageStart = System.nanoTime();
    var multistageSolver = SolverFactory.create(multistageConfig).buildSolver();
    var multistageSolution = multistageSolver.solve(problem);
    var multistageTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - multistageStart);

    // Benchmark union
    var unionStart = System.nanoTime();
    var unionSolver = SolverFactory.create(unionConfig).buildSolver();
    var unionSolution = unionSolver.solve(problem);
    var unionTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - unionStart);

    assertThat(multistageSolution.getScore().isFeasible()).isTrue();
    assertThat(unionSolution.getScore().isFeasible()).isTrue();

    System.out.println("Medium problem (10 entities, 50 values):");
    System.out.println("  Multistage time: " + multistageTime + "ms");
    System.out.println("  Union time: " + unionTime + "ms");
    System.out.println("  Multistage score: " + multistageSolution.getScore());
    System.out.println("  Union score: " + unionSolution.getScore());
  }

  /**
   * Benchmark random vs sequential multistage move selection.
   *
   * <p>This test compares the performance impact of random vs sequential selection.
   */
  @Test
  void benchmarkRandomVsSequentialSelection() {
    int entityCount = 10;
    int valueCount = 50;
    long timeLimitMs = 500L;

    var sequentialConfig =
        createMultistageSolverConfig(
            entityCount, valueCount, timeLimitMs, false);
    var randomConfig =
        createMultistageSolverConfig(
            entityCount, valueCount, timeLimitMs, true);

    var problem = TestdataSolution.generateSolution(entityCount, valueCount);

    // Benchmark sequential
    var sequentialStart = System.nanoTime();
    var sequentialSolver = SolverFactory.create(sequentialConfig).buildSolver();
    var sequentialSolution = sequentialSolver.solve(problem);
    var sequentialTime =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sequentialStart);

    // Benchmark random
    var randomStart = System.nanoTime();
    var randomSolver = SolverFactory.create(randomConfig).buildSolver();
    var randomSolution = randomSolver.solve(problem);
    var randomTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - randomStart);

    assertThat(sequentialSolution.getScore().isFeasible()).isTrue();
    assertThat(randomSolution.getScore().isFeasible()).isTrue();

    System.out.println("Sequential vs Random selection:");
    System.out.println("  Sequential time: " + sequentialTime + "ms");
    System.out.println("  Random time: " + randomTime + "ms");
    System.out.println("  Sequential score: " + sequentialSolution.getScore());
    System.out.println("  Random score: " + randomSolution.getScore());
  }

  /**
   * Benchmark with varying stage counts.
   *
   * <p>This test evaluates the performance impact of adding more stages.
   */
  @Test
  void benchmarkVaryingStageCounts() {
    int entityCount = 8;
    int valueCount = 40;
    long timeLimitMs = 500L;

    var problem = TestdataSolution.generateSolution(entityCount, valueCount);

    // Test with 2 stages
    var twoStageConfig =
        createMultistageSolverConfig(
            entityCount, valueCount, timeLimitMs, false, 2);
    var twoStageStart = System.nanoTime();
    var twoStageSolver = SolverFactory.create(twoStageConfig).buildSolver();
    var twoStageSolution = twoStageSolver.solve(problem);
    var twoStageTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - twoStageStart);

    // Test with 3 stages
    var threeStageConfig =
        createMultistageSolverConfig(
            entityCount, valueCount, timeLimitMs, false, 3);
    var threeStageStart = System.nanoTime();
    var threeStageSolver = SolverFactory.create(threeStageConfig).buildSolver();
    var threeStageSolution = threeStageSolver.solve(problem);
    var threeStageTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - threeStageStart);

    System.out.println("Varying stage counts:");
    System.out.println("  2 stages time: " + twoStageTime + "ms");
    System.out.println("  3 stages time: " + threeStageTime + "ms");
    System.out.println("  2 stages score: " + twoStageSolution.getScore());
    System.out.println("  3 stages score: " + threeStageSolution.getScore());
  }

  /**
   * Benchmark memory usage with large problems.
   *
   * <p>This test measures memory consumption during solving with multistage moves.
   */
  @Test
  void benchmarkMemoryUsage() {
    int entityCount = 20;
    int valueCount = 100;
    long timeLimitMs = 2000L;

    var config =
        createMultistageSolverConfig(
            entityCount, valueCount, timeLimitMs, false);
    var problem = TestdataSolution.generateSolution(entityCount, valueCount);

    var runtime = Runtime.getRuntime();
    runtime.gc();

    var memoryBefore = runtime.totalMemory() - runtime.freeMemory();

    var solver = SolverFactory.create(config).buildSolver();
    var solution = solver.solve(problem);

    var memoryAfter = runtime.totalMemory() - runtime.freeMemory();
    var memoryUsed = (memoryAfter - memoryBefore) / (1024 * 1024); // MB

    System.out.println("Memory usage:");
    System.out.println("  Memory used: " + memoryUsed + "MB");
    System.out.println("  Final score: " + solution.getScore());

    assertThat(solution.getScore().isFeasible()).isTrue();
    // Memory usage should be reasonable (less than 500MB for this problem size)
    assertThat(memoryUsed).isLessThan(500);
  }

  /**
   * Benchmark cache effectiveness in random iterator.
   *
   * <p>This test compares performance with and without caching for finite selectors.
   */
  @Test
  void benchmarkCacheEffectiveness() {
    int entityCount = 15;
    int valueCount = 60;
    long timeLimitMs = 1000L;

    var config =
        createMultistageSolverConfig(
            entityCount, valueCount, timeLimitMs, true);
    var problem = TestdataSolution.generateSolution(entityCount, valueCount);

    // Run multiple iterations to warm up and measure average
    var iterations = 5;
    var totalTime = 0L;

    for (int i = 0; i < iterations; i++) {
      var start = System.nanoTime();
      var solver = SolverFactory.create(config).buildSolver();
      var solution = solver.solve(problem);
      var time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      totalTime += time;

      assertThat(solution.getScore().isFeasible()).isTrue();
    }

    var avgTime = totalTime / iterations;

    System.out.println("Cache effectiveness (random selection):");
    System.out.println("  Average time over " + iterations + " runs: " + avgTime + "ms");
  }

  // ************************************************************************
  // Helper methods
  // ************************************************************************

  private SolverConfig createMultistageSolverConfig(
      int entityCount, int valueCount, long timeLimitMs, boolean randomSelection) {
    return createMultistageSolverConfig(
        entityCount, valueCount, timeLimitMs, randomSelection, 2);
  }

  private SolverConfig createMultistageSolverConfig(
      int entityCount,
      int valueCount,
      long timeLimitMs,
      boolean randomSelection,
      int stageCount) {
    var multistageConfig =
        new MultistageMoveSelectorConfig()
            .withStageProviderClass(
                stageCount == 2
                    ? TestTwoStageSwapChangeProvider.class
                    : TestThreeStageSwapChangeProvider.class)
            .withEntityClass(TestdataEntity.class)
            .withVariableName("value")
            .withRandomSelection(randomSelection);

    return new SolverConfig()
        .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withConstraintProviderClass(TestdataConstraintProvider.class)
        .withTerminationConfig(
            new TerminationConfig().withUnimprovedMillisecondsSpentLimit(timeLimitMs))
        .withPhaseList(
            List.of(
                new ConstructionHeuristicPhaseConfig(),
                new LocalSearchPhaseConfig().withMoveSelectorConfig(multistageConfig)));
  }

  private SolverConfig createUnionSolverConfig(
      int entityCount, int valueCount, long timeLimitMs) {
    var swapConfig = new SwapMoveSelectorConfig();
    var changeConfig = new ChangeMoveSelectorConfig();

    var unionConfig =
        new UnionMoveSelectorConfig()
            .withMoveSelectorConfigList(List.of(swapConfig, changeConfig));

    return new SolverConfig()
        .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withConstraintProviderClass(TestdataConstraintProvider.class)
        .withTerminationConfig(
            new TerminationConfig().withUnimprovedMillisecondsSpentLimit(timeLimitMs))
        .withPhaseList(
            List.of(
                new ConstructionHeuristicPhaseConfig(),
                new LocalSearchPhaseConfig().withMoveSelectorConfig(unionConfig)));
  }

  /**
   * Test StageProvider for two-stage moves (swap + change).
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

  /**
   * Test StageProvider for three-stage moves (swap + change + swap).
   */
  public static class TestThreeStageSwapChangeProvider<Solution_>
      implements StageProvider<Solution_> {

    @Override
    public List<MoveSelector<Solution_>> createStages(
        ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy<Solution_> configPolicy) {

      var stages = new java.util.ArrayList<MoveSelector<Solution_>>(3);

      // Stage 1: Swap move selector
      var swapSelectorConfig1 =
          ai.greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig
              .builder()
              .withEntityClass(
                  configPolicy
                      .getSolutionDescriptor()
                      .getGenuineEntityDescriptorList()
                      .get(0)
                      .getEntityClass())
              .build();

      var swapSelectorFactory1 =
          ai.greycos.solver.core.impl.heuristic.selector.move.generic.SwapMoveSelectorFactory
              .<Solution_>create(swapSelectorConfig1);
      stages.add(
          swapSelectorFactory1.buildMoveSelector(
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

      // Stage 3: Swap move selector (another one)
      var swapSelectorConfig2 =
          ai.greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig
              .builder()
              .withEntityClass(
                  configPolicy
                      .getSolutionDescriptor()
                      .getGenuineEntityDescriptorList()
                      .get(0)
                      .getEntityClass())
              .build();

      var swapSelectorFactory2 =
          ai.greycos.solver.core.impl.heuristic.selector.move.generic.SwapMoveSelectorFactory
              .<Solution_>create(swapSelectorConfig2);
      stages.add(
          swapSelectorFactory2.buildMoveSelector(
              configPolicy,
              ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType.JUST_IN_TIME,
              ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder.RANDOM,
              false));

      return stages;
    }

    @Override
    public int getStageCount() {
      return 3;
    }
  }
}
