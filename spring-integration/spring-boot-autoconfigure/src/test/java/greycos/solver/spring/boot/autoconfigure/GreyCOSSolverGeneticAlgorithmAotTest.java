package greycos.solver.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class GreyCOSSolverGeneticAlgorithmAotTest {

  @Test
  void aotConfigurationRestoresExplicitSettingsAndEveryMutationType() {
    var phase =
        new GeneticAlgorithmPhaseConfig()
            .withPopulationSize(37)
            .withCrossoverProbability(0.75)
            .withPBestRate(0.25)
            .withMutationRateMultiplier(2.5)
            .withTabuEntityRate(0.1)
            .withNoProgressAttemptLimit(99L)
            .withMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_NONE)
            .withMutationOperators(
                operator(GeneticAlgorithmMutationType.CHANGE, 0.05),
                operator(GeneticAlgorithmMutationType.SWAP, 0.1),
                operator(GeneticAlgorithmMutationType.SWAP_EDGES, 0.15),
                operator(GeneticAlgorithmMutationType.SCRAMBLE, 0.2),
                operator(GeneticAlgorithmMutationType.INSERTION, 0.25),
                operator(GeneticAlgorithmMutationType.INVERSE, 0.25))
            .inherit(
                new GeneticAlgorithmPhaseConfig()
                    .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(23)));
    var config = new SolverConfig().withPhases(phase);

    var restored = restore(config);

    assertThat(restored.getPhaseConfigList()).hasSize(1);
    var restoredPhase = (GeneticAlgorithmPhaseConfig) restored.getPhaseConfigList().getFirst();
    assertThat(restoredPhase.getEnvironmentMode()).isEqualTo(EnvironmentMode.FULL_ASSERT);
    assertThat(restoredPhase.getTerminationConfig().getStepCountLimit()).isEqualTo(23);
    assertThat(restoredPhase.getPopulationSize()).isEqualTo(37);
    assertThat(restoredPhase.getCrossoverProbability()).isEqualTo(0.75);
    assertThat(restoredPhase.getPBestRate()).isEqualTo(0.25);
    assertThat(restoredPhase.getMutationRateMultiplier()).isEqualTo(2.5);
    assertThat(restoredPhase.getTabuEntityRate()).isEqualTo(0.1);
    assertThat(restoredPhase.getNoProgressAttemptLimit()).isEqualTo(99L);
    assertThat(restoredPhase.getMoveThreadCount()).isEqualTo(SolverConfig.MOVE_THREAD_COUNT_NONE);
    assertThat(restoredPhase.getMutationOperatorConfigList())
        .extracting(
            GeneticAlgorithmMutationOperatorConfig::getType,
            GeneticAlgorithmMutationOperatorConfig::getProbability)
        .containsExactly(
            tuple(GeneticAlgorithmMutationType.CHANGE, 0.05),
            tuple(GeneticAlgorithmMutationType.SWAP, 0.1),
            tuple(GeneticAlgorithmMutationType.SWAP_EDGES, 0.15),
            tuple(GeneticAlgorithmMutationType.SCRAMBLE, 0.2),
            tuple(GeneticAlgorithmMutationType.INSERTION, 0.25),
            tuple(GeneticAlgorithmMutationType.INVERSE, 0.25));
    assertThat(restoredPhase.resolve()).usingRecursiveComparison().isEqualTo(phase.resolve());
  }

  @Test
  void aotConfigurationPreservesOmittedSettingsAndTheirResolvedDefaults() {
    var config = new SolverConfig().withPhases(new GeneticAlgorithmPhaseConfig());

    var restored = restore(config);

    assertThat(restored.getPhaseConfigList()).hasSize(1);
    var phase = (GeneticAlgorithmPhaseConfig) restored.getPhaseConfigList().getFirst();
    assertThat(phase).usingRecursiveComparison().isEqualTo(new GeneticAlgorithmPhaseConfig());
    var resolved = phase.resolve();
    assertThat(resolved.getPopulationSize()).isEqualTo(128);
    assertThat(resolved.getCrossoverProbability()).isEqualTo(0.5);
    assertThat(resolved.getPBestRate()).isEqualTo(0.05);
    assertThat(resolved.getMutationRateMultiplier()).isZero();
    assertThat(resolved.getTabuEntityRate()).isZero();
    assertThat(resolved.getNoProgressAttemptLimit()).isEqualTo(1280L);
    assertThat(resolved.getMoveThreadCount()).isNull();
    assertThat(resolved.getEnvironmentMode()).isNull();
    assertThat(resolved.getTerminationConfig()).isNull();
    assertThat(resolved.getMutationOperatorConfigList())
        .extracting(GeneticAlgorithmMutationOperatorConfig::getType)
        .containsExactly(GeneticAlgorithmMutationType.values());
    assertThat(resolved.getMutationOperatorConfigList())
        .allSatisfy(operator -> assertThat(operator.getProbability()).isEqualTo(1.0 / 6.0));
    assertThat(phase).usingRecursiveComparison().isEqualTo(new GeneticAlgorithmPhaseConfig());
  }

  @Test
  void aotConfigurationWithoutGeneticAlgorithmStillRoundTrips() {
    var config =
        new SolverConfig()
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withLocalSearchType(LocalSearchType.LATE_ACCEPTANCE)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(17)));

    var restored = restore(config);

    assertThat(restored).usingRecursiveComparison().isEqualTo(config);
    assertThat(restored.getPhaseConfigList())
        .singleElement()
        .isInstanceOf(LocalSearchPhaseConfig.class);
  }

  @Test
  void nativeReflectionMetadataIncludesEveryGeneticAlgorithmConfigurationType() throws Exception {
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
              GeneticAlgorithmPhaseConfig.class, GeneticAlgorithmMutationOperatorConfig.class)) {
        assertThat(registrations)
            .filteredOn(registration -> type.getName().equals(registration.get("name")))
            .singleElement()
            .isEqualTo(
                Map.of(
                    "name",
                    type.getName(),
                    "allDeclaredFields",
                    true,
                    "queryAllDeclaredMethods",
                    true,
                    "methods",
                    List.of(Map.of("name", "<init>", "parameterTypes", List.of()))));
      }
      assertThat(registrations)
          .filteredOn(
              registration ->
                  GeneticAlgorithmMutationType.class.getName().equals(registration.get("name")))
          .singleElement()
          .isEqualTo(
              Map.of(
                  "name", GeneticAlgorithmMutationType.class.getName(), "allDeclaredFields", true));
    }
  }

  private static SolverConfig restore(SolverConfig config) {
    var writer = new StringWriter();
    new SolverConfigIO().write(config, writer);
    return new GreyCOSSolverAotFactory().solverConfigSupplier(writer.toString());
  }

  private static GeneticAlgorithmMutationOperatorConfig operator(
      GeneticAlgorithmMutationType type, double probability) {
    return new GeneticAlgorithmMutationOperatorConfig().withType(type).withProbability(probability);
  }
}
