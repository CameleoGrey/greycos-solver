package ai.greycos.solver.jaxb.api.score.buildin.hardsoft;

import ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.greycos.solver.jaxb.api.score.AbstractScoreJaxbAdapter;

public class HardSoftScoreJaxbAdapter extends AbstractScoreJaxbAdapter<HardSoftScore> {

  @Override
  public HardSoftScore unmarshal(String scoreString) {
    return HardSoftScore.parseScore(scoreString);
  }
}
