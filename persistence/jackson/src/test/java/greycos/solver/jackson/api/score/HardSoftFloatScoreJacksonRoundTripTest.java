package greycos.solver.jackson.api.score;

import greycos.solver.core.api.score.HardSoftFloatScore;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

class HardSoftFloatScoreJacksonRoundTripTest extends AbstractScoreJacksonRoundTripTest {

  @Test
  void explicitAdaptersPreserveFiniteValuesAndStructuralScore() {
    assertSerializeAndDeserialize(null, new TestScore(null));
    for (float value :
        new float[] {0.0f, -0.0f, 0.1f, Math.nextUp(1.0f), Float.MIN_VALUE, Float.MAX_VALUE}) {
      var score = new HardSoftFloatScore(-7L, value, -value);
      assertSerializeAndDeserialize(score, new TestScore(score));
    }
  }

  public static class TestScore extends TestScoreWrapper<HardSoftFloatScore> {

    @JsonSerialize(using = HardSoftFloatScoreJacksonSerializer.class)
    @JsonDeserialize(using = HardSoftFloatScoreJacksonDeserializer.class)
    private HardSoftFloatScore score;

    private TestScore() {}

    TestScore(HardSoftFloatScore score) {
      this.score = score;
    }

    @Override
    public HardSoftFloatScore getScore() {
      return score;
    }
  }
}
