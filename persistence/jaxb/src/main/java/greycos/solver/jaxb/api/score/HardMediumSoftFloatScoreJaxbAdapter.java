package greycos.solver.jaxb.api.score;

import greycos.solver.core.api.score.HardMediumSoftFloatScore;

public class HardMediumSoftFloatScoreJaxbAdapter
    extends AbstractScoreJaxbAdapter<HardMediumSoftFloatScore> {

  @Override
  public HardMediumSoftFloatScore unmarshal(String scoreString) {
    return HardMediumSoftFloatScore.parseScore(scoreString);
  }
}
