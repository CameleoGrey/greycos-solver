package ai.greycos.solver.jsonb.api.score.buildin.hardmediumsoftlong;

import ai.greycos.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.greycos.solver.jsonb.api.score.AbstractScoreJsonbAdapter;

public class HardMediumSoftLongScoreJsonbAdapter
    extends AbstractScoreJsonbAdapter<HardMediumSoftLongScore> {

  @Override
  public HardMediumSoftLongScore adaptFromJson(String scoreString) {
    return HardMediumSoftLongScore.parseScore(scoreString);
  }
}
