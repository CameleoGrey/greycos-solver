package greycos.solver.jackson.api.score;

import greycos.solver.core.api.score.HardMediumSoftDoubleScore;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;

public class HardMediumSoftDoubleScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<HardMediumSoftDoubleScore> {

  @Override
  public HardMediumSoftDoubleScore deserialize(JsonParser parser, DeserializationContext context)
      throws JacksonException {
    return HardMediumSoftDoubleScore.parseScore(parser.getValueAsString());
  }
}
