package greycos.solver.greybay.api.objective;

import java.util.List;

import greycos.solver.core.api.score.Score;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.impl.objective.StandardExactScoreAdapter;

/**
 * Converts every native score level to its exact value, preserving native level order and meaning.
 * Custom {@link Number} types must supply this extension with a stable component identity.
 * Feasibility is always obtained from the original score, never inferred from these levels.
 */
public interface ExactScoreAdapter<Score_ extends Score<Score_>> {
  ComponentIdentity identity();

  List<ExactRational> levels(Score_ score);

  default ScoreLayout layout(Score_ score) {
    return ScoreLayout.of(score);
  }

  static <Score_ extends Score<Score_>> ExactScoreAdapter<Score_> standard() {
    return new StandardExactScoreAdapter<>();
  }
}
