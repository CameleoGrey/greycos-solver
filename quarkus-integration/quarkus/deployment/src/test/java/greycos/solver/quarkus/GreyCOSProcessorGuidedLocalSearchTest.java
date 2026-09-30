package greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.quarkus.testcotwin.guidedlocalsearch.TestdataQuarkusGuidedLocalSearchFeatureProvider;
import greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusConstraintProvider;
import greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusEntity;
import greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorGuidedLocalSearchTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey(
              "quarkus.greycos.solver-config-xml",
              "greycos/solver/quarkus/solverConfigWithGuidedLocalSearch.xml")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class,
                          TestdataQuarkusGuidedLocalSearchFeatureProvider.class,
                          TestdataQuarkusGuidedLocalSearchFeatureProvider.EmptySession.class)
                      .addAsResource(
                          "greycos/solver/quarkus/solverConfigWithGuidedLocalSearch.xml"))
          .addBuildChainCustomizer(
              builder -> {
                builder.addFinal(ArtifactResultBuildItem.class);
                builder
                    .addBuildStep(
                        context ->
                            assertThat(context.consumeMulti(ReflectiveClassBuildItem.class))
                                .anySatisfy(
                                    item -> {
                                      assertThat(item.getClassNames())
                                          .contains(
                                              TestdataQuarkusGuidedLocalSearchFeatureProvider.class
                                                  .getName());
                                      assertThat(item.isConstructors()).isTrue();
                                    }))
                    .consumes(ReflectiveClassBuildItem.class)
                    .produces(ArtifactResultBuildItem.class)
                    .build();
              });

  @Inject SolverConfig solverConfig;

  @Test
  void providerConfigurationSurvivesQuarkusAugmentation() {
    var phase = (LocalSearchPhaseConfig) solverConfig.getPhaseConfigList().getFirst();
    assertThat(phase.getGuidedLocalSearchConfig().getFeatureProviderClass())
        .isEqualTo(TestdataQuarkusGuidedLocalSearchFeatureProvider.class);
  }
}
