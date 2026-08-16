package greycos.solver.jaxb.api.score;

import greycos.solver.core.api.score.HardSoftBigDecimalScore;

public class HardSoftBigDecimalScoreJaxbAdapter
    extends AbstractScoreJaxbAdapter<HardSoftBigDecimalScore> {

  @Override
  public HardSoftBigDecimalScore unmarshal(String scoreString) {
    return HardSoftBigDecimalScore.parseScore(scoreString);
  }
}
