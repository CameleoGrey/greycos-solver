package greycos.solver.jackson.api.score;

import greycos.solver.core.api.score.HardSoftDoubleScore;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

class HardSoftDoubleScoreJacksonRoundTripTest extends AbstractScoreJacksonRoundTripTest {

  @Test
  void explicitAdaptersPreserveFiniteValuesAndStructuralScore() {
    assertSerializeAndDeserialize(null, new TestScore(null));
    for (double value :
        new double[] {0.0d, -0.0d, 0.1d, Math.nextUp(1.0d), Double.MIN_VALUE, Double.MAX_VALUE}) {
      var score = new HardSoftDoubleScore(-7L, value, -value);
      assertSerializeAndDeserialize(score, new TestScore(score));
    }
  }

  public static class TestScore extends TestScoreWrapper<HardSoftDoubleScore> {

    @JsonSerialize(using = HardSoftDoubleScoreJacksonSerializer.class)
    @JsonDeserialize(using = HardSoftDoubleScoreJacksonDeserializer.class)
    private HardSoftDoubleScore score;

    private TestScore() {}

    TestScore(HardSoftDoubleScore score) {
      this.score = score;
    }

    @Override
    public HardSoftDoubleScore getScore() {
      return score;
    }
  }
}
