package greycos.solver.benchmark.impl;

import java.util.UUID;
import java.util.concurrent.Callable;

import greycos.solver.benchmark.impl.result.SubSingleBenchmarkResult;
import greycos.solver.benchmark.impl.statistic.StatisticRegistry;
import greycos.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolutionUpdatePolicy;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.DefaultSolverFactory;
import greycos.solver.core.impl.solver.monitoring.SolverTags;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.micrometer.core.instrument.Metrics;

public class SubSingleBenchmarkRunner<Solution_>
    implements Callable<SubSingleBenchmarkRunner<Solution_>> {

  public static final String NAME_MDC = "subSingleBenchmark.name";

  private static final Logger LOGGER = LoggerFactory.getLogger(SubSingleBenchmarkRunner.class);

  private final SubSingleBenchmarkResult subSingleBenchmarkResult;
  private final boolean warmUp;

  private Long randomSeed = null;
  private Throwable failureThrowable = null;

  /**
   * @param subSingleBenchmarkResult never null
   */
  public SubSingleBenchmarkRunner(
      SubSingleBenchmarkResult subSingleBenchmarkResult, boolean warmUp) {
    this.subSingleBenchmarkResult = subSingleBenchmarkResult;
    this.warmUp = warmUp;
  }

  public SubSingleBenchmarkResult getSubSingleBenchmarkResult() {
    return subSingleBenchmarkResult;
  }

  public Long getRandomSeed() {
    return randomSeed;
  }

  public Throwable getFailureThrowable() {
    return failureThrowable;
  }

  public void setFailureThrowable(Throwable failureThrowable) {
    this.failureThrowable = failureThrowable;
  }

  // ************************************************************************
  // Benchmark methods
  // ************************************************************************

  @Override
  public SubSingleBenchmarkRunner<Solution_> call() {
    MDC.put(NAME_MDC, subSingleBenchmarkResult.getName());
    try {
      return runBenchmark();
    } finally {
      MDC.remove(NAME_MDC);
    }
  }

  private SubSingleBenchmarkRunner<Solution_> runBenchmark() {
    var runtime = Runtime.getRuntime();
    var singleBenchmarkResult = subSingleBenchmarkResult.getSingleBenchmarkResult();
    var problemBenchmarkResult = singleBenchmarkResult.getProblemBenchmarkResult();
    var problem = (Solution_) problemBenchmarkResult.readProblem();
    if (!problemBenchmarkResult.getPlannerBenchmarkResult().hasMultipleParallelBenchmarks()) {
      runtime.gc();
      subSingleBenchmarkResult.setUsedMemoryAfterInputSolution(
          runtime.totalMemory() - runtime.freeMemory());
    }
    LOGGER.trace(
        "Benchmark problem has been read for subSingleBenchmarkResult ({}).",
        subSingleBenchmarkResult);

    var solverConfig = singleBenchmarkResult.getSolverBenchmarkResult().getSolverConfig();
    if (singleBenchmarkResult.getSubSingleCount() > 1) {
      solverConfig = new SolverConfig(solverConfig);
      solverConfig.offerRandomSeedFromSubSingleIndex(
          subSingleBenchmarkResult.getSubSingleBenchmarkIndex());
    }
    var runId = UUID.randomUUID().toString();
    var subSingleBenchmarkSolverTags = SolverTags.withProblemId(runId);
    solverConfig = new SolverConfig(solverConfig);
    randomSeed = solverConfig.getRandomSeed();

    // Defensive copy of solverConfig for every SingleBenchmarkResult to reset Random, tabu lists,
    // ...
    var solverFactory = new DefaultSolverFactory<Solution_>(new SolverConfig(solverConfig));

    // Register metrics
    var statisticRegistry =
        new StatisticRegistry<Solution_>(
            solverFactory.getSolutionDescriptor().getScoreDefinition());
    var solver = (DefaultSolver<Solution_>) solverFactory.buildSolver();
    solver.setMonitorTags(subSingleBenchmarkSolverTags);
    var runTag = subSingleBenchmarkSolverTags.asTags();
    Solution_ solution;
    Metrics.addRegistry(statisticRegistry);
    try {
      subSingleBenchmarkResult
          .getEffectiveSubSingleStatisticMap()
          .forEach(
              (statisticType, statistic) -> {
                statistic.open(statisticRegistry, runTag);
                statistic.initPointList();
              });
      solver.addPhaseLifecycleListener(statisticRegistry);
      statisticRegistry.attach(solver.getSolverScope());
      solution = solver.solve(problem);
      for (var statistic : subSingleBenchmarkResult.getEffectiveSubSingleStatisticMap().values()) {
        statistic.close(statisticRegistry, runTag);
        statistic.hibernatePointList();
      }
    } finally {
      statisticRegistry.detach();
      solver.removePhaseLifecycleListener(statisticRegistry);
      Metrics.removeRegistry(statisticRegistry);
      statisticRegistry.close();
    }
    var timeMillisSpent = solver.getTimeMillisSpent();

    if (!warmUp) {
      var solverScope = solver.getSolverScope();
      var solutionDescriptor = solverScope.getSolutionDescriptor();
      problemBenchmarkResult.registerProblemSizeStatistics(solverScope.getProblemSizeStatistics());
      subSingleBenchmarkResult.setScore(
          solutionDescriptor.getScore(solution), solverScope.isBestSolutionInitialized());
      subSingleBenchmarkResult.setTimeMillisSpent(timeMillisSpent);
      subSingleBenchmarkResult.setScoreCalculationCount(
          solverScope.getReportedScoreCalculationCount());
      subSingleBenchmarkResult.setMoveEvaluationCount(solverScope.getReportedMoveEvaluationCount());

      var solutionManager = SolutionManager.create(solverFactory);
      var isConstraintMatchEnabled =
          solver.getSolverScope().getScoreDirector().getConstraintMatchPolicy().isEnabled();
      if (isConstraintMatchEnabled) { // Easy calculator fails otherwise.
        var scoreAnalysis =
            solutionManager.analyze(
                solution, ScoreAnalysisFetchPolicy.FETCH_ALL, SolutionUpdatePolicy.NO_UPDATE);
        subSingleBenchmarkResult.setScoreExplanationSummary(scoreAnalysis.summarize());
      }

      problemBenchmarkResult.writeSolution(subSingleBenchmarkResult, solution);
    }
    return this;
  }

  public String getName() {
    return subSingleBenchmarkResult.getName();
  }

  @Override
  public String toString() {
    return subSingleBenchmarkResult.toString();
  }
}
