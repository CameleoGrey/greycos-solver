package greycos.solver.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringWriter;

import greycos.solver.core.api.score.stream.test.ConstraintVerifier;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;
import greycos.solver.spring.boot.autoconfigure.nodesharing.TestdataSpringNodeSharingConstraintProvider;
import greycos.solver.spring.boot.autoconfigure.normal.cotwin.TestdataSpringEntity;
import greycos.solver.spring.boot.autoconfigure.normal.cotwin.TestdataSpringSolution;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NativeDetector;
import org.springframework.mock.env.MockEnvironment;

class GreyCOSSolverNodeSharingNativeTest {

  @Test
  void aotConfigSupplierRejectsEnabledNodeSharingInNativeImage() {
    var serializedConfig = serialize(solverConfig(true));
    try (var nativeDetectorMock = Mockito.mockStatic(NativeDetector.class)) {
      nativeDetectorMock.when(NativeDetector::inNativeImage).thenReturn(true);
      assertThatThrownBy(() -> new GreyCOSSolverAotFactory().solverConfigSupplier(serializedConfig))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContainingAll(
              "AOT runtime",
              TestdataSpringNodeSharingConstraintProvider.class.getName(),
              "constraintStreamAutomaticNodeSharing (true)",
              "unsupported",
              "native",
              "Maybe disable");
    }
  }

  @Test
  void aotManagerSupplierRejectsEnabledNodeSharingInNativeImage() {
    var serializedConfig = serialize(solverConfig(true));
    var factory = new GreyCOSSolverAotFactory();
    factory.setEnvironment(new MockEnvironment());
    try (var nativeDetectorMock = Mockito.mockStatic(NativeDetector.class)) {
      nativeDetectorMock.when(NativeDetector::inNativeImage).thenReturn(true);
      assertThatThrownBy(() -> factory.solverManagerSupplier(serializedConfig))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContainingAll("AOT runtime", "constraintStreamAutomaticNodeSharing (true)");
    }
  }

  @Test
  void aotConfigSupplierAllowsDisabledOrUnsetNodeSharingInNativeImage() {
    for (var sharing : new Boolean[] {false, null}) {
      var serializedConfig = serialize(solverConfig(sharing));
      try (var nativeDetectorMock = Mockito.mockStatic(NativeDetector.class)) {
        nativeDetectorMock.when(NativeDetector::inNativeImage).thenReturn(true);
        var restored = new GreyCOSSolverAotFactory().solverConfigSupplier(serializedConfig);
        assertThat(
                restored.getScoreDirectorFactoryConfig().getConstraintStreamAutomaticNodeSharing())
            .isEqualTo(sharing);
        assertThat(SolverFactory.create(restored).buildSolver()).isNotNull();
      }
    }
  }

  @Test
  void aotManagerSupplierAllowsDisabledOrUnsetNodeSharingInNativeImage() {
    for (var sharing : new Boolean[] {false, null}) {
      var serializedConfig = serialize(solverConfig(sharing));
      var factory = new GreyCOSSolverAotFactory();
      factory.setEnvironment(new MockEnvironment());
      try (var nativeDetectorMock = Mockito.mockStatic(NativeDetector.class)) {
        nativeDetectorMock.when(NativeDetector::inNativeImage).thenReturn(true);
        try (var manager = factory.solverManagerSupplier(serializedConfig)) {
          assertThat(manager).isNotNull();
        }
      }
    }
  }

  @Test
  void aotConfigSupplierAllowsMissingScoreDirectorFactoryInNativeImage() {
    var serializedConfig = serialize(new SolverConfig());
    try (var nativeDetectorMock = Mockito.mockStatic(NativeDetector.class)) {
      nativeDetectorMock.when(NativeDetector::inNativeImage).thenReturn(true);
      assertThat(
              new GreyCOSSolverAotFactory()
                  .solverConfigSupplier(serializedConfig)
                  .getScoreDirectorFactoryConfig())
          .isNull();
    }
  }

  @Test
  void customSolverConfigBeanRejectsEnabledNodeSharingInNativeImage() {
    try (var nativeDetectorMock = Mockito.mockStatic(NativeDetector.class)) {
      nativeDetectorMock.when(NativeDetector::inNativeImage).thenReturn(true);
      customSolverConfigContextRunner(true)
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                assertThatThrownBy(() -> context.getBean(SolverFactory.class))
                    .hasRootCauseInstanceOf(UnsupportedOperationException.class)
                    .rootCause()
                    .hasMessageContainingAll(
                        "runtime SolverConfig bean",
                        TestdataSpringNodeSharingConstraintProvider.class.getName(),
                        "constraintStreamAutomaticNodeSharing (true)",
                        "Maybe disable");
              });
    }
  }

  @Test
  void customSolverConfigVerifierRejectsEnabledNodeSharingInNativeImage() {
    try (var nativeDetectorMock = Mockito.mockStatic(NativeDetector.class)) {
      nativeDetectorMock.when(NativeDetector::inNativeImage).thenReturn(true);
      customSolverConfigContextRunner(true)
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                assertThatThrownBy(() -> context.getBean(ConstraintVerifier.class))
                    .hasRootCauseInstanceOf(UnsupportedOperationException.class)
                    .rootCause()
                    .hasMessageContainingAll(
                        "runtime SolverConfig bean", "constraintStreamAutomaticNodeSharing (true)");
              });
    }
  }

  @Test
  void customSolverConfigBeanAllowsDisabledOrUnsetNodeSharingInNativeImage() {
    for (var sharing : new Boolean[] {false, null}) {
      try (var nativeDetectorMock = Mockito.mockStatic(NativeDetector.class)) {
        nativeDetectorMock.when(NativeDetector::inNativeImage).thenReturn(true);
        customSolverConfigContextRunner(sharing)
            .run(
                context -> {
                  assertThat(context).hasNotFailed();
                  assertThat(context.getBean(SolverFactory.class).buildSolver()).isNotNull();
                });
      }
    }
  }

  private static ApplicationContextRunner customSolverConfigContextRunner(Boolean sharing) {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                GreyCOSSolverAutoConfiguration.class, GreyCOSSolverBeanFactory.class))
        .withBean(SolverConfig.class, () -> solverConfig(sharing));
  }

  private static SolverConfig solverConfig(Boolean sharing) {
    var scoreDirectorFactoryConfig =
        new ScoreDirectorFactoryConfig()
            .withConstraintProviderClass(TestdataSpringNodeSharingConstraintProvider.class);
    scoreDirectorFactoryConfig.setConstraintStreamAutomaticNodeSharing(sharing);
    return new SolverConfig()
        .withSolutionClass(TestdataSpringSolution.class)
        .withEntityClasses(TestdataSpringEntity.class)
        .withScoreDirectorFactory(scoreDirectorFactoryConfig);
  }

  private static String serialize(SolverConfig solverConfig) {
    var writer = new StringWriter();
    new SolverConfigIO().write(solverConfig, writer);
    return writer.toString();
  }
}
