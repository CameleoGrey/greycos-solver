package ai.greycos.solver.jaxb.api.score.buildin.hardmediumsoft;

import ai.greycos.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.greycos.solver.jaxb.api.score.AbstractScoreJaxbAdapter;

public class HardMediumSoftScoreJaxbAdapter extends AbstractScoreJaxbAdapter<HardMediumSoftScore> {

  @Override
  public HardMediumSoftScore unmarshal(String scoreString) {
    return HardMediumSoftScore.parseScore(scoreString);
  }
}
