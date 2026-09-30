package greycos.solver.quarkus.jackson.score;

import java.io.IOException;

import greycos.solver.core.api.score.HardMediumSoftFloatScore;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

public class HardMediumSoftFloatScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<HardMediumSoftFloatScore> {

  @Override
  public HardMediumSoftFloatScore deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    return HardMediumSoftFloatScore.parseScore(parser.getValueAsString());
  }
}
