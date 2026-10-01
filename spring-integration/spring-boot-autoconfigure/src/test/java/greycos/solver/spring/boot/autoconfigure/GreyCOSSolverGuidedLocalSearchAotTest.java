package greycos.solver.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchFeatureComposition;
import greycos.solver.core.config.localsearch.GuidedLocalSearchGuidanceMode;
import greycos.solver.core.config.localsearch.GuidedLocalSearchLevelScaleConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;

import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;

import tools.jackson.databind.json.JsonMapper;

class GreyCOSSolverGuidedLocalSearchAotTest {

  @Test
  void providerConstructorsAreRegisteredThroughLocalSearchAndIslandConfigurations() {
    var localSearch =
        new LocalSearchPhaseConfig()
            .withGuidedLocalSearchConfig(
                new GuidedLocalSearchConfig().withFeatureProviderClass(FeatureProvider.class));
    var configs =
        List.of(
            new SolverConfig().withPhases(localSearch),
            new SolverConfig()
                .withPhases(
                    new IslandModelPhaseConfig()
                        .withGuidedLocalSearchConfig(localSearch.getGuidedLocalSearchConfig())),
            new SolverConfig()
                .withPhases(
                    new IslandModelPhaseConfig().withPhaseConfigList(List.of(localSearch))));
    for (var config : configs) {
      var hints = new RuntimeHints();
      var generationContext = mock(GenerationContext.class);
      when(generationContext.getRuntimeHints()).thenReturn(hints);
      new GreyCOSSolverAotContribution(Map.of("solver", config)).applyTo(generationContext, null);

      var providerHint = hints.reflection().getTypeHint(FeatureProvider.class);
      assertThat(providerHint).isNotNull();
      assertThat(providerHint.getMemberCategories())
          .contains(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
    }
  }

  @Test
  void aotConfigurationRestoresGuidancePoliciesAndExactLevelScales() {
    var config =
        new SolverConfig()
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withGuidedLocalSearchConfig(
                        new GuidedLocalSearchConfig()
                            .withFeatureProviderClass(FeatureProvider.class)
                            .withFeatureComposition(GuidedLocalSearchFeatureComposition.COMBINED)
                            .withAutomaticListOwnershipEnabled(true)
                            .withDirectedOriginSelection(true)
                            .withGuidanceMode(GuidedLocalSearchGuidanceMode.ALL_LEVELS)
                            .withLevelScaleList(
                                List.of(
                                    new GuidedLocalSearchLevelScaleConfig()
                                        .withScoreLevelIndex(0)
                                        .withScale(new BigDecimal("0.125")),
                                    new GuidedLocalSearchLevelScaleConfig()
                                        .withScoreLevelIndex(2)
                                        .withScale(new BigDecimal("1E-30"))))
                            .withFocusStepLimit(17)
                            .withFocusPenaltyUpdateLimit(3)
                            .withMaxPenaltyUpdatesPerStep(11)
                            .withExcursionStepLimit(5)
                            .withExcursionRepairStepLimit(29)));
    var writer = new StringWriter();
    new SolverConfigIO().write(config, writer);
    var restored = new GreyCOSSolverAotFactory().solverConfigSupplier(writer.toString());
    var phase = (LocalSearchPhaseConfig) restored.getPhaseConfigList().getFirst();
    var guidance = phase.getGuidedLocalSearchConfig();
    assertThat(guidance.getFeatureProviderClass()).isEqualTo(FeatureProvider.class);
    assertThat(guidance.getFeatureComposition())
        .isEqualTo(GuidedLocalSearchFeatureComposition.COMBINED);
    assertThat(guidance.getAutomaticListOwnershipEnabled()).isTrue();
    assertThat(guidance.getDirectedOriginSelection()).isTrue();
    assertThat(guidance.getGuidanceMode()).isEqualTo(GuidedLocalSearchGuidanceMode.ALL_LEVELS);
    assertThat(guidance.getLevelScaleList()).hasSize(2);
    assertThat(guidance.getLevelScaleList().getFirst().getScoreLevelIndex()).isZero();
    assertThat(guidance.getLevelScaleList().getFirst().getScale()).isEqualByComparingTo("0.125");
    assertThat(guidance.getLevelScaleList().getLast().getScoreLevelIndex()).isEqualTo(2);
    assertThat(guidance.getLevelScaleList().getLast().getScale()).isEqualByComparingTo("1E-30");
    assertThat(guidance.getFocusStepLimit()).isEqualTo(17);
    assertThat(guidance.getFocusPenaltyUpdateLimit()).isEqualTo(3);
    assertThat(guidance.getMaxPenaltyUpdatesPerStep()).isEqualTo(11);
    assertThat(guidance.getExcursionStepLimit()).isEqualTo(5);
    assertThat(guidance.getExcursionRepairStepLimit()).isEqualTo(29);
  }

  @Test
  void aotConfigurationPreservesAutomaticFixedTargetAndOmittedSettings() {
    var config =
        new SolverConfig()
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withGuidedLocalSearchConfig(
                        new GuidedLocalSearchConfig()
                            .withFeatureComposition(GuidedLocalSearchFeatureComposition.AUTOMATIC)
                            .withTargetScoreLevelIndex(0)
                            .withDirectedOriginSelection(false)));
    var writer = new StringWriter();
    new SolverConfigIO().write(config, writer);
    var restored = new GreyCOSSolverAotFactory().solverConfigSupplier(writer.toString());
    var phase = (LocalSearchPhaseConfig) restored.getPhaseConfigList().getFirst();
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

  @Test
  void nativeReflectionMetadataIncludesGuidanceConfigurationAndComposition() throws Exception {
    try (var resource =
        getClass()
            .getResourceAsStream(
                "/META-INF/native-image/greycos.solver/greycos-solver-spring-boot-autoconfigure/reflect-config.json")) {
      assertThat(resource).isNotNull();
      var mapper = JsonMapper.builder().build();
      List<Map<String, Object>> registrations =
          mapper.readValue(
              resource, mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
      for (var type :
          List.of(GuidedLocalSearchConfig.class, GuidedLocalSearchFeatureComposition.class)) {
        assertThat(registrations)
            .anySatisfy(
                registration ->
                    assertThat(registration)
                        .containsEntry("name", type.getName())
                        .containsEntry("allDeclaredFields", true));
      }
    }
  }

  public abstract static class FeatureProvider
      implements GuidedLocalSearchFeatureProvider<Object, Object> {}
}
