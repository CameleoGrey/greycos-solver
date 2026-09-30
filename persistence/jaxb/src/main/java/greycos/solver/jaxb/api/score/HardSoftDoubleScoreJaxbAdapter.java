package greycos.solver.jaxb.api.score;

import greycos.solver.core.api.score.HardSoftDoubleScore;

public class HardSoftDoubleScoreJaxbAdapter extends AbstractScoreJaxbAdapter<HardSoftDoubleScore> {

  @Override
  public HardSoftDoubleScore unmarshal(String scoreString) {
    return HardSoftDoubleScore.parseScore(scoreString);
  }
}
