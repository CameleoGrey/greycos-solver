package greycos.solver.core.api.solver.alns;

import java.util.Objects;

import greycos.solver.core.api.score.Score;

import org.jspecify.annotations.Nullable;

/**
 * Immutable feedback after the resulting incumbent is stable. No mutable working objects escape.
 */
public record AlnsTrialResult<Score_ extends Score<Score_>>(
    long trialIndex,
    String destroyId,
    String repairId,
    AlnsOutcome outcome,
    Score_ beforeScore,
    @Nullable Score_ candidateScore,
    Score_ afterScore,
    Score_ bestBeforeScore,
    Score_ bestAfterScore,
    int destroyedCount,
    int recoveryCount,
    long probeCount,
    long elapsedNanos) {
  public AlnsTrialResult {
    Objects.requireNonNull(destroyId);
    Objects.requireNonNull(repairId);
    Objects.requireNonNull(outcome);
    Objects.requireNonNull(beforeScore);
    Objects.requireNonNull(afterScore);
    Objects.requireNonNull(bestBeforeScore);
    Objects.requireNonNull(bestAfterScore);
  }
}
