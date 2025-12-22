package ai.greycos.solver.jaxb.api.score.buildin.hardsoftlong;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import ai.greycos.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.greycos.solver.jaxb.api.score.AbstractScoreJaxbAdapterTest;

import org.junit.jupiter.api.Test;

class HardSoftLongScoreJaxbAdapterTest extends AbstractScoreJaxbAdapterTest {

  @Test
  void serializeAndDeserialize() {
    assertSerializeAndDeserialize(null, new TestHardSoftLongScoreWrapper(null));

    var score = HardSoftLongScore.of(1200L, 34L);
    assertSerializeAndDeserialize(score, new TestHardSoftLongScoreWrapper(score));
  }

  @XmlRootElement
  public static class TestHardSoftLongScoreWrapper extends TestScoreWrapper<HardSoftLongScore> {

    @XmlJavaTypeAdapter(HardSoftLongScoreJaxbAdapter.class)
    private HardSoftLongScore score;

    @SuppressWarnings("unused")
    private TestHardSoftLongScoreWrapper() {}

    public TestHardSoftLongScoreWrapper(HardSoftLongScore score) {
      this.score = score;
    }

    @Override
    public HardSoftLongScore getScore() {
      return score;
    }
  }
}
