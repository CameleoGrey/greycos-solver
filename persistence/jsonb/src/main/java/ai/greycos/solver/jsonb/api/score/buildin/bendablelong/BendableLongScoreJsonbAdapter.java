package ai.greycos.solver.jsonb.api.score.buildin.bendablelong;

import ai.greycos.solver.core.api.score.buildin.bendablelong.BendableLongScore;
import ai.greycos.solver.jsonb.api.score.AbstractScoreJsonbAdapter;

public class BendableLongScoreJsonbAdapter extends AbstractScoreJsonbAdapter<BendableLongScore> {

  @Override
  public BendableLongScore adaptFromJson(String scoreString) {
    return BendableLongScore.parseScore(scoreString);
  }
}
