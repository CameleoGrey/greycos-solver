package greycos.solver.quarkus.jackson.score;

import java.io.IOException;

import greycos.solver.core.api.score.HardMediumSoftDoubleScore;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

public class HardMediumSoftDoubleScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<HardMediumSoftDoubleScore> {

  @Override
  public HardMediumSoftDoubleScore deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    return HardMediumSoftDoubleScore.parseScore(parser.getValueAsString());
  }
}
