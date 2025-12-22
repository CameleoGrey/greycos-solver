package ai.greycos.solver.jsonb.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import ai.greycos.solver.core.api.score.buildin.bendable.BendableScore;
import ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;

import org.junit.jupiter.api.Test;

class GreycosJsonbConfigTest extends AbstractJsonbJsonAdapterTest {

  @Test
  void jsonbConfigSerializeAndDeserialize() {
    JsonbConfig config = GreycosJsonbConfig.createConfig();
    Jsonb jsonb = JsonbBuilder.create(config);

    TestGreycosJsonbConfigWrapper input = new TestGreycosJsonbConfigWrapper();
    input.setBendableScore(BendableScore.of(new int[] {1000, 200}, new int[] {34}));
    input.setHardSoftScore(HardSoftScore.of(-1, -20));
    TestGreycosJsonbConfigWrapper output = serializeAndDeserialize(jsonb, input);
    assertThat(output.getBendableScore())
        .isEqualTo(BendableScore.of(new int[] {1000, 200}, new int[] {34}));
    assertThat(output.getHardSoftScore()).isEqualTo(HardSoftScore.of(-1, -20));
  }

  public static class TestGreycosJsonbConfigWrapper {

    private BendableScore bendableScore;
    private HardSoftScore hardSoftScore;

    // Empty constructor required by JSON-B
    @SuppressWarnings("unused")
    public TestGreycosJsonbConfigWrapper() {}

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
