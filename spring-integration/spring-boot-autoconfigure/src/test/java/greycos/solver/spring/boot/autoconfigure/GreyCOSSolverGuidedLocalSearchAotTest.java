package greycos.solver.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;

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

  public abstract static class FeatureProvider
      implements GuidedLocalSearchFeatureProvider<Object, Object> {}
}
