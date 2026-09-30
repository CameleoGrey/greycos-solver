package greycos.solver.quarkus.jackson.score;

import java.io.IOException;

import greycos.solver.core.api.score.SimpleDoubleScore;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

public class SimpleDoubleScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<SimpleDoubleScore> {

  @Override
  public SimpleDoubleScore deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    return SimpleDoubleScore.parseScore(parser.getValueAsString());
  }
}
