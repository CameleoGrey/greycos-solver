package ai.greycos.solver.jsonb.api.score.buildin.hardmediumsoftbigdecimal;

import ai.greycos.solver.core.api.score.buildin.hardmediumsoftbigdecimal.HardMediumSoftBigDecimalScore;
import ai.greycos.solver.jsonb.api.score.AbstractScoreJsonbAdapter;

public class HardMediumSoftBigDecimalScoreJsonbAdapter
    extends AbstractScoreJsonbAdapter<HardMediumSoftBigDecimalScore> {

  @Override
  public HardMediumSoftBigDecimalScore adaptFromJson(String scoreString) {
    return HardMediumSoftBigDecimalScore.parseScore(scoreString);
  }
}
