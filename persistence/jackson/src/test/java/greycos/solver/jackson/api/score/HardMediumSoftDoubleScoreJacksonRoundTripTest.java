package greycos.solver.jackson.api.score;

import greycos.solver.core.api.score.HardMediumSoftDoubleScore;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

class HardMediumSoftDoubleScoreJacksonRoundTripTest extends AbstractScoreJacksonRoundTripTest {

  @Test
  void explicitAdaptersPreserveFiniteValuesAndStructuralScore() {
    assertSerializeAndDeserialize(null, new TestScore(null));
    for (double value :
        new double[] {0.0d, -0.0d, 0.1d, Math.nextUp(1.0d), Double.MIN_VALUE, Double.MAX_VALUE}) {
      var score = new HardMediumSoftDoubleScore(-7L, value, 0.1d, -value);
      assertSerializeAndDeserialize(score, new TestScore(score));
    }
  }

  public static class TestScore extends TestScoreWrapper<HardMediumSoftDoubleScore> {

    @JsonSerialize(using = HardMediumSoftDoubleScoreJacksonSerializer.class)
    @JsonDeserialize(using = HardMediumSoftDoubleScoreJacksonDeserializer.class)
    private HardMediumSoftDoubleScore score;

    private TestScore() {}

    TestScore(HardMediumSoftDoubleScore score) {
      this.score = score;
    }

    @Override
    public HardMediumSoftDoubleScore getScore() {
      return score;
    }
  }
}
