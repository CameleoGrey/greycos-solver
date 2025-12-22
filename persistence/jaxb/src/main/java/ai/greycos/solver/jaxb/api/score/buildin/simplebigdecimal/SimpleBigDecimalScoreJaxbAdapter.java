package ai.greycos.solver.jaxb.api.score.buildin.simplebigdecimal;

import ai.greycos.solver.core.api.score.buildin.simplebigdecimal.SimpleBigDecimalScore;
import ai.greycos.solver.jaxb.api.score.AbstractScoreJaxbAdapter;

public class SimpleBigDecimalScoreJaxbAdapter
    extends AbstractScoreJaxbAdapter<SimpleBigDecimalScore> {

  @Override
  public SimpleBigDecimalScore unmarshal(String scoreString) {
    return SimpleBigDecimalScore.parseScore(scoreString);
  }
}
