package greycos.solver.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.spring.boot.autoconfigure.nodesharing.NodeSharingSpringTestConfiguration;
import greycos.solver.spring.boot.autoconfigure.nodesharing.TestdataSpringNodeSharingConstraintProvider;
import greycos.solver.spring.boot.autoconfigure.normal.cotwin.TestdataSpringEntity;
import greycos.solver.spring.boot.autoconfigure.normal.cotwin.TestdataSpringSolution;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NativeDetector;

class GreyCOSSolverNodeSharingAutoConfigurationTest {

  @Test
  @SuppressWarnings("unchecked")
  void nodeSharingPreservesScoresAndOriginalProviderState() {
    for (boolean sharing : new boolean[] {false, true}) {
      var contextRunner = nodeSharingXmlContextRunner();
      if (!sharing) {
        contextRunner =
            contextRunner.withPropertyValues(
                "greycos.solver.constraint-stream-automatic-node-sharing=false");
      }
      contextRunner.run(
          context -> {
            assertThat(context).hasNotFailed();
            var config = context.getBean(SolverConfig.class);
            assertThat(
                    config
                        .getScoreDirectorFactoryConfig()
                        .getConstraintStreamAutomaticNodeSharing())
                .isEqualTo(sharing);
            assertThat(config.getScoreDirectorFactoryConfig().getConstraintProviderClass())
                .isEqualTo(TestdataSpringNodeSharingConstraintProvider.class);
            SolverFactory<TestdataSpringSolution> solverFactory =
                context.getBean(SolverFactory.class);
            assertThat(solverFactory.buildSolver()).isNotNull();
            SolutionManager<TestdataSpringSolution, SimpleScore> solutionManager =
                context.getBean(SolutionManager.class);

            var blocked = new TestdataSpringEntity();
            blocked.setValue("blocked");
            var allowed = new TestdataSpringEntity();
            allowed.setValue("allowed");
            var problem = new TestdataSpringSolution();
            problem.setValueList(List.of("blocked", "allowed"));
            problem.setEntityList(List.of(blocked, allowed));

            TestdataSpringNodeSharingConstraintProvider.blockedValue = "blocked";
            TestdataSpringNodeSharingConstraintProvider.predicateCalls = 0;
            assertThat(solutionManager.update(problem)).isEqualTo(SimpleScore.of(-3));
            assertThat(TestdataSpringNodeSharingConstraintProvider.predicateCalls)
                .isEqualTo(sharing ? 2 : 4);

            TestdataSpringNodeSharingConstraintProvider.blockedValue = "absent";
            TestdataSpringNodeSharingConstraintProvider.predicateCalls = 0;
            assertThat(solutionManager.update(problem)).isEqualTo(SimpleScore.ZERO);
            assertThat(TestdataSpringNodeSharingConstraintProvider.predicateCalls)
                .isEqualTo(sharing ? 2 : 4);
          });
    }
  }

  @Test
  void nativeImageRejectsNodeSharingEnabledOnlyInXml() {
    try (var nativeDetectorMock = Mockito.mockStatic(NativeDetector.class)) {
      nativeDetectorMock.when(NativeDetector::inNativeImage).thenReturn(true);
      nodeSharingXmlContextRunner()
          .run(
              context ->
                  assertThat(context.getStartupFailure())
                      .isInstanceOf(UnsupportedOperationException.class)
                      .hasMessageContainingAll("node sharing", "unsupported", "native", "disable"));
    }
  }

  @Test
  void nativeImageAllowsPropertyToDisableNodeSharingFromXml() {
    try (var nativeDetectorMock = Mockito.mockStatic(NativeDetector.class)) {
      nativeDetectorMock.when(NativeDetector::inNativeImage).thenReturn(true);
      nodeSharingXmlContextRunner()
          .withPropertyValues("greycos.solver.constraint-stream-automatic-node-sharing=false")
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                var solverConfig = context.getBean(SolverConfig.class);
                assertThat(
                        solverConfig
                            .getScoreDirectorFactoryConfig()
                            .getConstraintStreamAutomaticNodeSharing())
                    .isFalse();
                assertThat(context.getBean(SolverFactory.class).buildSolver()).isNotNull();
              });
    }
  }

  private static ApplicationContextRunner nodeSharingXmlContextRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                GreyCOSSolverAutoConfiguration.class, GreyCOSSolverBeanFactory.class))
        .withUserConfiguration(NodeSharingSpringTestConfiguration.class)
        .withPropertyValues(
            "greycos.solver-config-xml=greycos/solver/spring/boot/autoconfigure/solverConfigWithNodeSharing.xml");
  }
}
