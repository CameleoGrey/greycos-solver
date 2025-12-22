package ai.greycos.solver.jsonb.api.score.buildin.simplebigdecimal;

import ai.greycos.solver.core.api.score.buildin.simplebigdecimal.SimpleBigDecimalScore;
import ai.greycos.solver.jsonb.api.score.AbstractScoreJsonbAdapter;

public class SimpleBigDecimalScoreJsonbAdapter
    extends AbstractScoreJsonbAdapter<SimpleBigDecimalScore> {

  @Override
  public SimpleBigDecimalScore adaptFromJson(String scoreString) {
    return SimpleBigDecimalScore.parseScore(scoreString);
  }
}
