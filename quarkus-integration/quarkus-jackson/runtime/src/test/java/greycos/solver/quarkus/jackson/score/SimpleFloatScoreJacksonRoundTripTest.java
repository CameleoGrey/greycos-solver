package greycos.solver.quarkus.jackson.score;

import greycos.solver.core.api.score.SimpleFloatScore;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

class SimpleFloatScoreJacksonRoundTripTest extends AbstractScoreJacksonRoundTripTest {

  @Test
  void explicitAdaptersPreserveFiniteValuesAndStructuralScore() {
    assertSerializeAndDeserialize(null, new TestScore(null));
    for (float value :
        new float[] {0.0f, -0.0f, 0.1f, Math.nextUp(1.0f), Float.MIN_VALUE, Float.MAX_VALUE}) {
      var score = new SimpleFloatScore(-7L, value);
      assertSerializeAndDeserialize(score, new TestScore(score));
    }
  }

  public static class TestScore extends TestScoreWrapper<SimpleFloatScore> {

    @JsonSerialize(using = SimpleFloatScoreJacksonSerializer.class)
    @JsonDeserialize(using = SimpleFloatScoreJacksonDeserializer.class)
    private SimpleFloatScore score;

    private TestScore() {}

    TestScore(SimpleFloatScore score) {
      this.score = score;
    }

    @Override
    public SimpleFloatScore getScore() {
      return score;
    }
  }
}
