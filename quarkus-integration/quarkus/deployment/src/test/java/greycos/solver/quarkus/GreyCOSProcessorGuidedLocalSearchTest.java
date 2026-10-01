package greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import greycos.solver.core.config.localsearch.GuidedLocalSearchFeatureComposition;
import greycos.solver.core.config.localsearch.GuidedLocalSearchGuidanceMode;
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
  void providerAndGuidanceConfigurationSurviveQuarkusAugmentation() {
    var phase = (LocalSearchPhaseConfig) solverConfig.getPhaseConfigList().getFirst();
    var guidance = phase.getGuidedLocalSearchConfig();
    assertThat(guidance.getFeatureProviderClass())
        .isEqualTo(TestdataQuarkusGuidedLocalSearchFeatureProvider.class);
    assertThat(guidance.getFeatureComposition())
        .isEqualTo(GuidedLocalSearchFeatureComposition.COMBINED);
    assertThat(guidance.getAutomaticListOwnershipEnabled()).isTrue();
    assertThat(guidance.getDirectedOriginSelection()).isTrue();
    assertThat(guidance.getPenaltyFactor()).isEqualByComparingTo("0.125");
    assertThat(guidance.getPenaltyFactor().scale()).isEqualTo(4);
    assertThat(guidance.getGuidanceMode()).isEqualTo(GuidedLocalSearchGuidanceMode.ALL_LEVELS);
    assertThat(guidance.getLevelScaleList()).hasSize(1);
    assertThat(guidance.getLevelScaleList().getFirst().getScoreLevelIndex()).isZero();
    assertThat(guidance.getLevelScaleList().getFirst().getScale()).isEqualByComparingTo("1E-30");
    assertThat(guidance.getLevelScaleList().getFirst().getScale().scale()).isEqualTo(30);
    assertThat(guidance.getFocusStepLimit()).isEqualTo(17);
    assertThat(guidance.getFocusPenaltyUpdateLimit()).isEqualTo(3);
    assertThat(guidance.getMaxPenaltyUpdatesPerStep()).isEqualTo(11);
    assertThat(guidance.getExcursionStepLimit()).isEqualTo(5);
    assertThat(guidance.getExcursionRepairStepLimit()).isEqualTo(29);
  }

  @Test
  void automaticFixedTargetAndOmittedSettingsSurviveQuarkusAugmentation() {
    var phase = (LocalSearchPhaseConfig) solverConfig.getPhaseConfigList().getLast();
    var guidance = phase.getGuidedLocalSearchConfig();
    assertThat(guidance.getFeatureComposition())
        .isEqualTo(GuidedLocalSearchFeatureComposition.AUTOMATIC);
    assertThat(guidance.getTargetScoreLevelIndex()).isZero();
    assertThat(guidance.getFeatureProviderClass()).isNull();
    assertThat(guidance.getGuidanceMode()).isNull();
    assertThat(guidance.getAutomaticListOwnershipEnabled()).isNull();
    assertThat(guidance.getDirectedOriginSelection()).isFalse();
    assertThat(guidance.getMaxPenaltyUpdatesPerStep()).isNull();
    assertThat(guidance.getExcursionStepLimit()).isNull();
    assertThat(guidance.getExcursionRepairStepLimit()).isNull();
  }
}
