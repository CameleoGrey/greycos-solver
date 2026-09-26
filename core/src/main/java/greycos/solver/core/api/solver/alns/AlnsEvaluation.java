package greycos.solver.core.api.solver.alns;

import java.util.Objects;

import greycos.solver.core.api.score.Score;

/** An exact score together with the number of unassigned mandatory decisions. */
public record AlnsEvaluation<Score_ extends Score<Score_>>(Score_ score, int unassignedCount)
    implements Comparable<AlnsEvaluation<Score_>> {
  public AlnsEvaluation {
    Objects.requireNonNull(score);
    if (unassignedCount < 0) throw new IllegalArgumentException("Negative unassigned count.");
  }

  /**
   * Whether the score reports no unassigned mandatory decisions. A usable repair also requires
   * {@link #isStructurallyFlawed()} to return {@code false}.
   */
  public boolean isComplete() {
    return unassignedCount == 0;
  }

  /** Whether inconsistent shadow variables make this evaluation unusable as a repair candidate. */
  public boolean isStructurallyFlawed() {
    return score.structuralScore() < 0;
  }

  @Override
  public int compareTo(AlnsEvaluation<Score_> other) {
    int structural = Long.compare(score.structuralScore(), other.score.structuralScore());
    if (structural != 0) return structural;
    int initialization = Integer.compare(other.unassignedCount, unassignedCount);
    return initialization == 0 ? score.compareTo(other.score) : initialization;
  }
}
