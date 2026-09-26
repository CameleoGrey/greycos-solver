package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IslandEnvironmentModeTest {

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2"})
  void childPhasesSwitchDirectorsAndTheSameSolverCanRunAgain(String moveThreadCount) {
    var fullAssert = search(moveThreadCount).withEnvironmentMode(EnvironmentMode.FULL_ASSERT);
    var inherited = search(moveThreadCount);
    var island =
        new IslandModelPhaseConfig()
            .withIslandCount(1)
            .withPhaseConfigList(List.of(fullAssert, inherited));
    island.setEnvironmentMode(EnvironmentMode.STEP_ASSERT);
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
            .withPhases(island);
    var solver = SolverFactory.<TestdataSolution>create(config).buildSolver();

    for (int run = 0; run < 2; run++) {
      ObserveMode.modes.clear();
      var result = solver.solve(TestdataSolution.generateSolution(3, 4));
      assertThat(ObserveMode.modes)
          .contains(EnvironmentMode.FULL_ASSERT, EnvironmentMode.STEP_ASSERT)
          .doesNotContain(EnvironmentMode.PHASE_ASSERT);
      assertThat(result.getScore())
          .isEqualTo(new TestdataEasyScoreCalculator().calculateScore(result));
    }
  }

  private static LocalSearchPhaseConfig search(String moveThreadCount) {
    return new LocalSearchPhaseConfig()
        .withMoveThreadCount(moveThreadCount)
        .withMoveSelectorConfig(
            new ChangeMoveSelectorConfig()
                .withEntitySelectorConfig(
                    new EntitySelectorConfig(TestdataEntity.class)
                        .withFilterClass(ObserveMode.class)))
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(3));
  }

  public static class ObserveMode implements SelectionFilter<TestdataSolution, TestdataEntity> {
    static final ConcurrentLinkedQueue<EnvironmentMode> modes = new ConcurrentLinkedQueue<>();

    @Override
    public boolean accept(ScoreDirector<TestdataSolution> scoreDirector, TestdataEntity entity) {
      modes.add(((InnerScoreDirector<TestdataSolution, ?>) scoreDirector).getEnvironmentMode());
      return true;
    }
  }
}
