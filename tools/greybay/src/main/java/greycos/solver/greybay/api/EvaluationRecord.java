package greycos.solver.greybay.api;

import java.util.Objects;

import greycos.solver.core.api.score.Score;
import greycos.solver.greybay.impl.objective.ScoreCopies;

/** A committed attempt. Missing scores and event timestamps are never synthesized as zero. */
public record EvaluationRecord<Score_ extends Score<Score_>>(
    String evaluationId,
    String stage,
    long trialId,
    String problemId,
    long solverSeed,
    long attempt,
    EvaluationStatus status,
    Score_ score,
    int missingRequiredAssignmentCount,
    long selectedObserverNanos,
    long selectedSolverMillis,
    long solveElapsedNanos,
    long preparationElapsedNanos,
    long assessmentElapsedNanos,
    VerificationResult verification,
    String failureType,
    String failureMessage) {
  public EvaluationRecord {
    Objects.requireNonNull(evaluationId, "evaluationId");
    Objects.requireNonNull(stage, "stage");
    Objects.requireNonNull(problemId, "problemId");
    Objects.requireNonNull(status, "status");
    if (score != null) score = ScoreCopies.copy(score);
  }

  @Override
  public Score_ score() {
    return score == null ? null : ScoreCopies.copy(score);
  }

  public boolean eligible() {
    return status == EvaluationStatus.COMPLETE;
  }
}
