package greycos.solver.jaxb.api.score;

import greycos.solver.core.api.score.BendableFloatScore;

public class BendableFloatScoreJaxbAdapter extends AbstractScoreJaxbAdapter<BendableFloatScore> {

  @Override
  public BendableFloatScore unmarshal(String scoreString) {
    return BendableFloatScore.parseScore(scoreString);
  }
}
