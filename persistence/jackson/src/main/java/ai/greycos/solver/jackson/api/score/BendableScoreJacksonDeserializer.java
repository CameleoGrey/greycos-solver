package ai.greycos.solver.jackson.api.score;

import ai.greycos.solver.core.api.score.BendableScore;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;

public class BendableScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<BendableScore> {

  @Override
  public BendableScore deserialize(JsonParser parser, DeserializationContext context)
      throws JacksonException {
    return BendableScore.parseScore(parser.getValueAsString());
  }
}
