package greycos.solver.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchAcceptanceType;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.io.jaxb.JaxbMoveSelectorConfigAdapter;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;

import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;

import tools.jackson.databind.json.JsonMapper;

class GreyCOSSolverIteratedLocalSearchAotTest {

  @Test
  void customClassesAndConfigurationSurviveTopLevelAndIslandAot() {
    var phase =
        new IteratedLocalSearchPhaseConfig()
            .withLocalSearch(
                new LocalSearchPhaseConfig()
                    .withMoveSelectorConfig(
                        new ChangeMoveSelectorConfig().withFilterClass(InnerFilter.class)))
            .withPerturbationMoveSelectorConfig(
                new SwapMoveSelectorConfig().withFilterClass(PerturbationFilter.class))
            .withPerturbationStrengths(1, 3, 7)
            .withPerturbationAttemptLimit(32)
            .withEpisodeCandidateAttemptLimit(100L)
            .withIterationCountLimit(4)
            .withAcceptanceType(IteratedLocalSearchAcceptanceType.IMPROVING_ONLY);
    for (var config :
        List.of(
            new SolverConfig().withPhases(phase),
            new SolverConfig()
                .withPhases(new IslandModelPhaseConfig().withPhaseConfigList(List.of(phase))))) {
      var hints = new RuntimeHints();
      var context = mock(GenerationContext.class);
      when(context.getRuntimeHints()).thenReturn(hints);
      new GreyCOSSolverAotContribution(Map.of("solver", config)).applyTo(context, null);
      for (var type : List.of(InnerFilter.class, PerturbationFilter.class)) {
        assertThat(hints.reflection().getTypeHint(type)).isNotNull();
        assertThat(hints.reflection().getTypeHint(type).getMemberCategories())
            .contains(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
      }
      var writer = new StringWriter();
      new SolverConfigIO().write(config, writer);
      var restored = new GreyCOSSolverAotFactory().solverConfigSupplier(writer.toString());
      assertThat(restored).usingRecursiveComparison().isEqualTo(config);
    }
  }

  @Test
  void nativeMetadataIncludesPhaseAcceptanceAndXmlAdapter() throws Exception {
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
          List.of(
              IteratedLocalSearchPhaseConfig.class,
              IteratedLocalSearchAcceptanceType.class,
              JaxbMoveSelectorConfigAdapter.class,
              JaxbMoveSelectorConfigAdapter.AdaptedMoveSelectorConfig.class)) {
        assertThat(registrations)
            .anySatisfy(
                registration ->
                    assertThat(registration)
                        .containsEntry("name", type.getName())
                        .containsEntry("allDeclaredFields", true));
      }
    }
  }

  public abstract static class InnerFilter implements SelectionFilter<Object, Object> {}

  public abstract static class PerturbationFilter implements SelectionFilter<Object, Object> {}
}
