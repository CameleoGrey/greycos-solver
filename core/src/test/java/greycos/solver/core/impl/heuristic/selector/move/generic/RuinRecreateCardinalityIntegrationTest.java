package greycos.solver.core.impl.heuristic.selector.move.generic;

import static org.assertj.core.api.Assertions.assertThat;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.RuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListRuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.common.decorator.FairSelectorProbabilityWeightFactory;
import greycos.solver.core.impl.score.DummySimpleScoreEasyScoreCalculator;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;

import org.junit.jupiter.api.Test;

class RuinRecreateCardinalityIntegrationTest {

  @Test
  void fairBasicUnionWithMoreThanTwentyEntitiesEvaluatesMoves() {
    var moves =
        new UnionMoveSelectorConfig()
            .withMoveSelectors(new ChangeMoveSelectorConfig(), new RuinRecreateMoveSelectorConfig())
            .withSelectorProbabilityWeightFactoryClass(FairSelectorProbabilityWeightFactory.class);
    var config =
        baseConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withPhases(new LocalSearchPhaseConfig().withMoveSelectorConfig(moves));
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    solver.solve(TestdataSolution.generateSolution(3, 21));
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isGreaterThanOrEqualTo(2L);
  }

  @Test
  void fairListUnionWithMoreThanTwentyValuesEvaluatesMoves() {
    var moves =
        new UnionMoveSelectorConfig()
            .withMoveSelectors(
                new ListChangeMoveSelectorConfig(),
                new ListRuinRecreateMoveSelectorConfig()
                    .withMinimumRuinedCount(1)
                    .withMaximumRuinedCount(2))
            .withSelectorProbabilityWeightFactoryClass(FairSelectorProbabilityWeightFactory.class);
    var config =
        baseConfig()
            .withSolutionClass(TestdataListSolution.class)
            .withEntityClasses(TestdataListEntity.class, TestdataListValue.class)
            .withPhases(new LocalSearchPhaseConfig().withMoveSelectorConfig(moves));
    var solver =
        (DefaultSolver<TestdataListSolution>)
            SolverFactory.<TestdataListSolution>create(config).buildSolver();
    solver.solve(TestdataListSolution.generateInitializedSolution(21, 3));
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isGreaterThanOrEqualTo(2L);
  }

  private SolverConfig baseConfig() {
    return new SolverConfig()
        .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
        .withEasyScoreCalculatorClass(DummySimpleScoreEasyScoreCalculator.class)
        .withTerminationConfig(new TerminationConfig().withMoveCountLimit(2L));
  }
}
