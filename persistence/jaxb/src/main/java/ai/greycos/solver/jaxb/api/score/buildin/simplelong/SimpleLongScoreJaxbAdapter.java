package ai.greycos.solver.jaxb.api.score.buildin.simplelong;

import ai.greycos.solver.core.api.score.buildin.simplelong.SimpleLongScore;
import ai.greycos.solver.jaxb.api.score.AbstractScoreJaxbAdapter;

public class SimpleLongScoreJaxbAdapter extends AbstractScoreJaxbAdapter<SimpleLongScore> {

  @Override
  public SimpleLongScore unmarshal(String scoreString) {
    return SimpleLongScore.parseScore(scoreString);
  }
}
