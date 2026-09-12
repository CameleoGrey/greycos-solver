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

  public boolean isComplete() {
    return unassignedCount == 0;
  }

  @Override
  public int compareTo(AlnsEvaluation<Score_> other) {
    int initialization = Integer.compare(other.unassignedCount, unassignedCount);
    return initialization == 0 ? score.compareTo(other.score) : initialization;
  }
}
