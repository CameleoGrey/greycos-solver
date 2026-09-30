package greycos.solver.jaxb.api.score;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import greycos.solver.core.api.score.HardMediumSoftDoubleScore;

import org.junit.jupiter.api.Test;

class HardMediumSoftDoubleScoreJaxbAdapterTest extends AbstractScoreJaxbAdapterTest {

  @Test
  void xmlPreservesFiniteValuesAndStructuralScore() {
    assertSerializeAndDeserialize(null, new TestScore(null));
    for (double value :
        new double[] {0.0d, -0.0d, 0.1d, Math.nextUp(1.0d), Double.MIN_VALUE, Double.MAX_VALUE}) {
      var score = new HardMediumSoftDoubleScore(-7L, value, 0.1d, -value);
      assertSerializeAndDeserialize(score, new TestScore(score));
    }
  }

  @Test
  void rejectsNonFiniteLevels() {
    var adapter = new HardMediumSoftDoubleScoreJaxbAdapter();
    for (var token : new String[] {"NaN", "Infinity", "-Infinity", "1E99999"}) {
      var invalid = token + "hard/0.0medium/0.0soft";
      assertThatThrownBy(() -> adapter.unmarshal(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasStackTraceContaining("finite");
    }
  }

  @XmlRootElement
  public static class TestScore extends TestScoreWrapper<HardMediumSoftDoubleScore> {

    @XmlJavaTypeAdapter(HardMediumSoftDoubleScoreJaxbAdapter.class)
    private HardMediumSoftDoubleScore score;

    private TestScore() {}

    TestScore(HardMediumSoftDoubleScore score) {
      this.score = score;
    }

    @Override
    public HardMediumSoftDoubleScore getScore() {
      return score;
    }
  }
}
