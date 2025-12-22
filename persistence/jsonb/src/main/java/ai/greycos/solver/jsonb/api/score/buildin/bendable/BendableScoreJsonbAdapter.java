package ai.greycos.solver.jsonb.api.score.buildin.bendable;

import ai.greycos.solver.core.api.score.buildin.bendable.BendableScore;
import ai.greycos.solver.jsonb.api.score.AbstractScoreJsonbAdapter;

public class BendableScoreJsonbAdapter extends AbstractScoreJsonbAdapter<BendableScore> {

  @Override
  public BendableScore adaptFromJson(String scoreString) {
    return BendableScore.parseScore(scoreString);
  }
}
