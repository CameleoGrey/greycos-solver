package greycos.solver.greybay.api;

import java.util.List;

import greycos.solver.core.api.score.Score;
import greycos.solver.greybay.api.objective.ObjectiveValue;
import greycos.solver.greybay.config.ParameterAssignment;

/**
 * Immutable trial snapshot; the calibration baseline has trial ID zero and no fabricated
 * assignment.
 */
public record TrialResult<Score_ extends Score<Score_>>(
    long trialId,
    String candidateIdentity,
    ParameterAssignment assignment,
    TrialStatus status,
    ObjectiveValue objective,
    List<EvaluationRecord<Score_>> evaluations,
    String failure) {
  public TrialResult {
    evaluations = List.copyOf(evaluations);
  }

  public boolean eligible() {
    return status == TrialStatus.COMPLETE && objective != null;
  }
}
