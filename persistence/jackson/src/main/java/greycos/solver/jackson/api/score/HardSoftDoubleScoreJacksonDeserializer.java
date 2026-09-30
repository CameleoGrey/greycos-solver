package greycos.solver.jackson.api.score;

import greycos.solver.core.api.score.HardSoftDoubleScore;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;

public class HardSoftDoubleScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<HardSoftDoubleScore> {

  @Override
  public HardSoftDoubleScore deserialize(JsonParser parser, DeserializationContext context)
      throws JacksonException {
    return HardSoftDoubleScore.parseScore(parser.getValueAsString());
  }
}
