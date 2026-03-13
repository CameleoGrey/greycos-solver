package ai.greycos.solver.jackson.api.score.buildin;

import java.io.IOException;

import ai.greycos.solver.core.api.score.HardMediumSoftScore;
import ai.greycos.solver.jackson.api.score.AbstractScoreJacksonDeserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

public class HardMediumSoftScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<HardMediumSoftScore> {

  @Override
  public HardMediumSoftScore deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    return HardMediumSoftScore.parseScore(parser.getValueAsString());
  }
}
