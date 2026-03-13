package ai.greycos.solver.jaxb.api.score;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import ai.greycos.solver.core.api.score.HardSoftScore;

import org.junit.jupiter.api.Test;

class HardSoftScoreJaxbAdapterTest extends AbstractScoreJaxbAdapterTest {

  @Test
  void serializeAndDeserialize() {
    assertSerializeAndDeserialize(null, new TestHardSoftScoreWrapper(null));

    var score = HardSoftScore.of(1200, 34);
    assertSerializeAndDeserialize(score, new TestHardSoftScoreWrapper(score));
  }

  @XmlRootElement
  public static class TestHardSoftScoreWrapper extends TestScoreWrapper<HardSoftScore> {

    @XmlJavaTypeAdapter(HardSoftScoreJaxbAdapter.class)
    private HardSoftScore score;

    @SuppressWarnings("unused")
    private TestHardSoftScoreWrapper() {}

    public TestHardSoftScoreWrapper(HardSoftScore score) {
      this.score = score;
    }

    @Override
    public HardSoftScore getScore() {
      return score;
    }
  }
}
