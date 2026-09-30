package greycos.solver.jaxb.api.score;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import greycos.solver.core.api.score.SimpleDoubleScore;

import org.junit.jupiter.api.Test;

class SimpleDoubleScoreJaxbAdapterTest extends AbstractScoreJaxbAdapterTest {

  @Test
  void xmlPreservesFiniteValuesAndStructuralScore() {
    assertSerializeAndDeserialize(null, new TestScore(null));
    for (double value :
        new double[] {0.0d, -0.0d, 0.1d, Math.nextUp(1.0d), Double.MIN_VALUE, Double.MAX_VALUE}) {
      var score = new SimpleDoubleScore(-7L, value);
      assertSerializeAndDeserialize(score, new TestScore(score));
    }
  }

  @Test
  void rejectsNonFiniteLevels() {
    var adapter = new SimpleDoubleScoreJaxbAdapter();
    for (var token : new String[] {"NaN", "Infinity", "-Infinity", "1E99999"}) {
      var invalid = token;
      assertThatThrownBy(() -> adapter.unmarshal(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasStackTraceContaining("finite");
    }
  }

  @XmlRootElement
  public static class TestScore extends TestScoreWrapper<SimpleDoubleScore> {

    @XmlJavaTypeAdapter(SimpleDoubleScoreJaxbAdapter.class)
    private SimpleDoubleScore score;

    private TestScore() {}

    TestScore(SimpleDoubleScore score) {
      this.score = score;
    }

    @Override
    public SimpleDoubleScore getScore() {
      return score;
    }
  }
}
