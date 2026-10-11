package greycos.solver.core.api.solver;

import java.util.Objects;

import greycos.solver.core.api.score.Score;

import org.jspecify.annotations.NullMarked;

/**
 * A recalculated score and the number of missing required assignments in the same solution state.
 * Allowed unassignment is not counted. Completeness and native structural soundness do not certify
 * application-specific business rules or the completeness of the input data.
 *
 * @param score the recalculated score
 * @param missingRequiredAssignmentCount the number of uninitialized required basic variables plus
 *     unassigned required list values
 * @param <Score_> the score type
 */
@NullMarked
public record SolutionAssessment<Score_ extends Score<Score_>>(
    Score_ score, int missingRequiredAssignmentCount) {

  public SolutionAssessment {
    Objects.requireNonNull(score, "score");
    if (missingRequiredAssignmentCount < 0) {
      throw new IllegalArgumentException(
          "The missingRequiredAssignmentCount (%d) must be >= 0."
              .formatted(missingRequiredAssignmentCount));
    }
  }

  public boolean isInitialized() {
    return missingRequiredAssignmentCount == 0;
  }

  /**
   * Whether the native structural score is zero. Legacy models using an inconsistency marker may
   * instead represent variable loops through application constraints.
   */
  public boolean isStructurallySound() {
    return score.structuralScore() == 0;
  }
}
