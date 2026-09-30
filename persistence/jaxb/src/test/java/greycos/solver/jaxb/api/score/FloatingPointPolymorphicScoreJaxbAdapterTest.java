package greycos.solver.jaxb.api.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import greycos.solver.core.api.score.BendableDoubleScore;
import greycos.solver.core.api.score.BendableFloatScore;
import greycos.solver.core.api.score.HardMediumSoftDoubleScore;
import greycos.solver.core.api.score.HardMediumSoftFloatScore;
import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.core.api.score.HardSoftFloatScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.impl.io.jaxb.GenericJaxbIO;

import org.junit.jupiter.api.Test;

class FloatingPointPolymorphicScoreJaxbAdapterTest {

  @Test
  void xmlPreservesConcreteTypeAndExactFiniteValues() {
    var io = new GenericJaxbIO<>(ScoreWrapper.class);
    for (var score : samples()) {
      var input = new ScoreWrapper();
      input.score = score;
      var writer = new StringWriter();
      io.write(input, writer);
      assertThat(writer.toString()).contains(score.getClass().getName(), score.toString());
      var restored = io.read(new StringReader(writer.toString()));
      assertThat(restored.score).isExactlyInstanceOf(score.getClass()).isEqualTo(score);
    }
  }

  @Test
  void rejectsNonFiniteValuesInPolymorphicXml() throws Exception {
    var unmarshaller = JAXBContext.newInstance(ScoreWrapper.class).createUnmarshaller();
    // JAXB permits callers to recover from adapter errors. This consumer requires valid scores.
    unmarshaller.setEventHandler(event -> false);
    for (var scoreClass : samples().stream().map(Object::getClass).distinct().toList()) {
      var name = scoreClass.getSimpleName();
      var invalid =
          name.startsWith("Simple")
              ? "NaN"
              : name.startsWith("HardSoft")
                  ? "NaNhard/0.0soft"
                  : name.startsWith("HardMediumSoft")
                      ? "NaNhard/0.0medium/0.0soft"
                      : "[NaN]hard/[0.0]soft";
      var xml =
          "<scoreWrapper><score class=\""
              + scoreClass.getName()
              + "\">"
              + invalid
              + "</score></scoreWrapper>";
      assertThatThrownBy(() -> unmarshaller.unmarshal(new StringReader(xml)))
          .hasStackTraceContaining("finite");
    }
  }

  private static List<Score<?>> samples() {
    var scores = new ArrayList<Score<?>>();
    for (float value :
        new float[] {0.0f, -0.0f, 0.1f, Math.nextUp(1.0f), Float.MIN_VALUE, Float.MAX_VALUE}) {
      scores.add(new SimpleFloatScore(-7L, value));
      scores.add(new HardSoftFloatScore(-7L, value, -value));
      scores.add(new HardMediumSoftFloatScore(-7L, value, 0.1f, -value));
      scores.add(
          new BendableFloatScore(
              -7L, new float[] {value, 0.1f}, new float[] {-value, Float.MIN_VALUE}));
    }
    for (double value :
        new double[] {0.0d, -0.0d, 0.1d, Math.nextUp(1.0d), Double.MIN_VALUE, Double.MAX_VALUE}) {
      scores.add(new SimpleDoubleScore(-7L, value));
      scores.add(new HardSoftDoubleScore(-7L, value, -value));
      scores.add(new HardMediumSoftDoubleScore(-7L, value, 0.1d, -value));
      scores.add(
          new BendableDoubleScore(
              -7L, new double[] {value, 0.1d}, new double[] {-value, Double.MIN_VALUE}));
    }
    return scores;
  }

  @XmlRootElement
  public static class ScoreWrapper {
    @XmlJavaTypeAdapter(PolymorphicScoreJaxbAdapter.class)
    public Score<?> score;
  }
}
