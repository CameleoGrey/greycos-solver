package ai.greycos.solver.jackson.api.score.buildin;

import java.io.IOException;

import ai.greycos.solver.core.api.score.HardSoftScore;
import ai.greycos.solver.jackson.api.score.AbstractScoreJacksonDeserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

public class HardSoftScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<HardSoftScore> {

  @Override
  public HardSoftScore deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    return HardSoftScore.parseScore(parser.getValueAsString());
  }
}
