package greycos.solver.core.impl.solver.monitoring;

import java.util.Map;

import greycos.solver.core.impl.alns.AlnsStepScope;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmStepScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

/**
 * Creates immutable monitoring messages only after the owning thread finishes metric collection.
 */
public final class SolverMetricSamples {
  private SolverMetricSamples() {}

  public static SolverWorkSnapshot localWork(SolverScope<?> scope) {
    return new SolverWorkSnapshot(
        scope.getScoreCalculationCount(),
        scope.getMoveEvaluationCount(),
        scope.getMoveEvaluationCountPerType());
  }

  public static SolverWorkSnapshot reportedWork(SolverScope<?> scope) {
    return new SolverWorkSnapshot(
        scope.getReportedScoreCalculationCount(),
        scope.getReportedMoveEvaluationCount(),
        scope.getReportedMoveCountsByType());
  }

  public static SolverMetricSample captureStep(
      SolverScope<?> scope, AbstractStepScope<?> step, String source) {
    String moveType = null;
    if (step instanceof LocalSearchStepScope<?> localStep && localStep.getStep() != null) {
      moveType = localStep.getStep().describe();
    } else if (step instanceof AlnsStepScope<?> alnsStep) {
      moveType = alnsStep.getOperatorPairId();
    } else if (step instanceof GeneticAlgorithmStepScope<?> geneticStep) {
      moveType = geneticStep.getMoveTypeDescription();
    }
    return capture(
        scope,
        SolverMetricSample.Kind.STEP,
        source,
        moveType,
        Boolean.TRUE.equals(step.getBestScoreImproved()),
        step.getScore());
  }

  public static SolverMetricSample captureBest(SolverScope<?> scope, String source) {
    return capture(scope, SolverMetricSample.Kind.BEST, source, null, true, scope.getBestScore());
  }

  public static SolverMetricSample captureFinal(SolverScope<?> scope, String source) {
    return capture(scope, SolverMetricSample.Kind.FINAL, source, null, false, null);
  }

  private static SolverMetricSample capture(
      SolverScope<?> scope,
      SolverMetricSample.Kind kind,
      String source,
      String moveType,
      boolean improved,
      greycos.solver.core.impl.score.director.InnerScore<?> score) {
    var timeMillisSpent =
        kind == SolverMetricSample.Kind.BEST
            ? scope.getBestSolutionTimeMillisSpent()
            : scope.calculateTimeMillisSpentUpToNow();
    return new SolverMetricSample(
        kind,
        timeMillisSpent,
        source,
        scope.getMonitoringTags(),
        moveType,
        improved,
        score,
        "root".equals(source) ? reportedWork(scope) : localWork(scope),
        scope.hasMetricSampleListeners()
            ? SolverMetricSample.captureMeasurements(scope.getMonitoringTags())
            : Map.of());
  }

  /** Enqueue only after releasing the metric publication guard. */
  public static void publishIslandStep(SolverScope<?> scope, AbstractStepScope<?> step) {
    publishIslandStep(scope, step, 0L);
  }

  public static void publishIslandStep(
      SolverScope<?> scope, AbstractStepScope<?> step, long uncreditedCalculations) {
    if ("root".equals(scope.getMetricSource())) {
      return;
    }
    var holder = new SolverMetricSample[1];
    if (scope
        .getMetricRun()
        .publish(
            () -> {
              var sample = captureStep(scope, step, scope.getMetricSource());
              holder[0] =
                  sample.withWork(
                      sample
                          .work()
                          .plus(
                              new SolverWorkSnapshot(
                                  Math.max(0L, uncreditedCalculations), 0L, Map.of())));
            })) {
      scope.publishMetricSample(holder[0]);
    }
  }
}
