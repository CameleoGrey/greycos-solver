package ai.greycos.solver.core.impl.heuristic.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for MoveThreadRunner. Tests the core move thread functionality including operation
 * processing, exception handling, and thread lifecycle management.
 */
class MoveThreadRunnerTest {

  private String logIndentation = "    ";
  private int moveThreadIndex = 1;
  private boolean evaluateDoable = true;
  private BlockingQueue<MoveThreadOperation<TestSolution>> operationQueue;
  private OrderByMoveIndexBlockingQueue<TestSolution> resultQueue;
  private CyclicBarrier moveThreadBarrier;
  private boolean assertMoveScoreFromScratch = false;
  private boolean assertExpectedUndoMoveScore = false;
  private boolean assertStepScoreFromScratch = false;
  private boolean assertExpectedStepScore = false;
  private boolean assertShadowVariablesAreNotStaleAfterStep = false;

  private MoveThreadRunner<TestSolution, ?> moveThreadRunner;

  @BeforeEach
  void setUp() {
    operationQueue = new ArrayBlockingQueue<>(10);
    resultQueue = mock(OrderByMoveIndexBlockingQueue.class);
    moveThreadBarrier = mock(CyclicBarrier.class);

    moveThreadRunner =
        new MoveThreadRunner<>(
            logIndentation,
            moveThreadIndex,
            evaluateDoable,
            operationQueue,
            resultQueue,
            moveThreadBarrier,
            assertMoveScoreFromScratch,
            assertExpectedUndoMoveScore,
            assertStepScoreFromScratch,
            assertExpectedStepScore,
            assertShadowVariablesAreNotStaleAfterStep);
  }

  @Test
  void testMoveThreadRunnerCreation() {
    assertThat(moveThreadRunner).isNotNull();
    assertThat(moveThreadRunner.toString()).isEqualTo("MoveThreadRunner-1");
  }

  @Test
  void testSetupOperation() throws Exception {
    InnerScoreDirector<TestSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    Score<?> setupScore = mock(Score.class);

    when(scoreDirector.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD))
        .thenReturn(scoreDirector);
    when(scoreDirector.calculateScore()).thenReturn(setupScore);

    SetupOperation<TestSolution, ?> setupOperation = new SetupOperation<>(scoreDirector);
    operationQueue.put(setupOperation);

    // Start the thread runner in a separate thread
    Thread thread = new Thread(moveThreadRunner);
    thread.start();
    thread.join(1000); // Wait for completion

