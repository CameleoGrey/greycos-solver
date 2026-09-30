package greycos.solver.quarkus.jackson.score;

import java.io.IOException;

import greycos.solver.core.api.score.SimpleFloatScore;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

public class SimpleFloatScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<SimpleFloatScore> {

  @Override
  public SimpleFloatScore deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    return SimpleFloatScore.parseScore(parser.getValueAsString());
  }
}
