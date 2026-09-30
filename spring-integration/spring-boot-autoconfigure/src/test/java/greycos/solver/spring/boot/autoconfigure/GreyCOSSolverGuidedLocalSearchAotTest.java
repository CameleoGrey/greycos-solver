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
import greycos.solver.core.config.localsearch.GuidedLocalSearchGuidanceMode;
import greycos.solver.core.config.localsearch.GuidedLocalSearchLevelScaleConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;

import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;

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
  void aotConfigurationRestoresGuidanceModeAndExactLevelScales() {
    var config =
        new SolverConfig()
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withGuidedLocalSearchConfig(
                        new GuidedLocalSearchConfig()
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
                            .withFocusPenaltyUpdateLimit(3)));
    var writer = new StringWriter();
    new SolverConfigIO().write(config, writer);
    var restored = new GreyCOSSolverAotFactory().solverConfigSupplier(writer.toString());
    var phase = (LocalSearchPhaseConfig) restored.getPhaseConfigList().getFirst();
    var guidance = phase.getGuidedLocalSearchConfig();
    assertThat(guidance.getGuidanceMode()).isEqualTo(GuidedLocalSearchGuidanceMode.ALL_LEVELS);
    assertThat(guidance.getLevelScaleList()).hasSize(2);
    assertThat(guidance.getLevelScaleList().getFirst().getScoreLevelIndex()).isZero();
    assertThat(guidance.getLevelScaleList().getFirst().getScale()).isEqualByComparingTo("0.125");
    assertThat(guidance.getLevelScaleList().getLast().getScoreLevelIndex()).isEqualTo(2);
    assertThat(guidance.getLevelScaleList().getLast().getScale()).isEqualByComparingTo("1E-30");
    assertThat(guidance.getFocusStepLimit()).isEqualTo(17);
    assertThat(guidance.getFocusPenaltyUpdateLimit()).isEqualTo(3);
  }

  public abstract static class FeatureProvider
      implements GuidedLocalSearchFeatureProvider<Object, Object> {}
}
