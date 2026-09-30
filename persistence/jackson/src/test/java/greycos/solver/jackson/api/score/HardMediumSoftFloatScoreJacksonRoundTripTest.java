package greycos.solver.jackson.api.score;

import greycos.solver.core.api.score.HardMediumSoftFloatScore;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

class HardMediumSoftFloatScoreJacksonRoundTripTest extends AbstractScoreJacksonRoundTripTest {

  @Test
  void explicitAdaptersPreserveFiniteValuesAndStructuralScore() {
    assertSerializeAndDeserialize(null, new TestScore(null));
    for (float value :
        new float[] {0.0f, -0.0f, 0.1f, Math.nextUp(1.0f), Float.MIN_VALUE, Float.MAX_VALUE}) {
      var score = new HardMediumSoftFloatScore(-7L, value, 0.1f, -value);
      assertSerializeAndDeserialize(score, new TestScore(score));
    }
  }

  public static class TestScore extends TestScoreWrapper<HardMediumSoftFloatScore> {

    @JsonSerialize(using = HardMediumSoftFloatScoreJacksonSerializer.class)
    @JsonDeserialize(using = HardMediumSoftFloatScoreJacksonDeserializer.class)
    private HardMediumSoftFloatScore score;

    private TestScore() {}

    TestScore(HardMediumSoftFloatScore score) {
      this.score = score;
    }

    @Override
    public HardMediumSoftFloatScore getScore() {
      return score;
    }
  }
}
