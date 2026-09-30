package greycos.solver.jaxb.api.score;

import greycos.solver.core.api.score.HardSoftFloatScore;

public class HardSoftFloatScoreJaxbAdapter extends AbstractScoreJaxbAdapter<HardSoftFloatScore> {

  @Override
  public HardSoftFloatScore unmarshal(String scoreString) {
    return HardSoftFloatScore.parseScore(scoreString);
  }
}
