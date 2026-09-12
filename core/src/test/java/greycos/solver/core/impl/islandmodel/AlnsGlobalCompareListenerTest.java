package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.impl.alns.AlnsPhaseScope;
import greycos.solver.core.impl.alns.AlnsStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

class AlnsGlobalCompareListenerTest {
  @Test
  void globalComparisonSchedulesAndAppliesMigrantForAlnsStep() {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class);
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var scope = solver.getSolverScope();
    var initial = TestdataSolution.generateUninitializedSolution(2, 3);
    initial.getEntityList().forEach(entity -> entity.setValue(initial.getValueList().getFirst()));
    scope.setInitialSolution(initial);
    try (var director = scope.getScoreDirector()) {
      var initialScore = director.calculateScore();
      scope.setBestScore(initialScore);
      var migrant = director.cloneWorkingSolution();
      migrant.getEntityList().getFirst().setValue(migrant.getValueList().getLast());
      var migrantScore = new TestdataEasyScoreCalculator().calculateScore(migrant);
      assertThat(migrantScore).isGreaterThan((SimpleScore) initialScore.raw());
      var global = new SharedGlobalState<TestdataSolution>();
      global.tryUpdate(migrant, InnerScore.fullyAssigned(migrantScore));
      var listener =
          new GlobalCompareListener<>(
              global, IslandModelConfig.builder().withReceiveGlobalUpdateFrequency(1).build(), 0);
      listener.stepEnded(new AlnsStepScope<>(new AlnsPhaseScope<>(scope, 0)));
      var pending = scope.consumePendingMove();
      assertThat(pending).isNotNull();
      assertThat(pending.requiresReset()).isTrue();
      director.getMoveDirector().execute(pending.move());
      assertThat(director.calculateScore().raw()).isEqualTo(migrantScore);
      assertThat(director.getWorkingSolution().getEntityList().getFirst().getValue().getCode())
          .isEqualTo(migrant.getValueList().getLast().getCode());
    }
  }
}
