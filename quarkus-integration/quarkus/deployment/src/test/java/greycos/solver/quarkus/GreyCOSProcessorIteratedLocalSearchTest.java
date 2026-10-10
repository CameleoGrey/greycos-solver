package greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchAcceptanceType;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.quarkus.testcotwin.iteratedlocalsearch.TestdataQuarkusIteratedLocalSearchFilters;
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

class GreyCOSProcessorIteratedLocalSearchTest {
  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey(
              "quarkus.greycos.solver-config-xml",
              "greycos/solver/quarkus/solverConfigWithIteratedLocalSearch.xml")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class,
                          TestdataQuarkusIteratedLocalSearchFilters.class,
                          TestdataQuarkusIteratedLocalSearchFilters.Inner.class,
                          TestdataQuarkusIteratedLocalSearchFilters.Perturbation.class)
                      .addAsResource(
                          "greycos/solver/quarkus/solverConfigWithIteratedLocalSearch.xml"))
          .addBuildChainCustomizer(
              builder -> {
                builder.addFinal(ArtifactResultBuildItem.class);
                builder
                    .addBuildStep(
                        context -> {
                          var registrations = context.consumeMulti(ReflectiveClassBuildItem.class);
                          for (var type :
                              List.of(
                                  TestdataQuarkusIteratedLocalSearchFilters.Inner.class,
                                  TestdataQuarkusIteratedLocalSearchFilters.Perturbation.class)) {
                            assertThat(registrations)
                                .anySatisfy(
                                    item -> {
                                      assertThat(item.getClassNames()).contains(type.getName());
                                      assertThat(item.isConstructors()).isTrue();
                                    });
                          }
                        })
                    .consumes(ReflectiveClassBuildItem.class)
                    .produces(ArtifactResultBuildItem.class)
                    .build();
              });

  @Inject SolverConfig solverConfig;

  @Test
  void topLevelAndIslandEpisodeConfigurationsSurviveAugmentation() {
    var phase = (IteratedLocalSearchPhaseConfig) solverConfig.getPhaseConfigList().get(1);
    assertThat(phase.getPerturbationStrengths()).containsExactly(1, 3, 7);
    assertThat(phase.getMoveThreadCount()).isEqualTo("NONE");
    assertThat(phase.getPerturbationAttemptLimit()).isEqualTo(32L);
    assertThat(phase.getEpisodeCandidateAttemptLimit()).isEqualTo(100L);
    assertThat(phase.getIterationCountLimit()).isEqualTo(4L);
    assertThat(phase.getAcceptanceType())
        .isEqualTo(IteratedLocalSearchAcceptanceType.IMPROVING_ONLY);
    assertThat(phase.getLocalSearchConfig().getTerminationConfig().getStepCountLimit())
        .isEqualTo(3);
    assertFilters(phase);
    var island = (IslandModelPhaseConfig) solverConfig.getPhaseConfigList().getLast();
    var child = (IteratedLocalSearchPhaseConfig) island.getPhaseConfigList().getFirst();
    assertThat(child.getPerturbationStrengths()).containsExactly(2);
    assertThat(child.getAcceptanceType()).isNull();
    assertThat(child.getMoveThreadCount()).isNull();
    assertFilters(child);
  }

  private static void assertFilters(IteratedLocalSearchPhaseConfig phase) {
    assertThat(phase.getLocalSearchConfig().getMoveSelectorConfig().getFilterClass())
        .isEqualTo(TestdataQuarkusIteratedLocalSearchFilters.Inner.class);
    assertThat(phase.getPerturbationMoveSelectorConfig().getFilterClass())
        .isEqualTo(TestdataQuarkusIteratedLocalSearchFilters.Perturbation.class);
  }
}
