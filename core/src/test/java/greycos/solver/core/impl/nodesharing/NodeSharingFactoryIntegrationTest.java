package greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;

class NodeSharingFactoryIntegrationTest {

  @Test
  void cachedProviderClassKeepsCustomPropertiesLocalToEachFactory() {
    var restrictive = factory(5, true);
    var permissive = factory(2, true);
    var entity = new TestdataEntity("four", new TestdataValue("value"));

    assertThat(restrictive.fireAndForget(entity).extractScore()).isEqualTo(SimpleScore.of(-2));
    assertThat(permissive.fireAndForget(entity).extractScore()).isEqualTo(SimpleScore.of(-3));
    assertThat(restrictive.fireAndForget(entity).extractScore()).isEqualTo(SimpleScore.of(-2));
    assertThat(factory(5, false).fireAndForget(entity).extractScore())
        .isEqualTo(SimpleScore.of(-2));
    assertThat(factory(2, false).fireAndForget(entity).extractScore())
        .isEqualTo(SimpleScore.of(-3));
  }

  private static BavetConstraintStreamScoreDirectorFactory<TestdataSolution, SimpleScore> factory(
      int minLength, boolean sharing) {
    var config =
        new ScoreDirectorFactoryConfig()
            .withConstraintProviderClass(Provider.class)
            .withConstraintProviderCustomProperties(
                Map.of("minLength", Integer.toString(minLength)))
            .withConstraintStreamAutomaticNodeSharing(sharing);
    return BavetConstraintStreamScoreDirectorFactory.buildScoreDirectorFactory(
        TestdataSolution.buildSolutionDescriptor(), config, EnvironmentMode.NO_ASSERT);
  }

  public static class Provider implements ConstraintProvider {
    private int minLength;

    public void setMinLength(int minLength) {
      this.minLength = minLength;
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .filter(entity -> !entity.getCode().isEmpty())
            .penalize(SimpleScore.ONE)
            .asConstraint("first"),
        factory
            .forEach(TestdataEntity.class)
            .filter(entity -> !entity.getCode().isEmpty())
            .penalize(SimpleScore.ONE)
            .asConstraint("second"),
        factory
            .forEach(TestdataEntity.class)
            .filter(entity -> entity.getCode().length() > minLength)
            .penalize(SimpleScore.ONE)
            .asConstraint("configured")
      };
    }
  }
}
