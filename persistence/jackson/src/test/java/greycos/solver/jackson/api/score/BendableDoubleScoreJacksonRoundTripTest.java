package greycos.solver.jackson.api.score;

import greycos.solver.core.api.score.BendableDoubleScore;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

class BendableDoubleScoreJacksonRoundTripTest extends AbstractScoreJacksonRoundTripTest {

  @Test
  void explicitAdaptersPreserveFiniteValuesAndStructuralScore() {
    assertSerializeAndDeserialize(null, new TestScore(null));
    for (double value :
        new double[] {0.0d, -0.0d, 0.1d, Math.nextUp(1.0d), Double.MIN_VALUE, Double.MAX_VALUE}) {
      var score =
          new BendableDoubleScore(
              -7L, new double[] {value, 0.1d}, new double[] {-value, Double.MIN_VALUE});
      assertSerializeAndDeserialize(score, new TestScore(score));
    }
  }

  public static class TestScore extends TestScoreWrapper<BendableDoubleScore> {

    @JsonSerialize(using = BendableDoubleScoreJacksonSerializer.class)
    @JsonDeserialize(using = BendableDoubleScoreJacksonDeserializer.class)
    private BendableDoubleScore score;

    private TestScore() {}

    TestScore(BendableDoubleScore score) {
      this.score = score;
    }

    @Override
    public BendableDoubleScore getScore() {
      return score;
    }
  }
}
