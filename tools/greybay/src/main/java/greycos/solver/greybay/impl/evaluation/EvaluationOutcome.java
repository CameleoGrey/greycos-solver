package greycos.solver.greybay.impl.evaluation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import greycos.solver.core.api.score.Score;
import greycos.solver.greybay.api.EvaluationStatus;
import greycos.solver.greybay.api.VerificationResult;
import greycos.solver.greybay.impl.objective.ScoreCopies;

/** A finished physical attempt. No mutable solution graph crosses this boundary. */
public record EvaluationOutcome<Score_ extends Score<Score_>>(
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
    String failureMessage,
    List<String> suppressedFailures) {
  public EvaluationOutcome {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(verification, "verification");
    suppressedFailures = List.copyOf(suppressedFailures);
    if (score != null) score = ScoreCopies.copy(score);
    if (status == EvaluationStatus.COMPLETE
        && (score == null
            || missingRequiredAssignmentCount != 0
            || score.structuralScore() != 0
            || selectedObserverNanos < 0)) {
      throw new IllegalArgumentException(
          "A COMPLETE outcome requires an initialized, structurally sound on-time score.");
    }
  }

  public boolean eligible() {
    return status == EvaluationStatus.COMPLETE;
  }

  @Override
  public Score_ score() {
    return score == null ? null : ScoreCopies.copy(score);
  }

  public Map<String, Object> toMap() {
    var result = new LinkedHashMap<String, Object>();
    result.put("status", status.name());
    result.put("missingRequiredAssignmentCount", Integer.toString(missingRequiredAssignmentCount));
    result.put("selectedObserverNanos", Long.toString(selectedObserverNanos));
    result.put("selectedSolverMillis", Long.toString(selectedSolverMillis));
    result.put("solveElapsedNanos", Long.toString(solveElapsedNanos));
    result.put("preparationElapsedNanos", Long.toString(preparationElapsedNanos));
    result.put("assessmentElapsedNanos", Long.toString(assessmentElapsedNanos));
    result.put("failureType", failureType);
    result.put("failureMessage", failureMessage);
    result.put("suppressedFailures", suppressedFailures);
    result.put(
        "verification",
        Map.of(
            "status",
            verification.status().name(),
            "message",
            verification.message(),
            "metrics",
            verification.metrics()));
    if (score == null) {
      result.put("score", null);
    } else {
      var levels = new ArrayList<Map<String, String>>();
      for (Number number : score.toLevelNumbers()) {
        String value =
            number instanceof Double d
                ? Long.toHexString(Double.doubleToRawLongBits(d))
                : number instanceof Float f
                    ? Integer.toHexString(Float.floatToRawIntBits(f))
                    : number.toString();
        levels.add(Map.of("type", number.getClass().getName(), "value", value));
      }
      result.put(
          "score",
          Map.of(
              "type",
              score.getClass().getName(),
              "text",
              score.toString(),
              "structural",
              Long.toString(score.structuralScore()),
              "feasible",
              score.isFeasible(),
              "levels",
              levels));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  public static <Score_ extends Score<Score_>> EvaluationOutcome<Score_> fromMap(
      Map<String, Object> map, Function<String, Score_> scoreParser) {
    var scoreMap = (Map<String, Object>) map.get("score");
    Score_ score = scoreMap == null ? null : scoreParser.apply((String) scoreMap.get("text"));
    var verdict = (Map<String, Object>) map.get("verification");
    var outcome =
        new EvaluationOutcome<>(
            EvaluationStatus.valueOf((String) map.get("status")),
            score,
            Integer.parseInt((String) map.get("missingRequiredAssignmentCount")),
            number(map, "selectedObserverNanos"),
            number(map, "selectedSolverMillis"),
            number(map, "solveElapsedNanos"),
            number(map, "preparationElapsedNanos"),
            number(map, "assessmentElapsedNanos"),
            new VerificationResult(
                VerificationResult.Status.valueOf((String) verdict.get("status")),
                (String) verdict.get("message"),
                (Map<String, String>) verdict.get("metrics")),
            (String) map.get("failureType"),
            (String) map.get("failureMessage"),
            (List<String>) map.get("suppressedFailures"));
    if (!outcome.toMap().equals(map)) {
      throw new EvaluationMeasurementException(
          "The persisted evaluation encoding does not match its reconstructed score.");
    }
    return outcome;
  }

  private static long number(Map<String, Object> map, String key) {
    return Long.parseLong((String) map.get(key));
  }
}
