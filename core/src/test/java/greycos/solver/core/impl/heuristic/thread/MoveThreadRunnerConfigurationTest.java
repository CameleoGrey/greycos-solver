package greycos.solver.core.impl.heuristic.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.impl.constructionheuristic.decider.MultiThreadedConstructionHeuristicDecider;
import greycos.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.localsearch.decider.MultiThreadedLocalSearchDecider;
import greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.neighborhood.MoveSelectorBasedMoveRepository;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.api.Test;

class MoveThreadRunnerConfigurationTest {

  @Test
  void constructionHeuristicMoveThreadsRelyOnOriginSideDoabilityFiltering() {
    var solverScope = new SolverScope<Object>();
    solverScope.setScoreDirector(mock(InnerScoreDirector.class));
    var phaseScope = new ConstructionHeuristicPhaseScope<>(solverScope, 0);
    var pipeline = mockPipeline();
    var configuredPipeline = new AtomicReference<MoveEvaluationPipeline<Object>>();

    var decider =
        new MultiThreadedConstructionHeuristicDecider<>(
            "",
            PhaseTermination.bridge(new BasicPlumbingTermination<>(false)),
            mock(ConstructionHeuristicForager.class),
            runnable -> new Thread(runnable, "test-ch-move-thread"),
            2,
            20) {
          @Override
          protected ExecutorService createThreadPoolExecutor() {
            return mock(ExecutorService.class);
          }

          @Override
          protected MoveEvaluationPipeline<Object> createMoveEvaluationPipeline(int phaseIndex) {
            configuredPipeline.set(super.createMoveEvaluationPipeline(phaseIndex));
            return pipeline;
          }
        };

    decider.phaseStarted(phaseScope);
    assertThat(configuredPipeline.get().evaluateDoable).isFalse();
    verify(pipeline).start(phaseScope.getScoreDirector());
  }

  @Test
  void localSearchMoveThreadsPreFilterMoveDoability() {
    var solverScope = new SolverScope<Object>();
    solverScope.setScoreDirector(mock(InnerScoreDirector.class));
    var phaseScope = new LocalSearchPhaseScope<>(solverScope, 0);
    var pipeline = mockPipeline();
    var configuredPipeline = new AtomicReference<MoveEvaluationPipeline<Object>>();

    var decider =
        new MultiThreadedLocalSearchDecider<>(
            "",
            PhaseTermination.bridge(new BasicPlumbingTermination<>(false)),
            new MoveSelectorBasedMoveRepository<>(mock(MoveSelector.class)),
            mock(Acceptor.class),
            mock(LocalSearchForager.class),
            runnable -> new Thread(runnable, "test-ls-move-thread"),
            2,
            20) {
          @Override
          protected ExecutorService createThreadPoolExecutor() {
            return mock(ExecutorService.class);
          }

          @Override
          protected MoveEvaluationPipeline<Object> createMoveEvaluationPipeline(int phaseIndex) {
            configuredPipeline.set(super.createMoveEvaluationPipeline(phaseIndex));
            return pipeline;
          }
        };

    decider.phaseStarted(phaseScope);
    assertThat(configuredPipeline.get().evaluateDoable).isTrue();
    verify(pipeline).start(phaseScope.getScoreDirector());
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void pendingMoveCancelsCandidatesAndPublishesTheSelectedStep() {
    var scoreDirector = mock(InnerScoreDirector.class);
    var score = InnerScore.fullyAssigned(HardSoftScore.ZERO);
    when(scoreDirector.executeTemporaryMove(any(), anyBoolean())).thenReturn(score);
    Move<Object> pendingMove = mock(Move.class);
    var pipeline = mockPipeline();

    var solverScope = new SolverScope<Object>();
    solverScope.setScoreDirector(scoreDirector);
    solverScope.setPendingMove(pendingMove);
    var phaseScope = new LocalSearchPhaseScope<>(solverScope, 0);
    var stepScope = new LocalSearchStepScope<>(phaseScope, 0);

    var decider =
        new MultiThreadedLocalSearchDecider<>(
            "",
            PhaseTermination.bridge(new BasicPlumbingTermination<>(false)),
            new MoveSelectorBasedMoveRepository<>(mock(MoveSelector.class)),
            mock(Acceptor.class),
            mock(LocalSearchForager.class),
            runnable -> new Thread(runnable, "test-ls-move-thread"),
            2,
            20) {
          @Override
          protected ExecutorService createThreadPoolExecutor() {
            return mock(ExecutorService.class);
          }

          @Override
          protected MoveEvaluationPipeline<Object> createMoveEvaluationPipeline(int phaseIndex) {
            return pipeline;
          }
        };

    decider.phaseStarted(phaseScope);
    decider.decideNextStep(stepScope);

    var order = inOrder(pipeline);
    order.verify(pipeline).start(scoreDirector);
    order.verify(pipeline).startNextStep(0);
    order.verify(pipeline).cancelStep();
    order.verify(pipeline).applyStep(1, pendingMove, score);
    verify(pipeline, never()).submit(anyInt(), any());
    verify(pipeline, never()).close();
    verify(pipeline, never()).abort();
    assertThat(stepScope.getStep()).isSameAs(pendingMove);
    assertThat(stepScope.getScore()).isSameAs(score);
    assertThat(stepScope.getSelectedMoveCount()).isEqualTo(1);
    assertThat(stepScope.getAcceptedMoveCount()).isEqualTo(1);
  }

  @SuppressWarnings("unchecked")
  private static MoveEvaluationPipeline<Object> mockPipeline() {
    return mock(MoveEvaluationPipeline.class);
  }
}
