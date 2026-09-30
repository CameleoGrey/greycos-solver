package greycos.solver.jackson.api.score;

import greycos.solver.core.api.score.SimpleDoubleScore;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;

public class SimpleDoubleScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<SimpleDoubleScore> {

  @Override
  public SimpleDoubleScore deserialize(JsonParser parser, DeserializationContext context)
      throws JacksonException {
    return SimpleDoubleScore.parseScore(parser.getValueAsString());
  }
}
