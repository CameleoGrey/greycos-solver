package greycos.solver.jaxb.api.score;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import greycos.solver.core.api.score.HardSoftFloatScore;

import org.junit.jupiter.api.Test;

class HardSoftFloatScoreJaxbAdapterTest extends AbstractScoreJaxbAdapterTest {

  @Test
  void xmlPreservesFiniteValuesAndStructuralScore() {
    assertSerializeAndDeserialize(null, new TestScore(null));
    for (float value :
        new float[] {0.0f, -0.0f, 0.1f, Math.nextUp(1.0f), Float.MIN_VALUE, Float.MAX_VALUE}) {
      var score = new HardSoftFloatScore(-7L, value, -value);
      assertSerializeAndDeserialize(score, new TestScore(score));
    }
  }

  @Test
  void rejectsNonFiniteLevels() {
    var adapter = new HardSoftFloatScoreJaxbAdapter();
    for (var token : new String[] {"NaN", "Infinity", "-Infinity", "1E99999"}) {
      var invalid = token + "hard/0.0soft";
      assertThatThrownBy(() -> adapter.unmarshal(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasStackTraceContaining("finite");
    }
  }

  @XmlRootElement
  public static class TestScore extends TestScoreWrapper<HardSoftFloatScore> {

    @XmlJavaTypeAdapter(HardSoftFloatScoreJaxbAdapter.class)
    private HardSoftFloatScore score;

    private TestScore() {}

    TestScore(HardSoftFloatScore score) {
      this.score = score;
    }

    @Override
    public HardSoftFloatScore getScore() {
      return score;
    }
  }
}
