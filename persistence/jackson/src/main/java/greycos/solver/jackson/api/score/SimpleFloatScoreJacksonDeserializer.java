package greycos.solver.jackson.api.score;

import greycos.solver.core.api.score.SimpleFloatScore;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;

public class SimpleFloatScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<SimpleFloatScore> {

  @Override
  public SimpleFloatScore deserialize(JsonParser parser, DeserializationContext context)
      throws JacksonException {
    return SimpleFloatScore.parseScore(parser.getValueAsString());
  }
}
