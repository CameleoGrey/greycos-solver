package greycos.solver.core.impl.heuristic.thread;

import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.assertState;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.config;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.rearrange;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.recompute;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.scope;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.partitionedsearch.scope.PartitionChangeMove;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.SolutionView;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class MultipleListMoveThreadingTest {

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "3"})
  void fixedSeedSearchReproducesBothListsAndBasicVariables(String threads) {
    assertThat(solve(threads)).isEqualTo(solve(threads));
  }

  private static List<String> solve(String threads) {
    var solver =
        (DefaultSolver<TestdataMultipleListSolution>)
            SolverFactory.<TestdataMultipleListSolution>create(
                    config()
                        .withMoveThreadCount(threads)
                        .withMoveThreadBufferSize(2)
                        .withPhases(
                            new LocalSearchPhaseConfig()
                                .withAcceptorConfig(
                                    new LocalSearchAcceptorConfig().withLateAcceptanceSize(32))
                                .withForagerConfig(
                                    new LocalSearchForagerConfig().withAcceptedCountLimit(4))
                                .withTerminationConfig(
                                    new TerminationConfig().withStepCountLimit(20))))
                .buildSolver();
    var trace = new ArrayList<String>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataMultipleListSolution> stepScope) {
            if (stepScope instanceof LocalSearchStepScope<TestdataMultipleListSolution> step) {
              var solution = step.getWorkingSolution();
              assertState(solution);
              assertThat(step.getScore().raw()).isEqualTo(recompute(solution));
              assertThat(step.getScore().unassignedCount()).isZero();
              trace.add(
                  step.getStepIndex()
                      + ":"
                      + step.getStep()
                      + ":"
                      + step.getScore()
                      + ":"
                      + step.getSelectedMoveCount()
                      + ":"
                      + snapshot(solution));
            }
          }
        });
    var problem = TestdataMultipleListSolution.generateInitializedSolution(6, 8, 3);
    var before = snapshot(problem);
    var result = solver.solve(problem);
    assertThat(trace).hasSize(20);
    assertState(result);
    assertThat(result.getScore()).isEqualTo(recompute(result));
    assertThat(snapshot(problem)).isEqualTo(before);
    trace.add(snapshot(result) + ":" + result.getScore());
    return trace;
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3})
  void compoundReplayAcknowledgementAndUndoPreserveIndependentWorkerState(int workerCount)
      throws InterruptedException {
    var parentScope = scope();
    parentScope.setInitialSolution(
        TestdataMultipleListSolution.generateInitializedSolution(6, 8, 3));
    var closedStates = new CopyOnWriteArrayList<String>();
    try (var director = parentScope.<SimpleScore>getScoreDirector();
        var executor = Executors.newFixedThreadPool(workerCount)) {
      director.calculateScore();
      var pipeline =
          new MoveEvaluationPipeline<TestdataMultipleListSolution>(
              executor, workerCount, workerCount, 0, false, true, true, true, true, true);
      pipeline.setMetadataCollectorFactory(
          worker ->
              new MoveEvaluationPipeline.CandidateMetadataCollector<>() {
                @Override
                public void beforeEvaluation(MoveEvaluationPipeline.EvaluationContext context) {
                  assertState(worker.getWorkingSolution());
                  assertThat(snapshot(worker.getWorkingSolution()))
                      .isEqualTo(((ExpectedState) context).before());
                }

                @Override
                public MoveEvaluationPipeline.EvaluationMetadata collect(
                    SolutionView<TestdataMultipleListSolution> view,
                    Move<TestdataMultipleListSolution> move,
                    MoveEvaluationPipeline.EvaluationContext context) {
                  assertState(worker.getWorkingSolution());
                  assertThat(snapshot(worker.getWorkingSolution()))
                      .isEqualTo(((ExpectedState) context).after());
                  assertThat(worker.calculateScore().raw())
                      .isEqualTo(recompute(worker.getWorkingSolution()));
                  return null;
                }

                @Override
                public void close() {
                  assertState(worker.getWorkingSolution());
                  closedStates.add(snapshot(worker.getWorkingSolution()));
                }
              });
      try {
        pipeline.start(director);
        for (int step = 0; step < 4; step++) {
          pipeline.startNextStep(step);
          var before = snapshot(director.getWorkingSolution());
          var target = director.cloneWorkingSolution();
          rearrange(target, step + 1);
          var move = PartitionChangeMove.createMove(director, target, 0).rebase(director);
          var expected = recompute(target);
          for (int candidate = 0; candidate < workerCount; candidate++) {
            pipeline.submit(candidate, move, new ExpectedState(before, snapshot(target)));
          }
          for (int candidate = 0; candidate < workerCount; candidate++) {
            var result = pipeline.take();
            assertThat(result).isNotNull();
            assertThat(result.stepIndex()).isEqualTo(step);
            assertThat(result.moveIndex()).isEqualTo(candidate);
            assertThat(result.score().raw()).isEqualTo(expected);
          }
          assertThat(snapshot(director.getWorkingSolution())).isEqualTo(before);
          pipeline.cancelStep();
          director.executeMove(move);
          var score = director.calculateScore();
          assertState(director.getWorkingSolution());
          assertThat(score.raw()).isEqualTo(expected);
          pipeline.applyStep(step + 1, move, score);
        }
        pipeline.close();
        assertThat(closedStates)
            .hasSize(workerCount)
            .containsOnly(snapshot(director.getWorkingSolution()));
      } finally {
        pipeline.abort();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failureOrCancellationRestoresBothListsBeforeClosingWorkers(boolean cancel)
      throws InterruptedException {
    var parentScope = scope();
    parentScope.setInitialSolution(
        TestdataMultipleListSolution.generateInitializedSolution(4, 6, 2));
    var evaluated = new CountDownLatch(1);
    var closedStates = new CopyOnWriteArrayList<String>();
    try (var director = parentScope.<SimpleScore>getScoreDirector();
        var executor = Executors.newFixedThreadPool(2)) {
      director.calculateScore();
      var before = snapshot(director.getWorkingSolution());
      var target = director.cloneWorkingSolution();
      rearrange(target, 1);
      var move = PartitionChangeMove.createMove(director, target, 0).rebase(director);
      var pipeline =
          new MoveEvaluationPipeline<TestdataMultipleListSolution>(
              executor, 2, 2, 0, false, true, true, true, true, true);
      pipeline.setMetadataCollectorFactory(
          worker ->
              new MoveEvaluationPipeline.CandidateMetadataCollector<>() {
                @Override
                public MoveEvaluationPipeline.EvaluationMetadata collect(
                    SolutionView<TestdataMultipleListSolution> view,
                    Move<TestdataMultipleListSolution> candidate,
                    MoveEvaluationPipeline.EvaluationContext context) {
                  assertState(worker.getWorkingSolution());
                  assertThat(snapshot(worker.getWorkingSolution())).isEqualTo(snapshot(target));
                  evaluated.countDown();
                  if (cancel) {
                    try {
                      new CountDownLatch(1).await();
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                      throw new IllegalStateException("cancelled candidate", e);
                    }
                  }
                  throw new IllegalStateException("failed candidate");
                }

                @Override
                public void close() {
                  assertState(worker.getWorkingSolution());
                  closedStates.add(snapshot(worker.getWorkingSolution()));
                }
              });
      try {
        pipeline.start(director);
        pipeline.startNextStep(0);
        pipeline.submit(0, move, new ExpectedState(before, snapshot(target)));
        pipeline.flush();
        assertThat(evaluated.await(5, TimeUnit.SECONDS)).isTrue();
        if (!cancel) {
          assertThatThrownBy(pipeline::take)
              .isInstanceOf(IllegalStateException.class)
              .hasRootCauseMessage("failed candidate");
        }
        pipeline.abort();
        assertThat(closedStates).hasSize(2).containsOnly(before);
        assertThat(snapshot(director.getWorkingSolution())).isEqualTo(before);
        assertThat(director.calculateScore().raw())
            .isEqualTo(recompute(director.getWorkingSolution()));
      } finally {
        pipeline.abort();
      }
    }
  }

  private record ExpectedState(String before, String after)
      implements MoveEvaluationPipeline.EvaluationContext {}
}
