package ai.greycos.solver.jsonb.api.score.buildin.simple;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.jsonb.api.score.AbstractScoreJsonbAdapter;

public class SimpleScoreJsonbAdapter extends AbstractScoreJsonbAdapter<SimpleScore> {

  @Override
  public SimpleScore adaptFromJson(String scoreString) {
    return SimpleScore.parseScore(scoreString);
  }
}
