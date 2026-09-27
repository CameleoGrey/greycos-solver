package greycos.solver.core.impl.phase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataSolutionPartitioner;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PartitionPhaseReachabilityTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @Timeout(10)
  void finitePartitionChildrenAllowFollowingGlobalSearch(boolean localSearchChild) {
    var partition = partition().withPhaseConfigs(new ConstructionHeuristicPhaseConfig());
    if (localSearchChild) {
      partition.withPhaseConfigs(new ConstructionHeuristicPhaseConfig(), finiteSearch());
    }
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withPhases(partition, finiteSearch());
    var solution =
        SolverFactory.<TestdataSolution>create(config)
            .buildSolver()
            .solve(TestdataSolution.generateUninitializedSolution(3, 6));
    assertThat(solution.getEntityList())
        .hasSize(6)
        .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
  }

  @Test
  void recursivelyFiniteNestedPartitionsCanTerminate() {
    assertThat(
            PhaseFactory.canTerminate(
                partition()
                    .withPhaseConfigs(
                        partition()
                            .withPhaseConfigs(
                                new ConstructionHeuristicPhaseConfig(), finiteSearch()))))
        .isTrue();
    assertThat(
            PhaseFactory.canTerminate(
                partition()
                    .withPhaseConfigs(
                        partition()
                            .withPhaseConfigs(
                                new ConstructionHeuristicPhaseConfig(),
                                new LocalSearchPhaseConfig()))))
        .isFalse();
  }

  @Test
  void defaultChildrenNeedOuterTermination() {
    assertThat(PhaseFactory.canTerminate(partition())).isFalse();
    assertThat(PhaseFactory.canTerminate(partition().withPhaseConfigList(List.of()))).isFalse();
    assertThat(
            PhaseFactory.canTerminate(
                partition().withTerminationConfig(new TerminationConfig().withStepCountLimit(1))))
        .isTrue();
  }

  @Test
  void unterminatedChildrenStillMakeFollowingPhaseUnreachable() {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withPhases(
                partition()
                    .withPhaseConfigs(
                        new ConstructionHeuristicPhaseConfig(), new LocalSearchPhaseConfig()),
                finiteSearch());
    assertThatThrownBy(() -> SolverFactory.create(config).buildSolver())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unreachable phase");
  }

  private static PartitionedSearchPhaseConfig partition() {
    return new PartitionedSearchPhaseConfig()
        .withSolutionPartitionerClass(TestdataSolutionPartitioner.class);
  }

  private static LocalSearchPhaseConfig finiteSearch() {
    return new LocalSearchPhaseConfig()
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(1));
  }
}
