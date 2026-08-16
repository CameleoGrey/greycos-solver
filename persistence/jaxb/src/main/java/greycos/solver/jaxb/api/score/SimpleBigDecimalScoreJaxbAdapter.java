package greycos.solver.jaxb.api.score;

import greycos.solver.core.api.score.SimpleBigDecimalScore;

public class SimpleBigDecimalScoreJaxbAdapter
    extends AbstractScoreJaxbAdapter<SimpleBigDecimalScore> {

  @Override
  public SimpleBigDecimalScore unmarshal(String scoreString) {
    return SimpleBigDecimalScore.parseScore(scoreString);
  }
}
