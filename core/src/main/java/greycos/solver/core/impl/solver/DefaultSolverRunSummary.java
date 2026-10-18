package greycos.solver.core.impl.solver;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.SequencedMap;

import greycos.solver.core.api.solver.ProblemSizeStatistics;
import greycos.solver.core.api.solver.SolverRunSummary;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.util.MathUtils;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record DefaultSolverRunSummary(
    Duration solvingDuration,
    long scoreCalculationCount,
    long moveEvaluationCount,
    boolean isSolutionInitialized,
    ProblemSizeStatistics problemSizeStatistics)
    implements SolverRunSummary {

  public DefaultSolverRunSummary {
    var entityCounts =
        Collections.unmodifiableSequencedMap(
            new LinkedHashMap<>(problemSizeStatistics.genuineEntityClassToEntityCount()));
    var variableCounts = new LinkedHashMap<Class<?>, SequencedMap<String, Long>>();
    problemSizeStatistics
        .genuineEntityClassToVariableToValueCount()
        .forEach(
            (entityClass, counts) ->
                variableCounts.put(
                    entityClass,
                    Collections.unmodifiableSequencedMap(new LinkedHashMap<>(counts))));
    problemSizeStatistics =
        new ProblemSizeStatistics(
            problemSizeStatistics.entityCount(),
            entityCounts,
            problemSizeStatistics.variableCount(),
            problemSizeStatistics.approximateValueCount(),
            Collections.unmodifiableSequencedMap(variableCounts),
            problemSizeStatistics.approximateProblemSizeLog());
  }

  /** Call only after the run ended. */
  public static SolverRunSummary of(SolverScope<?> solverScope) {
    var solverStatistics = solverScope.getSolverStatistics();
    return new DefaultSolverRunSummary(
        Duration.ofMillis(solverStatistics.getTimeMillisSpent()),
        solverStatistics.getScoreCalculationCount(),
        solverStatistics.getMoveEvaluationCount(),
        solverScope.isBestSolutionInitialized(),
        solverScope.getProblemSizeStatistics());
  }

  @Override
  public long scoreCalculationSpeed() {
    return MathUtils.getSpeed(scoreCalculationCount, solvingDuration.toMillis());
  }

  @Override
  public long moveEvaluationSpeed() {
    return MathUtils.getSpeed(moveEvaluationCount, solvingDuration.toMillis());
  }
}
