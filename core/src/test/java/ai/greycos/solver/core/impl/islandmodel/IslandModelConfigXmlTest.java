package ai.greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;

import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.solver.SolverConfig;

import org.junit.jupiter.api.Test;

/** Test for island model XML configuration parsing. */
class IslandModelConfigXmlTest {

  @Test
  void islandModelConfigXmlShouldParseCorrectly() {
    // Load configuration from XML
    SolverConfig solverConfig =
        SolverConfig.createFromXmlResource(
            "ai/greycos/solver/core/config/solver/islandModelSolverConfig.xml");

    // Verify solver config loaded
    assertThat(solverConfig).isNotNull();
    assertThat(solverConfig.getSolutionClass()).isNotNull();
    assertThat(solverConfig.getSolutionClass().getName())
        .isEqualTo("ai.greycos.solver.core.testdomain.TestdataSolution");

    // Get island model phase config
    var phaseConfigList = solverConfig.getPhaseConfigList();
    System.out.println("DEBUG: phaseConfigList = " + phaseConfigList);
    System.out.println(
        "DEBUG: phaseConfigList size = "
            + (phaseConfigList != null ? phaseConfigList.size() : "null"));
    if (phaseConfigList != null && !phaseConfigList.isEmpty()) {
      System.out.println(
          "DEBUG: First phase type = " + phaseConfigList.get(0).getClass().getName());
      if (phaseConfigList.get(0) instanceof IslandModelPhaseConfig) {
        var islandModelPhaseConfig = (IslandModelPhaseConfig) phaseConfigList.get(0);
        System.out.println(
            "DEBUG: islandModelPhaseConfig.getPhaseConfigList() = "
                + islandModelPhaseConfig.getPhaseConfigList());
        System.out.println(
            "DEBUG: islandPhaseList size = "
                + (islandModelPhaseConfig.getPhaseConfigList() != null
                    ? islandModelPhaseConfig.getPhaseConfigList().size()
                    : "null"));
      }
    }
    assertThat(phaseConfigList).isNotNull();
    assertThat(phaseConfigList).hasSize(1);

    var islandModelPhaseConfig = (IslandModelPhaseConfig) phaseConfigList.get(0);
    assertThat(islandModelPhaseConfig).isNotNull();

    // Verify island model parameters
    assertThat(islandModelPhaseConfig.getIslandCount()).isEqualTo(2);
    assertThat(islandModelPhaseConfig.getMigrationFrequency()).isEqualTo(10);

    // Verify phase configurations - THIS IS THE KEY TEST
    assertThat(islandModelPhaseConfig.getPhaseConfigList()).isNotNull();
    assertThat(islandModelPhaseConfig.getPhaseConfigList()).hasSize(2);

    // Verify first phase is construction heuristic
    var phase0 = islandModelPhaseConfig.getPhaseConfigList().get(0);
    assertThat(phase0).isInstanceOf(ConstructionHeuristicPhaseConfig.class);
    var chConfig = (ConstructionHeuristicPhaseConfig) phase0;
    assertThat(chConfig.getConstructionHeuristicType()).isNotNull();

    // Verify second phase is local search
    var phase1 = islandModelPhaseConfig.getPhaseConfigList().get(1);
    assertThat(phase1).isInstanceOf(LocalSearchPhaseConfig.class);
    var lsConfig = (LocalSearchPhaseConfig) phase1;
    assertThat(lsConfig.getTerminationConfig()).isNotNull();
  }

  @Test
  void islandModelConfigXmlShouldBuildSolver() {
    // Load configuration from XML and build solver
    var solverFactory =
        SolverFactory.createFromXmlResource(
            "ai/greycos/solver/core/config/solver/islandModelSolverConfig.xml");
    var solver = solverFactory.buildSolver();

    // Verify solver was built successfully
    assertThat(solver).isNotNull();
  }
}
