package greycos.solver.jaxb.api.score;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import greycos.solver.core.api.score.BendableDoubleScore;

import org.junit.jupiter.api.Test;

class BendableDoubleScoreJaxbAdapterTest extends AbstractScoreJaxbAdapterTest {

  @Test
  void xmlPreservesFiniteValuesAndStructuralScore() {
    assertSerializeAndDeserialize(null, new TestScore(null));
    for (double value :
        new double[] {0.0d, -0.0d, 0.1d, Math.nextUp(1.0d), Double.MIN_VALUE, Double.MAX_VALUE}) {
      var score =
          new BendableDoubleScore(
              -7L, new double[] {value, 0.1d}, new double[] {-value, Double.MIN_VALUE});
      assertSerializeAndDeserialize(score, new TestScore(score));
    }
  }

  @Test
  void rejectsNonFiniteLevels() {
    var adapter = new BendableDoubleScoreJaxbAdapter();
    for (var token : new String[] {"NaN", "Infinity", "-Infinity", "1E99999"}) {
      var invalid = "[" + token + "]hard/[0.0]soft";
      assertThatThrownBy(() -> adapter.unmarshal(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasStackTraceContaining("finite");
    }
  }

  @XmlRootElement
  public static class TestScore extends TestScoreWrapper<BendableDoubleScore> {

    @XmlJavaTypeAdapter(BendableDoubleScoreJaxbAdapter.class)
    private BendableDoubleScore score;

    private TestScore() {}

    TestScore(BendableDoubleScore score) {
      this.score = score;
    }

    @Override
    public BendableDoubleScore getScore() {
      return score;
    }
  }
}
