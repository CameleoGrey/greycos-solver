package ai.greycos.solver.jaxb.api.score.buildin.hardsoftbigdecimal;

import ai.greycos.solver.core.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScore;
import ai.greycos.solver.jaxb.api.score.AbstractScoreJaxbAdapter;

public class HardSoftBigDecimalScoreJaxbAdapter
    extends AbstractScoreJaxbAdapter<HardSoftBigDecimalScore> {

  @Override
  public HardSoftBigDecimalScore unmarshal(String scoreString) {
    return HardSoftBigDecimalScore.parseScore(scoreString);
  }
}
