package greycos.solver.greybay.api;

import java.util.List;

import greycos.solver.core.api.score.Score;

/** Thread-safe snapshot containing only durably committed observations. */
public record StudyResult<Score_ extends Score<Score_>>(
    String studyId,
    StudyState state,
    TrainingStatus trainingStatus,
    List<TrialResult<Score_>> trials,
    TrialResult<Score_> incumbent,
    Long frozenSelectionTrialId,
    String activeEvaluationId,
    long measuredActiveNanos,
    long admissionChargedNanos,
    boolean interruptedCostUnknown,
    String stopReason) {
  public StudyResult {
    trials = List.copyOf(trials);
  }

  public boolean hasEligibleIncumbent() {
    return incumbent != null && incumbent.eligible();
  }
}
