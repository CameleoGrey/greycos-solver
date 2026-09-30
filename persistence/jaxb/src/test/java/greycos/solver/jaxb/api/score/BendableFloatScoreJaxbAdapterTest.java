package greycos.solver.jaxb.api.score;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import greycos.solver.core.api.score.BendableFloatScore;

import org.junit.jupiter.api.Test;

class BendableFloatScoreJaxbAdapterTest extends AbstractScoreJaxbAdapterTest {

  @Test
  void xmlPreservesFiniteValuesAndStructuralScore() {
    assertSerializeAndDeserialize(null, new TestScore(null));
    for (float value :
        new float[] {0.0f, -0.0f, 0.1f, Math.nextUp(1.0f), Float.MIN_VALUE, Float.MAX_VALUE}) {
      var score =
          new BendableFloatScore(
              -7L, new float[] {value, 0.1f}, new float[] {-value, Float.MIN_VALUE});
      assertSerializeAndDeserialize(score, new TestScore(score));
    }
  }

  @Test
  void rejectsNonFiniteLevels() {
    var adapter = new BendableFloatScoreJaxbAdapter();
    for (var token : new String[] {"NaN", "Infinity", "-Infinity", "1E99999"}) {
      var invalid = "[" + token + "]hard/[0.0]soft";
      assertThatThrownBy(() -> adapter.unmarshal(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasStackTraceContaining("finite");
    }
  }

  @XmlRootElement
  public static class TestScore extends TestScoreWrapper<BendableFloatScore> {

    @XmlJavaTypeAdapter(BendableFloatScoreJaxbAdapter.class)
    private BendableFloatScore score;

    private TestScore() {}

    TestScore(BendableFloatScore score) {
      this.score = score;
    }

    @Override
    public BendableFloatScore getScore() {
      return score;
    }
  }
}