    verify(scoreDirector).createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
    verify(scoreDirector).calculateScore();
  }

  @Test
  void testDestroyOperation() throws Exception {
    InnerScoreDirector<TestSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    AtomicLong calculationCount = new AtomicLong(42);
    when(scoreDirector.getCalculationCount()).thenReturn(calculationCount.get());

    SetupOperation<TestSolution, ?> setupOperation = new SetupOperation<>(scoreDirector);
    DestroyOperation<TestSolution> destroyOperation = new DestroyOperation<>();

    operationQueue.put(setupOperation);
    operationQueue.put(destroyOperation);

    // Start the thread runner in a separate thread
    Thread thread = new Thread(moveThreadRunner);
    thread.start();
    thread.join(1000); // Wait for completion

    assertThat(moveThreadRunner.getCalculationCount()).isEqualTo(42);
  }

  @Test
  void testMoveEvaluationOperation() throws Exception {
    InnerScoreDirector<TestSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    Move<TestSolution> move = mock(Move.class);
    Score<?> moveScore = mock(Score.class);

    when(scoreDirector.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD))
        .thenReturn(scoreDirector);
    when(scoreDirector.calculateScore()).thenReturn(mock(Score.class));
    when(scoreDirector.executeTemporaryMove(any(Move.class), any(Boolean.class)))
        .thenReturn(moveScore);

    SetupOperation<TestSolution, ?> setupOperation = new SetupOperation<>(scoreDirector);
    MoveEvaluationOperation<TestSolution> moveEvalOperation =
        new MoveEvaluationOperation<>(0, 0, move);

    operationQueue.put(setupOperation);
    operationQueue.put(moveEvalOperation);

    // Start the thread runner in a separate thread
    Thread thread = new Thread(moveThreadRunner);
    thread.start();
    thread.join(1000); // Wait for completion

    verify(resultQueue).addMove(moveThreadIndex, 0, 0, move, moveScore);
  }

  @Test
  void testApplyStepOperation() throws Exception {
    InnerScoreDirector<TestSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    Move<TestSolution> step = mock(Move.class);
    Score<?> stepScore = mock(Score.class);

    when(scoreDirector.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD))
        .thenReturn(scoreDirector);
    when(scoreDirector.calculateScore()).thenReturn(mock(Score.class));

    SetupOperation<TestSolution, ?> setupOperation = new SetupOperation<>(scoreDirector);
    ApplyStepOperation<TestSolution, ?> applyStepOperation =
        new ApplyStepOperation<>(1, step, stepScore);

    operationQueue.put(setupOperation);
    operationQueue.put(applyStepOperation);

    // Start the thread runner in a separate thread
    Thread thread = new Thread(moveThreadRunner);
    thread.start();
    thread.join(1000); // Wait for completion

    verify(step).doMoveOnly(scoreDirector);
  }

  @Test
  void testExceptionHandling() throws Exception {
    InnerScoreDirector<TestSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    RuntimeException testException = new RuntimeException("Test exception");

    when(scoreDirector.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD))
        .thenThrow(testException);

    SetupOperation<TestSolution, ?> setupOperation = new SetupOperation<>(scoreDirector);

    operationQueue.put(setupOperation);

    // Start the thread runner in a separate thread
    Thread thread = new Thread(moveThreadRunner);
    thread.start();
    thread.join(1000); // Wait for completion

    verify(resultQueue).addExceptionThrown(moveThreadIndex, testException);
  }

  @Test
  void testScoreDirectorCloseOnException() throws Exception {
    InnerScoreDirector<TestSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    RuntimeException testException = new RuntimeException("Test exception");

    when(scoreDirector.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD))
        .thenThrow(testException);

    SetupOperation<TestSolution, ?> setupOperation = new SetupOperation<>(scoreDirector);

    operationQueue.put(setupOperation);

    // Start the thread runner in a separate thread
    Thread thread = new Thread(moveThreadRunner);
    thread.start();
    thread.join(1000); // Wait for completion

    verify(scoreDirector).close();
  }

  @Test
  void testScoreDirectorCloseOnNormalCompletion() throws Exception {
    InnerScoreDirector<TestSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    Score<?> setupScore = mock(Score.class);

    when(scoreDirector.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD))
        .thenReturn(scoreDirector);
    when(scoreDirector.calculateScore()).thenReturn(setupScore);

    SetupOperation<TestSolution, ?> setupOperation = new SetupOperation<>(scoreDirector);
    DestroyOperation<TestSolution> destroyOperation = new DestroyOperation<>();

    operationQueue.put(setupOperation);
    operationQueue.put(destroyOperation);

    // Start the thread runner in a separate thread
    Thread thread = new Thread(moveThreadRunner);
    thread.start();
    thread.join(1000); // Wait for completion

    verify(scoreDirector).close();
  }

  @Test
  void testScoreDirectorCloseOnInterrupt() throws Exception {
    InnerScoreDirector<TestSolution, ?> scoreDirector = mock(InnerScoreDirector.class);

    when(scoreDirector.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD))
        .thenReturn(scoreDirector);
    when(scoreDirector.calculateScore()).thenReturn(mock(Score.class));

    SetupOperation<TestSolution, ?> setupOperation = new SetupOperation<>(scoreDirector);

    operationQueue.put(setupOperation);

    // Start the thread runner in a separate thread
    Thread thread = new Thread(moveThreadRunner);
    thread.start();

    // Interrupt the thread
    thread.interrupt();
    thread.join(1000); // Wait for completion

    verify(scoreDirector).close();
  }

  @Test
  void testGetCalculationCountBeforeDestroy() {
    assertThat(moveThreadRunner.getCalculationCount()).isEqualTo(0L);
  }

  @Test
  void testGetCalculationCountAfterDestroy() throws Exception {
    InnerScoreDirector<TestSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    AtomicLong calculationCount = new AtomicLong(123);
    when(scoreDirector.getCalculationCount()).thenReturn(calculationCount.get());

    SetupOperation<TestSolution, ?> setupOperation = new SetupOperation<>(scoreDirector);
    DestroyOperation<TestSolution> destroyOperation = new DestroyOperation<>();

    operationQueue.put(setupOperation);
    operationQueue.put(destroyOperation);

    // Start the thread runner in a separate thread
    Thread thread = new Thread(moveThreadRunner);
    thread.start();
    thread.join(1000); // Wait for completion

    assertThat(moveThreadRunner.getCalculationCount()).isEqualTo(123);
  }

  @Test
  void testMultipleOperations() throws Exception {
    InnerScoreDirector<TestSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    Move<TestSolution> move1 = mock(Move.class);
    Move<TestSolution> move2 = mock(Move.class);
    Score<?> moveScore1 = mock(Score.class);
    Score<?> moveScore2 = mock(Score.class);

    when(scoreDirector.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD))
        .thenReturn(scoreDirector);
    when(scoreDirector.calculateScore()).thenReturn(mock(Score.class));
    when(scoreDirector.executeTemporaryMove(any(Move.class), any(Boolean.class)))
        .thenReturn(moveScore1)
        .thenReturn(moveScore2);

    SetupOperation<TestSolution, ?> setupOperation = new SetupOperation<>(scoreDirector);
    MoveEvaluationOperation<TestSolution> moveEvalOperation1 =
        new MoveEvaluationOperation<>(0, 0, move1);
    MoveEvaluationOperation<TestSolution> moveEvalOperation2 =
        new MoveEvaluationOperation<>(0, 1, move2);
    DestroyOperation<TestSolution> destroyOperation = new DestroyOperation<>();

    operationQueue.put(setupOperation);
    operationQueue.put(moveEvalOperation1);
    operationQueue.put(moveEvalOperation2);
    operationQueue.put(destroyOperation);

    // Start the thread runner in a separate thread
    Thread thread = new Thread(moveThreadRunner);
    thread.start();
    thread.join(1000); // Wait for completion

    ArgumentCaptor<Move<TestSolution>> moveCaptor = ArgumentCaptor.forClass(Move.class);
    verify(resultQueue, times(2))
        .addMove(moveThreadIndex, 0, 0, moveCaptor.capture(), any(Score.class));
  }

  // Mock classes for testing
  private static class TestSolution {
    // Mock solution class
  }
}
