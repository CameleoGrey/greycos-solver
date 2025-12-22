package ai.greycos.solver.jaxb.api.score.buildin.hardmediumsoftbigdecimal;

import ai.greycos.solver.core.api.score.buildin.hardmediumsoftbigdecimal.HardMediumSoftBigDecimalScore;
import ai.greycos.solver.jaxb.api.score.AbstractScoreJaxbAdapter;

public class HardMediumSoftBigDecimalScoreJaxbAdapter
    extends AbstractScoreJaxbAdapter<HardMediumSoftBigDecimalScore> {

  @Override
  public HardMediumSoftBigDecimalScore unmarshal(String scoreString) {
    return HardMediumSoftBigDecimalScore.parseScore(scoreString);
  }
}
