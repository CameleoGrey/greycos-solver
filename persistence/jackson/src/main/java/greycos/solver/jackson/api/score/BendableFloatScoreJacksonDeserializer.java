package greycos.solver.jackson.api.score;

import greycos.solver.core.api.score.BendableFloatScore;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;

public class BendableFloatScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<BendableFloatScore> {

  @Override
  public BendableFloatScore deserialize(JsonParser parser, DeserializationContext context)
      throws JacksonException {
    return BendableFloatScore.parseScore(parser.getValueAsString());
  }
}
