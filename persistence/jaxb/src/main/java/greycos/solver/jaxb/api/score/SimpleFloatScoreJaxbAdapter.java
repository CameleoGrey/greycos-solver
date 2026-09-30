package greycos.solver.jaxb.api.score;

import greycos.solver.core.api.score.SimpleFloatScore;

public class SimpleFloatScoreJaxbAdapter extends AbstractScoreJaxbAdapter<SimpleFloatScore> {

  @Override
  public SimpleFloatScore unmarshal(String scoreString) {
    return SimpleFloatScore.parseScore(scoreString);
  }
}
