package greycos.solver.greybay.impl.objective;

import java.util.Arrays;
import java.util.List;

import greycos.solver.core.api.score.Score;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.objective.ExactRational;
import greycos.solver.greybay.api.objective.ExactScoreAdapter;

public final class StandardExactScoreAdapter<Score_ extends Score<Score_>>
    implements ExactScoreAdapter<Score_> {
  @Override
  public ComponentIdentity identity() {
    return new ComponentIdentity("greybay.exact-score", "1", "greycos-solver-greybay");
  }

  @Override
  public List<ExactRational> levels(Score_ score) {
    return Arrays.stream(score.toLevelNumbers()).map(ExactRational::fromNumber).toList();
  }
}
