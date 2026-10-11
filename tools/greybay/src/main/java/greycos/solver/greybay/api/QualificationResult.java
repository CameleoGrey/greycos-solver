package greycos.solver.greybay.api;

import java.util.List;

import greycos.solver.core.api.score.Score;
import greycos.solver.greybay.api.objective.ExactRational;
import greycos.solver.greybay.api.objective.ObjectiveValue;

/** Independent held-out evidence for an irrevocably frozen training selection. */
public record QualificationResult<Score_ extends Score<Score_>>(
    QualificationStatus status,
    QualificationComparison comparison,
    long selectedTrialId,
    ExactRational feasibilityRate,
    ObjectiveValue baselineObjective,
    ObjectiveValue selectedObjective,
    List<EvaluationRecord<Score_>> evaluations,
    String detail) {
  public QualificationResult {
    evaluations = List.copyOf(evaluations);
  }
}
