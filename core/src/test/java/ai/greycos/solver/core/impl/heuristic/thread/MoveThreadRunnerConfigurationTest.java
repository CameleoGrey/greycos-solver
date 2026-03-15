package ai.greycos.solver.core.impl.heuristic.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

import ai.greycos.solver.core.api.score.HardSoftScore;
import ai.greycos.solver.core.impl.constructionheuristic.decider.MultiThreadedConstructionHeuristicDecider;
import ai.greycos.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;
import ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import ai.greycos.solver.core.impl.localsearch.decider.MultiThreadedLocalSearchDecider;
import ai.greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import ai.greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.greycos.solver.core.impl.neighborhood.MoveSelectorBasedMoveRepository;
import ai.greycos.solver.core.impl.score.director.InnerScore;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import ai.greycos.solver.core.impl.solver.termination.PhaseTermination;

import org.junit.jupiter.api.Test;

class MoveThreadRunnerConfigurationTest {

  @Test
  void constructionHeuristicMoveThreadsDoNotPreFilterMoveDoability() throws Exception {
    var solverScope = new SolverScope<Object>();
    solverScope.setScoreDirector(mock(InnerScoreDirector.class));
    var phaseScope = new ConstructionHeuristicPhaseScope<>(solverScope, 0);

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
        };

    decider.phaseStarted(phaseScope);
    var runnerList = readRunnerList(decider);

    assertThat(runnerList).hasSize(2);
    for (var runner : runnerList) {
      assertThat(readEvaluateDoable(runner)).isFalse();
    }
  }

  @Test
  void localSearchMoveThreadsPreFilterMoveDoability() throws Exception {
    var solverScope = new SolverScope<Object>();
    solverScope.setScoreDirector(mock(InnerScoreDirector.class));
    var phaseScope = new LocalSearchPhaseScope<>(solverScope, 0);

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
        };

    decider.phaseStarted(phaseScope);
    var runnerList = readRunnerList(decider);

    assertThat(runnerList).hasSize(2);
    for (var runner : runnerList) {
      assertThat(readEvaluateDoable(runner)).isTrue();
    }
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void localSearchCleanupKeepsNonMoveOperationsForPendingMoveStep() throws Exception {
    var scoreDirector = mock(InnerScoreDirector.class);
    when(scoreDirector.executeTemporaryMove(any(), anyBoolean()))
        .thenReturn(InnerScore.fullyAssigned(HardSoftScore.ZERO));
    ai.greycos.solver.core.preview.api.move.Move<Object> pendingMove =
        mock(ai.greycos.solver.core.preview.api.move.Move.class);

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
        };

    BlockingQueue<MoveThreadOperation<Object>> operationQueue = new ArrayBlockingQueue<>(10);
    Move<Object> legacyMove = mock(Move.class);
    operationQueue.add(new ApplyStepOperation<>(7, legacyMove, HardSoftScore.ZERO));
    operationQueue.add(new MoveEvaluationOperation<>(0, 0, legacyMove));
    operationQueue.add(new DestroyOperation<>());

    var operationQueueField =
        MultiThreadedLocalSearchDecider.class.getDeclaredField("operationQueue");
    operationQueueField.setAccessible(true);
    operationQueueField.set(decider, operationQueue);

    var resultQueueField = MultiThreadedLocalSearchDecider.class.getDeclaredField("resultQueue");
    resultQueueField.setAccessible(true);
    resultQueueField.set(decider, new OrderByMoveIndexBlockingQueue<>(10));

    decider.decideNextStep(stepScope);

    assertThat(operationQueue).hasSize(4);
    assertThat(operationQueue).anyMatch(op -> op instanceof DestroyOperation);
    assertThat(operationQueue).noneMatch(op -> op instanceof MoveEvaluationOperation);
  }

  @SuppressWarnings("unchecked")
  private static List<MoveThreadRunner<Object, ?>> readRunnerList(Object decider) throws Exception {
    var field = decider.getClass().getSuperclass().getDeclaredField("moveThreadRunnerList");
    field.setAccessible(true);
    return (List<MoveThreadRunner<Object, ?>>) field.get(decider);
  }

  private static boolean readEvaluateDoable(MoveThreadRunner<?, ?> runner) throws Exception {
    Field field = MoveThreadRunner.class.getDeclaredField("evaluateDoable");
    field.setAccessible(true);
    return (boolean) field.get(runner);
  }
}
