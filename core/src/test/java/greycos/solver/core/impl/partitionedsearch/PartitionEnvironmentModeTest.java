package greycos.solver.core.impl.partitionedsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchPhaseScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PartitionEnvironmentModeTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void partitionAndNestedIslandPhasesInheritTheirEffectiveMode(boolean nestedIsland) {
    var fullAssert = search().withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT);
    var inherited = search();
    List<PhaseConfig> children;
    if (nestedIsland) {
      var island =
          new IslandModelPhaseConfig()
              .withIslandCount(1)
              .withPhaseConfigList(List.of(fullAssert, inherited));
      island.setEnvironmentMode(EnvironmentMode.FULL_ASSERT);
      children = List.of(island);
    } else {
      children = List.of(fullAssert, inherited);
    }
    var partition =
        new PartitionedSearchPhaseConfig()
            .withSolutionPartitionerClass(OnePartition.class)
            .withPhaseConfigList(children)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT);
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
            .withPhases(partition);
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var started = new AtomicInteger();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
            assertThat(((PartitionedSearchPhaseScope<TestdataSolution>) scope).getPartCount())
                .isEqualTo(1);
            assertThat(scope.getScoreDirector().getEnvironmentMode())
                .isEqualTo(EnvironmentMode.FULL_ASSERT);
            started.incrementAndGet();
          }
        });
    ObserveMode.modes.clear();
    var result = solver.solve(TestdataSolution.generateSolution(3, 4));
    assertThat(started).hasValue(1);
    assertThat(ObserveMode.modes)
        .contains(EnvironmentMode.TRACKED_FULL_ASSERT, EnvironmentMode.FULL_ASSERT)
        .doesNotContain(EnvironmentMode.PHASE_ASSERT);
    assertThat(result.getScore())
        .isEqualTo(new TestdataEasyScoreCalculator().calculateScore(result));
  }

  private static LocalSearchPhaseConfig search() {
    return new LocalSearchPhaseConfig()
        .withMoveSelectorConfig(
            new ChangeMoveSelectorConfig()
                .withEntitySelectorConfig(
                    new EntitySelectorConfig(TestdataEntity.class)
                        .withFilterClass(ObserveMode.class)))
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(3));
  }

  public static class OnePartition implements SolutionPartitioner<TestdataSolution> {
    @Override
    public List<TestdataSolution> splitWorkingSolution(
        ScoreDirector<TestdataSolution> director, Integer runnablePartThreadLimit) {
      var innerDirector = (InnerScoreDirector<TestdataSolution, ?>) director;
      assertThat(innerDirector.getEnvironmentMode()).isEqualTo(EnvironmentMode.FULL_ASSERT);
      return List.of(innerDirector.cloneWorkingSolution());
    }
  }

  public static class ObserveMode implements SelectionFilter<TestdataSolution, TestdataEntity> {
    static final ConcurrentLinkedQueue<EnvironmentMode> modes = new ConcurrentLinkedQueue<>();

    @Override
    public boolean accept(ScoreDirector<TestdataSolution> director, TestdataEntity entity) {
      modes.add(((InnerScoreDirector<TestdataSolution, ?>) director).getEnvironmentMode());
      return true;
    }
  }
}
