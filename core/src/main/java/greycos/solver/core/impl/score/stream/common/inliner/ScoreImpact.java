package greycos.solver.core.impl.score.stream.common.inliner;

import greycos.solver.core.api.score.Score;

public interface ScoreImpact<Score_ extends Score<Score_>> {

  void undo();

  Score_ toScore();
}
