package ai.greycos.solver.jaxb.api.score.buildin.hardmediumsoftlong;

import ai.greycos.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.greycos.solver.jaxb.api.score.AbstractScoreJaxbAdapter;

public class HardMediumSoftLongScoreJaxbAdapter
    extends AbstractScoreJaxbAdapter<HardMediumSoftLongScore> {

  @Override
  public HardMediumSoftLongScore unmarshal(String scoreString) {
    return HardMediumSoftLongScore.parseScore(scoreString);
  }
}
