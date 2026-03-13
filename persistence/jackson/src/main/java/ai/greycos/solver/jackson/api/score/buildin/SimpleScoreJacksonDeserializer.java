package ai.greycos.solver.jackson.api.score.buildin;

import java.io.IOException;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.jackson.api.score.AbstractScoreJacksonDeserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

public class SimpleScoreJacksonDeserializer extends AbstractScoreJacksonDeserializer<SimpleScore> {

  @Override
  public SimpleScore deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    return SimpleScore.parseScore(parser.getValueAsString());
  }
}
