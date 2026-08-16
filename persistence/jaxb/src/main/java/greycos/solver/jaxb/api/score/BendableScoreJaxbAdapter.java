package greycos.solver.jaxb.api.score;

import greycos.solver.core.api.score.BendableScore;

public class BendableScoreJaxbAdapter extends AbstractScoreJaxbAdapter<BendableScore> {

  @Override
  public BendableScore unmarshal(String scoreString) {
    return BendableScore.parseScore(scoreString);
  }
}
