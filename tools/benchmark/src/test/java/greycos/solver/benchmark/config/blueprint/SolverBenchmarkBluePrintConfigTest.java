package greycos.solver.benchmark.config.blueprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;

import org.junit.jupiter.api.Test;

class SolverBenchmarkBluePrintConfigTest {

  @Test
  void automaticBlueprintsIncludeGuidedLocalSearchWithoutRequiringProviders() {
    boolean guidedLocalSearchFound = false;
    for (var type : SolverBenchmarkBluePrintType.values()) {
      var blueprints =
          new SolverBenchmarkBluePrintConfig()
              .withSolverBenchmarkBluePrintType(type)
              .buildSolverBenchmarkConfigList();
      for (var blueprint : blueprints) {
        for (var phase : blueprint.getSolverConfig().getPhaseConfigList()) {
          if (phase instanceof LocalSearchPhaseConfig localSearch) {
            if (localSearch.getLocalSearchType() == LocalSearchType.GUIDED_LOCAL_SEARCH) {
              guidedLocalSearchFound = true;
              assertThat(localSearch.getGuidedLocalSearchConfig()).isNull();
            }
          }
        }
      }
    }
    assertThat(guidedLocalSearchFound).isTrue();
  }

  @Test
  void withoutSolverBenchmarkBluePrintType() {
    SolverBenchmarkBluePrintConfig config = new SolverBenchmarkBluePrintConfig();
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(config::validate)
        .withMessageContaining("solverBenchmarkBluePrintType");
  }
}
