package ai.greycos.solver.jackson.api.score.buildin;

import java.io.IOException;

import ai.greycos.solver.core.api.score.BendableBigDecimalScore;
import ai.greycos.solver.jackson.api.score.AbstractScoreJacksonDeserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

public class BendableBigDecimalScoreJacksonDeserializer
    extends AbstractScoreJacksonDeserializer<BendableBigDecimalScore> {

  @Override
  public BendableBigDecimalScore deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    return BendableBigDecimalScore.parseScore(parser.getValueAsString());
  }
}
