package greycos.solver.core.impl.islandmodel.resumable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.islandmodel.SolutionSyncMove;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.TestdataListVarEasyScoreCalculator;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResidentSearchMigrationTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void finalStepMigrationIsRebasedOntoTheFollowingPhasesWorkingEntities(boolean requiresReset) {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class);
    var scope =
        ((DefaultSolver<TestdataSolution>)
                SolverFactory.<TestdataSolution>create(config).buildSolver())
            .getSolverScope();
    var initial = TestdataSolution.generateUninitializedSolution(3, 3);
    initial.getEntityList().forEach(entity -> entity.setValue(initial.getValueList().getFirst()));
    scope.setInitialSolution(initial);
    try (var director = scope.getScoreDirector()) {
      scope.setBestScore(director.calculateScore());
      var oldWorking = director.getWorkingSolution();
      var migrant = director.cloneWorkingSolution();
      for (int i = 0; i < migrant.getEntityList().size(); i++)
        migrant.getEntityList().get(i).setValue(migrant.getValueList().get(i));
      var expected =
          InnerScore.fullyAssigned(new TestdataEasyScoreCalculator().calculateScore(migrant));
      scope.setPendingMoveIfBetter(
          SolutionSyncMove.createMove(director, migrant), expected, requiresReset);

      ResidentSearchMigrationTest.<TestdataSolution>resident().restoreWorkingForNextPhase(scope);

      var pending = scope.consumePendingMove();
      assertThat(pending).isNotNull();
      assertThat(pending.requiresReset()).isEqualTo(requiresReset);
      assertThat(director.getWorkingSolution()).isNotSameAs(oldWorking);
      director.executeMove(pending.move());
      assertThat(director.calculateScore()).isEqualTo(expected);
      assertThat(new TestdataEasyScoreCalculator().calculateScore(oldWorking))
          .isEqualTo(SimpleScore.of(-6));
    }
  }

  @Test
  void listMigrationRebasesBothValuesAndEntitiesAndPreservesTheOldSnapshot() {
    var config =
        PlannerTestUtils.buildSolverConfig(
                TestdataListSolution.class, TestdataListEntity.class, TestdataListValue.class)
            .withEasyScoreCalculatorClass(TestdataListVarEasyScoreCalculator.class);
    var scope =
        ((DefaultSolver<TestdataListSolution>)
                SolverFactory.<TestdataListSolution>create(config).buildSolver())
            .getSolverScope();
    scope.setInitialSolution(TestdataListSolution.generateInitializedSolution(4, 2));
    try (var director = scope.getScoreDirector()) {
      scope.setBestScore(director.calculateScore());
      var oldWorking = director.getWorkingSolution();
      var migrant = director.cloneWorkingSolution();
      var moved = migrant.getEntityList().getFirst().getValueList().removeLast();
      migrant.getEntityList().getLast().getValueList().add(moved);
      var expected =
          InnerScore.fullyAssigned(
              new TestdataListVarEasyScoreCalculator().calculateScore(migrant));
      scope.setPendingMoveIfBetter(SolutionSyncMove.createMove(director, migrant), expected, true);

      ResidentSearchMigrationTest.<TestdataListSolution>resident()
          .restoreWorkingForNextPhase(scope);

      var pending = scope.consumePendingMove();
      assertThat(pending).isNotNull();
      director.executeMove(pending.move());
      assertThat(director.calculateScore()).isEqualTo(expected);
      assertThat(oldWorking.getEntityList())
          .allSatisfy(entity -> assertThat(entity.getValueList()).hasSize(2));
      for (var entity : director.getWorkingSolution().getEntityList())
        for (int index = 0; index < entity.getValueList().size(); index++) {
          var value = entity.getValueList().get(index);
          assertThat(value.getEntity()).isSameAs(entity);
          assertThat(value.getIndex()).isEqualTo(index);
          assertThat(oldWorking.getValueList()).noneMatch(old -> old == value);
        }
    }
  }

  @SuppressWarnings("unchecked")
  private static <Solution_> ResidentSearchSolver<Solution_> resident() {
    return new ResidentSearchSolver<>(
        EnvironmentMode.PHASE_ASSERT,
        mock(ScoreDirectorFactory.class),
        mock(BestSolutionRecaller.class),
        mock(BasicPlumbingTermination.class),
        List.of());
  }
}
