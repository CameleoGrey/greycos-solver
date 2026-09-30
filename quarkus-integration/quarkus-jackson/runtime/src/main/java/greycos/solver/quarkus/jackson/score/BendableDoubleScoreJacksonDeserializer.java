package greycos.solver.quarkus.jackson.score;

import java.io.IOException;

import greycos.solver.core.api.score.BendableDoubleScore;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

public class BendableDoubleScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<BendableDoubleScore> {

  @Override
  public BendableDoubleScore deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    return BendableDoubleScore.parseScore(parser.getValueAsString());
  }
}
