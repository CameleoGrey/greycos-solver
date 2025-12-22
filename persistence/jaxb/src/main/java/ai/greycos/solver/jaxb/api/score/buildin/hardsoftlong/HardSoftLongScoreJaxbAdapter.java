package ai.greycos.solver.jaxb.api.score.buildin.hardsoftlong;

import ai.greycos.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.greycos.solver.jaxb.api.score.AbstractScoreJaxbAdapter;

public class HardSoftLongScoreJaxbAdapter extends AbstractScoreJaxbAdapter<HardSoftLongScore> {

  @Override
  public HardSoftLongScore unmarshal(String scoreString) {
    return HardSoftLongScore.parseScore(scoreString);
  }
}
