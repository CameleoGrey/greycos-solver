package greycos.solver.jackson.api.score;

import greycos.solver.core.api.score.HardSoftFloatScore;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;

public class HardSoftFloatScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<HardSoftFloatScore> {

  @Override
  public HardSoftFloatScore deserialize(JsonParser parser, DeserializationContext context)
      throws JacksonException {
    return HardSoftFloatScore.parseScore(parser.getValueAsString());
  }
}
