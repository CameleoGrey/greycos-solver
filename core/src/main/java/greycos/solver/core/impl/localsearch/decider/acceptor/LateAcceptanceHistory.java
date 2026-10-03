package greycos.solver.core.impl.localsearch.decider.acceptor;

import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.score.definition.ScoreDefinition;
import greycos.solver.core.impl.score.director.InnerScore;

/** Effective late scores and the next history slot after the best-producing step. */
public record LateAcceptanceHistory(
    List<InnerScore<?>> scores,
    int nextIndex,
    Class<? extends Score<?>> scoreClass,
    int levelsSize,
    int feasibleLevelsSize)
    implements AcceptorMigrationState {

  public LateAcceptanceHistory {
    scores = List.copyOf(scores);
    Objects.requireNonNull(scoreClass);
    if (scores.isEmpty() || nextIndex < 0 || nextIndex >= scores.size()) {
      throw new IllegalArgumentException("Invalid late acceptance history size or position.");
    }
    if (levelsSize < 1 || feasibleLevelsSize < 0 || feasibleLevelsSize > levelsSize) {
      throw new IllegalArgumentException("Invalid late acceptance score dimensions.");
    }
    for (var score : scores) {
      if (score.raw().getClass() != scoreClass
          || score.raw().toLevelNumbers().length != levelsSize) {
        throw new IllegalArgumentException("Late acceptance history contains incompatible scores.");
      }
    }
  }

  public boolean isCompatible(ScoreDefinition<?> definition) {
    return scoreClass == definition.getScoreClass()
        && levelsSize == definition.getLevelsSize()
        && feasibleLevelsSize == definition.getFeasibleLevelsSize();
  }
}
