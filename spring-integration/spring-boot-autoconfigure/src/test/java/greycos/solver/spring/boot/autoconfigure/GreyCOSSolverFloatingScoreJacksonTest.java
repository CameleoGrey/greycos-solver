package greycos.solver.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

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

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.json.JsonMapper;

class GreyCOSSolverFloatingScoreJacksonTest {

  @Test
  void automaticallyProvidedModuleHandlesEveryFloatingPointScoreType() {
    new ApplicationContextRunner()
        .withUserConfiguration(GreyCOSSolverBeanFactory.GreyCOSJacksonConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var mapper =
                  JsonMapper.builder().addModule(context.getBean(JacksonModule.class)).build();
              for (var score : samples()) {
                var json = mapper.writeValueAsString(score);
                assertThat(mapper.readValue(json, score.getClass()))
                    .isExactlyInstanceOf(score.getClass())
                    .isEqualTo(score);
              }
            });
  }

  private static List<Score<?>> samples() {
    return List.of(
        new SimpleFloatScore(-7L, Float.MIN_VALUE),
        new SimpleDoubleScore(-7L, Double.MIN_VALUE),
        new HardSoftFloatScore(-7L, 0.1f, Float.MAX_VALUE),
        new HardSoftDoubleScore(-7L, 0.1, Double.MAX_VALUE),
        new HardMediumSoftFloatScore(-7L, 0.1f, Math.nextUp(1.0f), Float.MIN_VALUE),
        new HardMediumSoftDoubleScore(-7L, 0.1, Math.nextUp(1.0), Double.MIN_VALUE),
        new BendableFloatScore(-7L, new float[] {0.1f}, new float[] {Float.MIN_VALUE}),
        new BendableDoubleScore(-7L, new double[] {0.1}, new double[] {Double.MIN_VALUE}));
  }
}
