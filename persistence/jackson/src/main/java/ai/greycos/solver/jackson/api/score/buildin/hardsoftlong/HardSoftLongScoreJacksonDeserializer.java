package ai.greycos.solver.jackson.api.score.buildin.hardsoftlong;

import java.io.IOException;

import ai.greycos.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.greycos.solver.jackson.api.score.AbstractScoreJacksonDeserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

public class HardSoftLongScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<HardSoftLongScore> {

  @Override
  public HardSoftLongScore deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    return HardSoftLongScore.parseScore(parser.getValueAsString());
  }
}
