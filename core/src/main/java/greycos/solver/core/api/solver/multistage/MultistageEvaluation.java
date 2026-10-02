package greycos.solver.core.api.solver.multistage;

import java.util.Objects;

import greycos.solver.core.api.score.Score;

/** An exact score and the number of unassigned mandatory decisions in the evaluated solution. */
public record MultistageEvaluation<Score_ extends Score<Score_>>(Score_ score, int unassignedCount)
    implements Comparable<MultistageEvaluation<Score_>> {

  public MultistageEvaluation {
    Objects.requireNonNull(score);
    if (unassignedCount < 0) {
      throw new IllegalArgumentException(
          "The unassignedCount (%d) must be >= 0.".formatted(unassignedCount));
    }
    if (score.structuralScore() > 0) {
      throw new IllegalArgumentException(
          "The structuralScore (%d) must be <= 0.".formatted(score.structuralScore()));
    }
  }

  /** Whether all mandatory decisions are assigned. Optional unassignment does not affect this. */
  public boolean isComplete() {
    return unassignedCount == 0;
  }

  /** Whether inconsistent shadow variables make this state unusable as a completed candidate. */
  public boolean isStructurallyFlawed() {
    return score.structuralScore() < 0;
  }

  /** Compares structural consistency, assignment completeness and then the exact score. */
  @Override
  public int compareTo(MultistageEvaluation<Score_> other) {
    int structuralComparison = Long.compare(score.structuralScore(), other.score.structuralScore());
    if (structuralComparison != 0) {
      return structuralComparison;
    }
    int assignmentComparison = Integer.compare(other.unassignedCount, unassignedCount);
    return assignmentComparison == 0 ? score.compareTo(other.score) : assignmentComparison;
  }
}
