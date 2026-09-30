package greycos.solver.jaxb.api.score;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import greycos.solver.core.api.score.HardMediumSoftFloatScore;

import org.junit.jupiter.api.Test;

class HardMediumSoftFloatScoreJaxbAdapterTest extends AbstractScoreJaxbAdapterTest {

  @Test
  void xmlPreservesFiniteValuesAndStructuralScore() {
    assertSerializeAndDeserialize(null, new TestScore(null));
    for (float value :
        new float[] {0.0f, -0.0f, 0.1f, Math.nextUp(1.0f), Float.MIN_VALUE, Float.MAX_VALUE}) {
      var score = new HardMediumSoftFloatScore(-7L, value, 0.1f, -value);
      assertSerializeAndDeserialize(score, new TestScore(score));
    }
  }

  @Test
  void rejectsNonFiniteLevels() {
    var adapter = new HardMediumSoftFloatScoreJaxbAdapter();
    for (var token : new String[] {"NaN", "Infinity", "-Infinity", "1E99999"}) {
      var invalid = token + "hard/0.0medium/0.0soft";
      assertThatThrownBy(() -> adapter.unmarshal(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasStackTraceContaining("finite");
    }
  }

  @XmlRootElement
  public static class TestScore extends TestScoreWrapper<HardMediumSoftFloatScore> {

    @XmlJavaTypeAdapter(HardMediumSoftFloatScoreJaxbAdapter.class)
    private HardMediumSoftFloatScore score;

    private TestScore() {}

    TestScore(HardMediumSoftFloatScore score) {
      this.score = score;
    }

    @Override
    public HardMediumSoftFloatScore getScore() {
      return score;
    }
  }
}
