package greycos.solver.quarkus.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.quarkus.testcotwin.dummy.DummyTestdataQuarkusEasyScoreCalculator;
import greycos.solver.quarkus.testcotwin.dummy.DummyTestdataQuarkusIncrementalScoreCalculator;
import greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusConstraintProvider;

import org.jboss.jandex.IndexView;
import org.junit.jupiter.api.Test;

class GreyCOSProcessorTest {

  @Test
  void indexesCoreArtifactFromPublishedNamespace() {
    var indexDependency = new GreyCOSProcessor().indexDependencyBuildItem();

    assertThat(indexDependency.getGroupId()).isEqualTo("io.github.cameleogrey");
    assertThat(indexDependency.getArtifactId()).isEqualTo("greycos-solver-core");
  }

  @Test
  void explicitScoreStrategyPreventsDiscoveryOfOtherStrategies() {
    var indexView = mock(IndexView.class);
    var processor = new GreyCOSProcessor();
    for (var scoreDirectorFactoryConfig :
        List.of(
            new ScoreDirectorFactoryConfig()
                .withConstraintProviderClass(TestdataQuarkusConstraintProvider.class),
            new ScoreDirectorFactoryConfig()
                .withEasyScoreCalculatorClass(DummyTestdataQuarkusEasyScoreCalculator.class),
            new ScoreDirectorFactoryConfig()
                .withIncrementalScoreCalculatorClass(
                    DummyTestdataQuarkusIncrementalScoreCalculator.class))) {
      var solverConfig = new SolverConfig().withScoreDirectorFactory(scoreDirectorFactoryConfig);
      processor.applyScoreDirectorFactoryProperties(indexView, solverConfig);
      assertThat(solverConfig.getScoreDirectorFactoryConfig()).isSameAs(scoreDirectorFactoryConfig);
    }
    verifyNoInteractions(indexView);
  }
}
