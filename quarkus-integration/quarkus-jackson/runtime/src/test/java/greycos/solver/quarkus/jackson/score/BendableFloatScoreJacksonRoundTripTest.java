package greycos.solver.quarkus.jackson.score;

import greycos.solver.core.api.score.BendableFloatScore;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

class BendableFloatScoreJacksonRoundTripTest extends AbstractScoreJacksonRoundTripTest {

  @Test
  void explicitAdaptersPreserveFiniteValuesAndStructuralScore() {
    assertSerializeAndDeserialize(null, new TestScore(null));
    for (float value :
        new float[] {0.0f, -0.0f, 0.1f, Math.nextUp(1.0f), Float.MIN_VALUE, Float.MAX_VALUE}) {
      var score =
          new BendableFloatScore(
              -7L, new float[] {value, 0.1f}, new float[] {-value, Float.MIN_VALUE});
      assertSerializeAndDeserialize(score, new TestScore(score));
    }
  }

  public static class TestScore extends TestScoreWrapper<BendableFloatScore> {

    @JsonSerialize(using = BendableFloatScoreJacksonSerializer.class)
    @JsonDeserialize(using = BendableFloatScoreJacksonDeserializer.class)
    private BendableFloatScore score;

    private TestScore() {}

    TestScore(BendableFloatScore score) {
      this.score = score;
    }

    @Override
    public BendableFloatScore getScore() {
      return score;
    }
  }
}
