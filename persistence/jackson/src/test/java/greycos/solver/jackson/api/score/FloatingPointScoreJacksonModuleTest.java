package greycos.solver.jackson.api.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.score.BendableDoubleScore;
import greycos.solver.core.api.score.BendableFloatScore;
import greycos.solver.core.api.score.HardMediumSoftDoubleScore;
import greycos.solver.core.api.score.HardMediumSoftFloatScore;
import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.core.api.score.HardSoftFloatScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.jackson.api.GreyCOSJacksonModule;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class FloatingPointScoreJacksonModuleTest {

  private final JsonMapper mapper =
      JsonMapper.builder().addModule(GreyCOSJacksonModule.createModule()).build();

  @Test
  void modulePreservesConcreteRootValues() throws Exception {
    for (var score : samples()) {
      var json = mapper.writeValueAsString(score);
      assertThat(json).isEqualTo("\"" + score + "\"");
      var restored = mapper.readValue(json, score.getClass());
      assertThat(restored).isExactlyInstanceOf(score.getClass()).isEqualTo(score);
    }
  }

  @Test
  void polymorphicRootsAndFollowingPropertiesRoundTrip() throws Exception {
    for (var score : samples()) {
      var rootJson = mapper.writerFor(Score.class).writeValueAsString(score);
      assertThat(mapper.readValue(rootJson, Score.class))
          .isExactlyInstanceOf(score.getClass())
          .isEqualTo(score);
      var wrapper = new PolymorphicWrapper();
      wrapper.score = score;
      wrapper.trailer = "after the score";
      var restored = mapper.readValue(mapper.writeValueAsString(wrapper), PolymorphicWrapper.class);
      assertThat(restored.score).isExactlyInstanceOf(score.getClass()).isEqualTo(score);
      assertThat(restored.trailer).isEqualTo(wrapper.trailer);
    }
  }

  @Test
  void concreteAndPolymorphicReadersRejectNonFiniteValues() {
    for (var scoreClass : samples().stream().map(Object::getClass).distinct().toList()) {
      for (var token : List.of("NaN", "Infinity", "-Infinity", "1E99999")) {
        var name = scoreClass.getSimpleName();
        var invalid =
            name.startsWith("Simple")
                ? token
                : name.startsWith("HardSoft")
                    ? token + "hard/0.0soft"
                    : name.startsWith("HardMediumSoft")
                        ? token + "hard/0.0medium/0.0soft"
                        : "[" + token + "]hard/[0.0]soft";
        assertThatThrownBy(() -> mapper.readValue("\"" + invalid + "\"", scoreClass))
            .hasStackTraceContaining("finite");
        assertThatThrownBy(
                () -> mapper.readValue("{\"" + name + "\":\"" + invalid + "\"}", Score.class))
            .hasStackTraceContaining("finite");
      }
    }
  }

  @Test
  void polymorphicReaderRejectsExtraScoreTypes() {
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    "{\"SimpleFloatScore\":\"0.1\",\"SimpleDoubleScore\":\"0.2\"}", Score.class))
        .hasStackTraceContaining("exactly one score type");
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

  public static class PolymorphicWrapper {
    public Score<?> score;
    public String trailer;
  }
}
