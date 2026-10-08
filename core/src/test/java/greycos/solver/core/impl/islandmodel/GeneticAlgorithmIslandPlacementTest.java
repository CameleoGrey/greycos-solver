package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class GeneticAlgorithmIslandPlacementTest {

  @Test
  void effectiveEnabledMoveWorkersFailWhenFactoryIsCreated() {
    for (var island :
        List.of(
            island(new GeneticAlgorithmPhaseConfig().withMoveThreadCount("1")),
            island(new GeneticAlgorithmPhaseConfig()).withMoveThreadCount("1"),
            island(island(new GeneticAlgorithmPhaseConfig()).withMoveThreadCount("1")))) {
      assertThatThrownBy(() -> SolverFactory.create(config(island)))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("geneticAlgorithm")
          .hasMessageContaining("moveThreadCount (1)")
          .hasMessageContaining("NONE");
    }
  }

  @Test
  void explicitNoneAndNestedIslandDefaultOverrideEnclosingMoveWorkers() {
    var explicit =
        island(new GeneticAlgorithmPhaseConfig().withMoveThreadCount("NONE"))
            .withMoveThreadCount("1");
    var nested = island(island(new GeneticAlgorithmPhaseConfig())).withMoveThreadCount("1");
    assertThatCode(() -> SolverFactory.create(config(explicit)).buildSolver())
        .doesNotThrowAnyException();
    assertThatCode(() -> SolverFactory.create(config(nested)).buildSolver())
        .doesNotThrowAnyException();
  }

  @Test
  void partitionAncestorIsRejectedThroughAnyIslandDepth() {
    var partition =
        new PartitionedSearchPhaseConfig()
            .withPhaseConfigs(island(island(new GeneticAlgorithmPhaseConfig())));
    assertThatThrownBy(() -> SolverFactory.create(config(island(partition))))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("cannot be nested under partitionedSearch");
  }

  @Test
  void defaultIslandNonePreventsRootMoveWorkersFromLeakingIntoGa() {
    var ga =
        new GeneticAlgorithmPhaseConfig()
            .withPopulationSize(2)
            .withMigrationRate(0.0)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(5));
    var config = config(island(ga).withIslandCount(2)).withMoveThreadCount("1");
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();

    var result = solver.solve(TestdataSolution.generateSolution(3, 3));

    assertThat(result.getScore())
        .isEqualTo(new TestdataEasyScoreCalculator().calculateScore(result));
    var phase = (DefaultIslandModelPhase<TestdataSolution>) solver.getPhaseList().getFirst();
    assertThat(phase.getIslandDiagnostics())
        .hasSize(2)
        .allSatisfy(
            diagnostics -> {
              assertThat(diagnostics.moveEvaluationCount()).isEqualTo(5L);
              assertThat(diagnostics.physicalScoreCalculationCount()).isPositive();
              assertThat(diagnostics.geneticAlgorithmPhases()).hasSize(1);
              assertThat(
                      diagnostics.geneticAlgorithmPhases().getFirst().migration().exportedBatches())
                  .isZero();
              assertThat(diagnostics.geneticAlgorithmPhases().getFirst().completedGenerations())
                  .isEqualTo(2L);
            });
    assertThat(phase.getGeneticAlgorithmMigrationDiagnostics()).hasSize(2);
    assertThatThrownBy(() -> phase.getIslandDiagnostics().clear())
        .isInstanceOf(UnsupportedOperationException.class);

    solver.solve(TestdataSolution.generateSolution(3, 3));
    assertThat(phase.getIslandDiagnostics())
        .hasSize(2)
        .allSatisfy(diagnostics -> assertThat(diagnostics.moveEvaluationCount()).isEqualTo(5L));
  }

  private static IslandModelPhaseConfig island(PhaseConfig<?> phase) {
    return new IslandModelPhaseConfig().withPhaseConfigList(List.of(phase));
  }

  private static SolverConfig config(PhaseConfig<?> phase) {
    return PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
        .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withPhases(phase);
  }
}
