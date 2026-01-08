package ai.greycos.solver.jsonb.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import ai.greycos.solver.core.api.score.buildin.bendable.BendableScore;
import ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;

import org.junit.jupiter.api.Test;

class GreyCOSJsonbConfigTest extends AbstractJsonbJsonAdapterTest {

  @Test
  void jsonbConfigSerializeAndDeserialize() {
    JsonbConfig config = GreyCOSJsonbConfig.createConfig();
    Jsonb jsonb = JsonbBuilder.create(config);

    TestGreyCOSJsonbConfigWrapper input = new TestGreyCOSJsonbConfigWrapper();
    input.setBendableScore(BendableScore.of(new int[] {1000, 200}, new int[] {34}));
    input.setHardSoftScore(HardSoftScore.of(-1, -20));
    TestGreyCOSJsonbConfigWrapper output = serializeAndDeserialize(jsonb, input);
    assertThat(output.getBendableScore())
        .isEqualTo(BendableScore.of(new int[] {1000, 200}, new int[] {34}));
    assertThat(output.getHardSoftScore()).isEqualTo(HardSoftScore.of(-1, -20));
  }

  public static class TestGreyCOSJsonbConfigWrapper {

    private BendableScore bendableScore;
    private HardSoftScore hardSoftScore;

    // Empty constructor required by JSON-B
    @SuppressWarnings("unused")
    public TestGreyCOSJsonbConfigWrapper() {}

    public BendableScore getBendableScore() {
      return bendableScore;
    }

    public void setBendableScore(BendableScore bendableScore) {
      this.bendableScore = bendableScore;
    }

    public HardSoftScore getHardSoftScore() {
      return hardSoftScore;
    }

    public void setHardSoftScore(HardSoftScore hardSoftScore) {
      this.hardSoftScore = hardSoftScore;
    }
  }
}
