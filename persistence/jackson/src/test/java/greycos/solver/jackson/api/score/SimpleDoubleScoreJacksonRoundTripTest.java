package greycos.solver.jackson.api.score;

import greycos.solver.core.api.score.SimpleDoubleScore;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

class SimpleDoubleScoreJacksonRoundTripTest extends AbstractScoreJacksonRoundTripTest {

  @Test
  void explicitAdaptersPreserveFiniteValuesAndStructuralScore() {
    assertSerializeAndDeserialize(null, new TestScore(null));
    for (double value :
        new double[] {0.0d, -0.0d, 0.1d, Math.nextUp(1.0d), Double.MIN_VALUE, Double.MAX_VALUE}) {
      var score = new SimpleDoubleScore(-7L, value);
      assertSerializeAndDeserialize(score, new TestScore(score));
    }
  }

  public static class TestScore extends TestScoreWrapper<SimpleDoubleScore> {

    @JsonSerialize(using = SimpleDoubleScoreJacksonSerializer.class)
    @JsonDeserialize(using = SimpleDoubleScoreJacksonDeserializer.class)
    private SimpleDoubleScore score;

    private TestScore() {}

    TestScore(SimpleDoubleScore score) {
      this.score = score;
    }

    @Override
    public SimpleDoubleScore getScore() {
      return score;
    }
  }
}
