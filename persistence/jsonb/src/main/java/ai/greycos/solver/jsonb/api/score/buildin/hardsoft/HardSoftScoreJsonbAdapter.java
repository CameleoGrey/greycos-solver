package ai.greycos.solver.jsonb.api.score.buildin.hardsoft;

import ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.greycos.solver.jsonb.api.score.AbstractScoreJsonbAdapter;

public class HardSoftScoreJsonbAdapter extends AbstractScoreJsonbAdapter<HardSoftScore> {

  @Override
  public HardSoftScore adaptFromJson(String scoreString) {
    return HardSoftScore.parseScore(scoreString);
  }
}
