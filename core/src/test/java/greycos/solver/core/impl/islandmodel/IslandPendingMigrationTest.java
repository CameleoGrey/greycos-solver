package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
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

class IslandPendingMigrationTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void improvingMigrationRebasesOntoNextPhaseWorkingClone(boolean requiresReset) {
    var scope = basicScope();
    var initial = TestdataSolution.generateUninitializedSolution(3, 3);
    initial.getEntityList().forEach(entity -> entity.setValue(initial.getValueList().getFirst()));
    scope.setInitialSolution(initial);
    try (var director = scope.getScoreDirector()) {
      scope.setBestScore(director.calculateScore());
      var oldWorking = director.getWorkingSolution();
      var migrant = director.cloneWorkingSolution();
      for (int i = 0; i < migrant.getEntityList().size(); i++) {
        migrant.getEntityList().get(i).setValue(migrant.getValueList().get(i));
      }
      var migrantScore =
          InnerScore.fullyAssigned(new TestdataEasyScoreCalculator().calculateScore(migrant));
      scope.setPendingMoveIfBetter(
          SolutionSyncMove.createMove(director, migrant), migrantScore, requiresReset);

      IslandPendingMigrationTest.<TestdataSolution>islandSolver()
          .restoreWorkingSolutionForNextPhase(scope);

      var pending = scope.consumePendingMove();
      assertThat(pending).isNotNull();
      assertThat(pending.score()).isEqualTo(migrantScore);
      assertThat(pending.requiresReset()).isEqualTo(requiresReset);
      assertThat(director.getWorkingSolution()).isNotSameAs(oldWorking);
      director.executeMove(pending.move());
      assertThat(director.calculateScore()).isEqualTo(migrantScore);
      assertThat(new TestdataEasyScoreCalculator().calculateScore(oldWorking))
          .isEqualTo(SimpleScore.of(-6));
    }
  }

  @Test
  void listMigrationRebasesEntitiesAndValuesOntoNextPhaseWorkingClone() {
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
      var movedValue = migrant.getEntityList().getFirst().getValueList().removeLast();
      migrant.getEntityList().getLast().getValueList().add(movedValue);
      var migrantScore =
          InnerScore.fullyAssigned(
              new TestdataListVarEasyScoreCalculator().calculateScore(migrant));
      scope.setPendingMoveIfBetter(
          SolutionSyncMove.createMove(director, migrant), migrantScore, true);

      IslandPendingMigrationTest.<TestdataListSolution>islandSolver()
          .restoreWorkingSolutionForNextPhase(scope);

      var pending = scope.consumePendingMove();
      assertThat(pending).isNotNull();
      director.executeMove(pending.move());
      assertThat(director.calculateScore()).isEqualTo(migrantScore);
      assertThat(oldWorking.getEntityList())
          .allSatisfy(entity -> assertThat(entity.getValueList()).hasSize(2));
      var working = director.getWorkingSolution();
      for (var entity : working.getEntityList()) {
        for (int index = 0; index < entity.getValueList().size(); index++) {
          var value = entity.getValueList().get(index);
          assertThat(value.getEntity()).isSameAs(entity);
          assertThat(value.getIndex()).isEqualTo(index);
          assertThat(working.getValueList()).contains(value);
          assertThat(oldWorking.getValueList()).noneMatch(oldValue -> oldValue == value);
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"equal", "worse", "unscored", "otherMove"})
  void handoffDropsStaleOrUnsupportedPendingMoves(String kind) {
    var scope = basicScope();
    scope.setInitialSolution(TestdataSolution.generateSolution(3, 3));
    try (var director = scope.getScoreDirector()) {
      scope.setBestScore(director.calculateScore());
      var syncMove = SolutionSyncMove.createMove(director, scope.getBestSolution());
      switch (kind) {
        case "equal" ->
            scope.setPendingMoveIfBetter(
                syncMove, InnerScore.fullyAssigned(SimpleScore.ZERO), true);
        case "worse" ->
            scope.setPendingMoveIfBetter(
                syncMove, InnerScore.fullyAssigned(SimpleScore.of(-1)), true);
        case "unscored" -> scope.setPendingMove(syncMove, true);
        case "otherMove" ->
            scope.setPendingMoveIfBetter(
                view -> {}, InnerScore.fullyAssigned(SimpleScore.ONE), true);
        default -> throw new IllegalStateException(kind);
      }

      IslandPendingMigrationTest.<TestdataSolution>islandSolver()
          .restoreWorkingSolutionForNextPhase(scope);

      assertThat(scope.consumePendingMove()).isNull();
      assertThat(director.calculateScore()).isEqualTo(scope.getBestScore());
    }
  }

  private static SolverScope<TestdataSolution> basicScope() {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class);
    return ((DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver())
        .getSolverScope();
  }

  @SuppressWarnings("unchecked")
  private static <Solution_> IslandSolver<Solution_> islandSolver() {
    return new IslandSolver<>(
        mock(BestSolutionRecaller.class), mock(BasicPlumbingTermination.class), List.of());
  }
}
