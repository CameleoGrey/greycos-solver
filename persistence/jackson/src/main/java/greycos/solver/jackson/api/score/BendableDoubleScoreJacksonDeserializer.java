package greycos.solver.jackson.api.score;

import greycos.solver.core.api.score.BendableDoubleScore;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;

public class BendableDoubleScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<BendableDoubleScore> {

  @Override
  public BendableDoubleScore deserialize(JsonParser parser, DeserializationContext context)
      throws JacksonException {
    return BendableDoubleScore.parseScore(parser.getValueAsString());
  }
}
