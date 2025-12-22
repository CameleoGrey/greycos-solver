package ai.greycos.solver.jaxb.api.score.buildin.bendablebigdecimal;

import ai.greycos.solver.core.api.score.buildin.bendablebigdecimal.BendableBigDecimalScore;
import ai.greycos.solver.jaxb.api.score.AbstractScoreJaxbAdapter;

public class BendableBigDecimalScoreJaxbAdapter
    extends AbstractScoreJaxbAdapter<BendableBigDecimalScore> {

  @Override
  public BendableBigDecimalScore unmarshal(String scoreString) {
    return BendableBigDecimalScore.parseScore(scoreString);
  }
}
