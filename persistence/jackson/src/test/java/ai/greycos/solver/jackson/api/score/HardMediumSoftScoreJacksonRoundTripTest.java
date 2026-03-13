package ai.greycos.solver.jackson.api.score;

import ai.greycos.solver.core.api.score.HardMediumSoftScore;
import ai.greycos.solver.jackson.api.score.buildin.HardMediumSoftScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.HardMediumSoftScoreJacksonSerializer;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

class HardMediumSoftScoreJacksonRoundTripTest extends AbstractScoreJacksonRoundTripTest {

  @Test
  void serializeAndDeserialize() {
    assertSerializeAndDeserialize(null, new TestHardMediumSoftScoreWrapper(null));
    var score = HardMediumSoftScore.of(1200, 30, 4);
    assertSerializeAndDeserialize(score, new TestHardMediumSoftScoreWrapper(score));
  }

  public static class TestHardMediumSoftScoreWrapper extends TestScoreWrapper<HardMediumSoftScore> {

    @JsonSerialize(using = HardMediumSoftScoreJacksonSerializer.class)
    @JsonDeserialize(using = HardMediumSoftScoreJacksonDeserializer.class)
    private HardMediumSoftScore score;

    @SuppressWarnings("unused")
    private TestHardMediumSoftScoreWrapper() {}

    public TestHardMediumSoftScoreWrapper(HardMediumSoftScore score) {
      this.score = score;
    }

    @Override
    public HardMediumSoftScore getScore() {
      return score;
    }
  }
}
