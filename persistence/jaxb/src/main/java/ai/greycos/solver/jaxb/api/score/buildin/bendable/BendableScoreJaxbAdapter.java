package ai.greycos.solver.jaxb.api.score.buildin.bendable;

import ai.greycos.solver.core.api.score.buildin.bendable.BendableScore;
import ai.greycos.solver.jaxb.api.score.AbstractScoreJaxbAdapter;

public class BendableScoreJaxbAdapter extends AbstractScoreJaxbAdapter<BendableScore> {

  @Override
  public BendableScore unmarshal(String scoreString) {
    return BendableScore.parseScore(scoreString);
  }
}
