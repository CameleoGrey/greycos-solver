package greycos.solver.jaxb.api.score;

import greycos.solver.core.api.score.HardMediumSoftDoubleScore;

public class HardMediumSoftDoubleScoreJaxbAdapter
    extends AbstractScoreJaxbAdapter<HardMediumSoftDoubleScore> {

  @Override
  public HardMediumSoftDoubleScore unmarshal(String scoreString) {
    return HardMediumSoftDoubleScore.parseScore(scoreString);
  }
}
