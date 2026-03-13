package ai.greycos.solver.jackson.api.score.buildin;

import java.io.IOException;

import ai.greycos.solver.core.api.score.BendableScore;
import ai.greycos.solver.jackson.api.score.AbstractScoreJacksonDeserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

public class BendableScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<BendableScore> {

  @Override
  public BendableScore deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    return BendableScore.parseScore(parser.getValueAsString());
  }
}
