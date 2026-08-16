package greycos.solver.jaxb.api.score;

import greycos.solver.core.api.score.HardSoftScore;

public class HardSoftScoreJaxbAdapter extends AbstractScoreJaxbAdapter<HardSoftScore> {

  @Override
  public HardSoftScore unmarshal(String scoreString) {
    return HardSoftScore.parseScore(scoreString);
  }
}
