package greycos.solver.quarkus.jackson.score;

import java.io.IOException;

import greycos.solver.core.api.score.BendableFloatScore;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

public class BendableFloatScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<BendableFloatScore> {

  @Override
  public BendableFloatScore deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    return BendableFloatScore.parseScore(parser.getValueAsString());
  }
}
