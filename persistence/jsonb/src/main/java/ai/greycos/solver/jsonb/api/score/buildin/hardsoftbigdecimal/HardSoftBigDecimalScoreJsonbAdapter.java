package ai.greycos.solver.jsonb.api.score.buildin.hardsoftbigdecimal;

import ai.greycos.solver.core.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScore;
import ai.greycos.solver.jsonb.api.score.AbstractScoreJsonbAdapter;

public class HardSoftBigDecimalScoreJsonbAdapter
    extends AbstractScoreJsonbAdapter<HardSoftBigDecimalScore> {

  @Override
  public HardSoftBigDecimalScore adaptFromJson(String scoreString) {
    return HardSoftBigDecimalScore.parseScore(scoreString);
  }
}
