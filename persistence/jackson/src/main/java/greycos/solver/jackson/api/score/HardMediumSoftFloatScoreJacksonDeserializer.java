package greycos.solver.jackson.api.score;

import greycos.solver.core.api.score.HardMediumSoftFloatScore;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;

public class HardMediumSoftFloatScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<HardMediumSoftFloatScore> {

  @Override
  public HardMediumSoftFloatScore deserialize(JsonParser parser, DeserializationContext context)
      throws JacksonException {
    return HardMediumSoftFloatScore.parseScore(parser.getValueAsString());
  }
}
