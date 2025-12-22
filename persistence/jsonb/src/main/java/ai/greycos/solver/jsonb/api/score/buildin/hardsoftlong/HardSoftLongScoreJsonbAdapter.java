package ai.greycos.solver.jsonb.api.score.buildin.hardsoftlong;

import ai.greycos.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.greycos.solver.jsonb.api.score.AbstractScoreJsonbAdapter;

public class HardSoftLongScoreJsonbAdapter extends AbstractScoreJsonbAdapter<HardSoftLongScore> {

  @Override
  public HardSoftLongScore adaptFromJson(String scoreString) {
    return HardSoftLongScore.parseScore(scoreString);
  }
}
