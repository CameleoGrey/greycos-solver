package ai.greycos.solver.jackson.api.score;

import ai.greycos.solver.core.api.score.BendableScore;
import ai.greycos.solver.jackson.api.score.buildin.BendableScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.BendableScoreJacksonSerializer;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

class BendableScoreJacksonRoundTripTest extends AbstractScoreJacksonRoundTripTest {

  @Test
  void serializeAndDeserialize() {
    assertSerializeAndDeserialize(null, new TestBendableScoreWrapper(null));
    var score = BendableScore.of(new long[] {1000, 200}, new long[] {34});
    assertSerializeAndDeserialize(score, new TestBendableScoreWrapper(score));
  }

  public static class TestBendableScoreWrapper extends TestScoreWrapper<BendableScore> {

    @JsonSerialize(using = BendableScoreJacksonSerializer.class)
    @JsonDeserialize(using = BendableScoreJacksonDeserializer.class)
    private BendableScore score;

    @SuppressWarnings("unused")
    private TestBendableScoreWrapper() {}

    public TestBendableScoreWrapper(BendableScore score) {
      this.score = score;
    }

    @Override
    public BendableScore getScore() {
      return score;
    }
  }
}
