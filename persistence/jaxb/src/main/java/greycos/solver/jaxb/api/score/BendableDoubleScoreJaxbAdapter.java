package greycos.solver.jaxb.api.score;

import greycos.solver.core.api.score.BendableDoubleScore;

public class BendableDoubleScoreJaxbAdapter extends AbstractScoreJaxbAdapter<BendableDoubleScore> {

  @Override
  public BendableDoubleScore unmarshal(String scoreString) {
    return BendableDoubleScore.parseScore(scoreString);
  }
}
