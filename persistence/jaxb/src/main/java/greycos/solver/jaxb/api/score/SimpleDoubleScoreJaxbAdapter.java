package greycos.solver.jaxb.api.score;

import greycos.solver.core.api.score.SimpleDoubleScore;

public class SimpleDoubleScoreJaxbAdapter extends AbstractScoreJaxbAdapter<SimpleDoubleScore> {

  @Override
  public SimpleDoubleScore unmarshal(String scoreString) {
    return SimpleDoubleScore.parseScore(scoreString);
  }
}
