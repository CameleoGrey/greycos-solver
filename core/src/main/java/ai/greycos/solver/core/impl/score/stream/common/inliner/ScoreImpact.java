package ai.greycos.solver.core.impl.score.stream.common.inliner;

import ai.greycos.solver.core.api.score.Score;

public interface ScoreImpact<Score_ extends Score<Score_>> {

  void undo();

  Score_ toScore();
}
