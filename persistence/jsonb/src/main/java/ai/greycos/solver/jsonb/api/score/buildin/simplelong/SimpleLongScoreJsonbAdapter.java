package ai.greycos.solver.jsonb.api.score.buildin.simplelong;

import ai.greycos.solver.core.api.score.buildin.simplelong.SimpleLongScore;
import ai.greycos.solver.jsonb.api.score.AbstractScoreJsonbAdapter;

public class SimpleLongScoreJsonbAdapter extends AbstractScoreJsonbAdapter<SimpleLongScore> {

  @Override
  public SimpleLongScore adaptFromJson(String scoreString) {
    return SimpleLongScore.parseScore(scoreString);
  }
}
