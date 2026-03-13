package ai.greycos.solver.jaxb.api.score;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import ai.greycos.solver.core.api.score.BendableScore;

import org.junit.jupiter.api.Test;

class BendableScoreJaxbAdapterTest extends AbstractScoreJaxbAdapterTest {

  @Test
  void serializeAndDeserialize() {
    assertSerializeAndDeserialize(null, new TestBendableScoreWrapper(null));

    var score = BendableScore.of(new long[] {1000, 200}, new long[] {34});
    assertSerializeAndDeserialize(score, new TestBendableScoreWrapper(score));
  }

  @XmlRootElement
  public static class TestBendableScoreWrapper extends TestScoreWrapper<BendableScore> {

    @XmlJavaTypeAdapter(BendableScoreJaxbAdapter.class)
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
