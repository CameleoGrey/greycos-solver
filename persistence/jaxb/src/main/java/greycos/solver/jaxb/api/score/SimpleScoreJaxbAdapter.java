package greycos.solver.jaxb.api.score;

import greycos.solver.core.api.score.SimpleScore;

public class SimpleScoreJaxbAdapter extends AbstractScoreJaxbAdapter<SimpleScore> {

  @Override
  public SimpleScore unmarshal(String scoreString) {
    return SimpleScore.parseScore(scoreString);
  }
}
