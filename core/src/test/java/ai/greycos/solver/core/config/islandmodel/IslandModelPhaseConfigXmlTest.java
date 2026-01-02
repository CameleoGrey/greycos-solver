package ai.greycos.solver.core.config.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;

import ai.greycos.solver.core.config.solver.SolverConfig;

import org.junit.jupiter.api.Test;

/** Test that IslandModelPhaseConfig can be properly deserialized from XML. */
class IslandModelPhaseConfigXmlTest {

  @Test
  void islandModelPhaseConfigFromXml() {
    SolverConfig solverConfig =
        SolverConfig.createFromXmlResource(
            "ai/greycos/solver/core/config/solver/islandModelSolverConfig.xml");

    assertThat(solverConfig).isNotNull();
    assertThat(solverConfig.getPhaseConfigList()).isNotNull().hasSize(1);

    var phaseConfig = solverConfig.getPhaseConfigList().get(0);
    assertThat(phaseConfig).isInstanceOf(IslandModelPhaseConfig.class);

    IslandModelPhaseConfig islandModelConfig = (IslandModelPhaseConfig) phaseConfig;

    // Verify island model parameters are read from XML
    assertThat(islandModelConfig.getIslandCount()).isEqualTo(2);
    assertThat(islandModelConfig.getMigrationFrequency()).isEqualTo(10);
    assertThat(islandModelConfig.getTerminationConfig())
        .isNull(); // No termination at island model level in this XML
  }

  @Test
  void islandModelPhaseConfigWithTerminationFromXml() {
    SolverConfig solverConfig =
        SolverConfig.createFromXmlResource(
            "examples/cloudbalancing/cloudBalancingSolverConfig.xml");

    assertThat(solverConfig).isNotNull();
    assertThat(solverConfig.getPhaseConfigList()).isNotNull().hasSize(2);

    // Second phase should be island model
    var phaseConfig = solverConfig.getPhaseConfigList().get(1);
    assertThat(phaseConfig).isInstanceOf(IslandModelPhaseConfig.class);

    IslandModelPhaseConfig islandModelConfig = (IslandModelPhaseConfig) phaseConfig;

    // Verify island model parameters are read from XML
    assertThat(islandModelConfig.getIslandCount()).isEqualTo(4);
    assertThat(islandModelConfig.getMigrationFrequency()).isEqualTo(Integer.MAX_VALUE);
    assertThat(islandModelConfig.getReceiveGlobalUpdateFrequency()).isEqualTo(1);
    assertThat(islandModelConfig.getMigrationTimeout()).isEqualTo(1000L);
    assertThat(islandModelConfig.getCompareGlobalEnabled()).isNull(); // Not set in XML
  }
}
