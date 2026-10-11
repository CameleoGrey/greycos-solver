package greycos.solver.core.impl.islandmodel;

import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.assertState;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.config;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.rearrange;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.recompute;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.scope;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class MultipleListIslandLifecycleTest {

  @Test
  void migrationAndRetainedUndoRebaseAcrossIndependentlyConstructedModels() {
    var sourceScope = scope();
    var destinationScope = scope();
    sourceScope.setInitialSolution(
        TestdataMultipleListSolution.generateInitializedSolution(4, 6, 2));
    destinationScope.setInitialSolution(
        TestdataMultipleListSolution.generateInitializedSolution(4, 6, 2));
    try (var source = sourceScope.<SimpleScore>getScoreDirector();
        var destination = destinationScope.<SimpleScore>getScoreDirector()) {
      source.calculateScore();
      destination.calculateScore();
      assertThat(source.getSolutionDescriptor()).isNotSameAs(destination.getSolutionDescriptor());
      var before = snapshot(source.getWorkingSolution());
      var migrant = source.cloneWorkingSolution();
      rearrange(migrant, 1);
      var move = SolutionSyncMove.createMove(source, migrant);
      var undo =
          source
              .getMoveDirector()
              .executeTemporaryProducingUndoMove(
                  move,
                  score -> {
                    assertState(source.getWorkingSolution());
                    assertThat(score.raw()).isEqualTo(recompute(migrant));
                  });
      destination.executeMove(move.rebase(destination));
      assertState(destination.getWorkingSolution());
      assertThat(snapshot(destination.getWorkingSolution())).isEqualTo(snapshot(migrant));
      assertThat(destination.calculateScore().raw()).isEqualTo(recompute(migrant));
      assertThat(destination.getWorkingSolution().getFirstValueList())
          .noneMatch(
              value ->
                  source.getWorkingSolution().getFirstValueList().stream()
                      .anyMatch(other -> value == other));
      assertThat(destination.getWorkingSolution().getSecondValueList())
          .noneMatch(
              value ->
                  source.getWorkingSolution().getSecondValueList().stream()
                      .anyMatch(other -> value == other));
      destination.executeMove(undo.rebase(destination.getMoveDirector()));
      assertState(destination.getWorkingSolution());
      assertThat(snapshot(destination.getWorkingSolution())).isEqualTo(before);
      assertThat(snapshot(source.getWorkingSolution())).isEqualTo(before);
      assertThat(destination.calculateScore()).isEqualTo(source.calculateScore());
    }
  }

  @Test
  void failingMigrationCallbackUndoesBothListsAndBasicAssignments() {
    var scope = scope();
    scope.setInitialSolution(TestdataMultipleListSolution.generateInitializedSolution(4, 6, 2));
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var beforeScore = director.calculateScore();
      var before = snapshot(director.getWorkingSolution());
      var migrant = director.cloneWorkingSolution();
      rearrange(migrant, 1);
      var failure = new IllegalStateException("migration callback failed");
      assertThatThrownBy(
              () ->
                  director
                      .getMoveDirector()
                      .executeTemporaryProducingUndoMove(
                          SolutionSyncMove.createMove(director, migrant),
                          score -> {
                            assertState(director.getWorkingSolution());
                            assertThat(snapshot(director.getWorkingSolution()))
                                .isEqualTo(snapshot(migrant));
                            throw failure;
                          }))
          .isSameAs(failure);
      assertState(director.getWorkingSolution());
      assertThat(snapshot(director.getWorkingSolution())).isEqualTo(before);
      assertThat(director.calculateScore()).isEqualTo(beforeScore);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void pendingMigrationSurvivesWorkingSolutionReplacement(boolean requiresReset) {
    var scope = scope();
    scope.setInitialSolution(TestdataMultipleListSolution.generateInitializedSolution(4, 6, 2));
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      scope.setBestScore(director.calculateScore());
      var oldWorking = director.getWorkingSolution();
      var before = snapshot(oldWorking);
      var migrant = director.cloneWorkingSolution();
      rearrange(migrant, 1);
      var score = InnerScore.fullyAssigned(recompute(migrant));
      assertThat(score).isGreaterThan(scope.getBestScore());
      scope.setPendingMoveIfBetter(
          SolutionSyncMove.createMove(director, migrant), score, requiresReset);
      var islandSolver =
          new IslandSolver<TestdataMultipleListSolution>(
              EnvironmentMode.FULL_ASSERT,
              mock(ScoreDirectorFactory.class),
              mock(BestSolutionRecaller.class),
              mock(BasicPlumbingTermination.class),
              List.of());
      islandSolver.restoreWorkingSolutionForNextPhase(scope);
      var pending = scope.consumePendingMove();
      assertThat(pending).isNotNull();
      assertThat(pending.requiresReset()).isEqualTo(requiresReset);
      assertThat(director.getWorkingSolution()).isNotSameAs(oldWorking);
      director.executeMove(pending.move());
      assertState(director.getWorkingSolution());
      assertThat(snapshot(director.getWorkingSolution())).isEqualTo(snapshot(migrant));
      assertThat(director.calculateScore()).isEqualTo(score);
      assertState(oldWorking);
      assertThat(snapshot(oldWorking)).isEqualTo(before);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void islandsPublishCompleteMultipleListSolutions(String moveThreads) {
    var solver =
        SolverFactory.<TestdataMultipleListSolution>create(
                config()
                    .withMoveThreadCount(moveThreads)
                    .withPhases(
                        new IslandModelPhaseConfig()
                            .withIslandCount(2)
                            .withPhaseConfigList(
                                List.of(
                                    new LocalSearchPhaseConfig()
                                        .withTerminationConfig(
                                            new TerminationConfig().withStepCountLimit(12))))))
            .buildSolver();
    var result = solver.solve(TestdataMultipleListSolution.generateInitializedSolution(4, 6, 2));
    assertState(result);
    assertThat(result.getScore()).isEqualTo(recompute(result));
  }
}
