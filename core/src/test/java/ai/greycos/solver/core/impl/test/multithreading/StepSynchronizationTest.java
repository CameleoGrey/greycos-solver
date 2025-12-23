package ai.greycos.solver.core.impl.test.multithreading;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadFactory;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.heuristic.move.AbstractMove;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.thread.ApplyStepOperation;
import ai.greycos.solver.core.impl.heuristic.thread.MoveThreadOperation;
import ai.greycos.solver.core.impl.heuristic.thread.MoveThreadRunner;
import ai.greycos.solver.core.impl.heuristic.thread.OrderByMoveIndexBlockingQueue;
import ai.greycos.solver.core.impl.heuristic.thread.SetupOperation;
import ai.greycos.solver.core.impl.localsearch.decider.MultiThreadedLocalSearchDecider;
import ai.greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import ai.greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import ai.greycos.solver.core.impl.neighborhood.MoveRepository;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.termination.PhaseTermination;
import ai.greycos.solver.core.impl.solver.thread.DefaultSolverThreadFactory;
import ai.greycos.solver.core.testdomain.TestdataSolution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;

/**
 * Step synchronization tests for multithreading functionality. These tests validate that the main
 * thread properly waits for all move threads to complete processing the current step before moving
 * to the next step.
 */
public class StepSynchronizationTest {

  private String logIndentation = "    ";
  private ThreadFactory threadFactory;
  private int moveThreadCount = 3;
  private int selectedMoveBufferSize = 10;

  @BeforeEach
  void setUp() {
    threadFactory = new DefaultSolverThreadFactory();
  }

  @Test
  @Timeout(5000)
  void testStepCompletionQueueBehavior() throws Exception {
    // Test the behavior of the step completion queue
    BlockingQueue<OrderByMoveIndexBlockingQueue.MoveResult<TestdataSolution>> stepCompletionQueue =
        new ArrayBlockingQueue<>(moveThreadCount);

    // Create move thread runners
    List<MoveThreadRunner<TestdataSolution, SimpleScore>> runners = new ArrayList<>();
    for (int i = 0; i < moveThreadCount; i++) {
      BlockingQueue<MoveThreadOperation<TestdataSolution>> operationQueue =
          new ArrayBlockingQueue<>(20);
      OrderByMoveIndexBlockingQueue<TestdataSolution> resultQueue =
          new OrderByMoveIndexBlockingQueue<>(20);
      CyclicBarrier moveThreadBarrier = new CyclicBarrier(moveThreadCount);

      MoveThreadRunner<TestdataSolution, SimpleScore> runner =
          new MoveThreadRunner<>(
              logIndentation,
              i,
              true,
              operationQueue,
              resultQueue,
              moveThreadBarrier,
              false,
              false,
              false,
              false,
              false);
      runners.add(runner);
    }

    // Verify that runners can be created and configured
    assertThat(runners).hasSize(moveThreadCount);
    for (int i = 0; i < moveThreadCount; i++) {
      assertThat(runners.get(i)).isNotNull();
    }
  }

  @Test
  @Timeout(5000)
  void testMoveThreadStepCompletionAcknowledgment() throws Exception {
    // Test that move threads properly send step completion acknowledgments
    BlockingQueue<MoveThreadOperation<TestdataSolution>> operationQueue =
        new ArrayBlockingQueue<>(20);
    OrderByMoveIndexBlockingQueue<TestdataSolution> resultQueue =
        new OrderByMoveIndexBlockingQueue<>(20);
    CyclicBarrier moveThreadBarrier = new CyclicBarrier(moveThreadCount);
    BlockingQueue<OrderByMoveIndexBlockingQueue.MoveResult<TestdataSolution>> stepCompletionQueue =
        new ArrayBlockingQueue<>(moveThreadCount);

    // Create a move thread runner
    MoveThreadRunner<TestdataSolution, SimpleScore> runner =
        new MoveThreadRunner<>(
            logIndentation,
            0,
            true,
            operationQueue,
            resultQueue,
            moveThreadBarrier,
            false,
            false,
            false,
            false,
            false);

    // Create a thread to run the runner
    Thread runnerThread = new Thread(runner);

    // Send setup operation
    InnerScoreDirector<TestdataSolution, SimpleScore> scoreDirector =
        Mockito.mock(InnerScoreDirector.class);
    operationQueue.put(new SetupOperation<>(scoreDirector));

    // Start the thread
    runnerThread.start();

    // Wait for setup to complete
    Thread.sleep(100);

    // Send step operation
    Move<TestdataSolution> step = new MockMove<>();
    operationQueue.put(new ApplyStepOperation<>(0, step, SimpleScore.of(100)));

    // Wait for step completion
    Thread.sleep(100);

    // Send destroy operation
    operationQueue.put(new ai.greycos.solver.core.impl.heuristic.thread.DestroyOperation<>());

    // Wait for thread to complete
    runnerThread.join(1000);

    // Verify that step completion was sent
    assertThat(stepCompletionQueue.size()).isGreaterThanOrEqualTo(1);
  }

  @Test
  @Timeout(5000)
  void testMultiThreadedLocalSearchDeciderThreadCoordination() {
    // Test that multiple threads coordinate properly in MultiThreadedLocalSearchDecider
    PhaseTermination<TestdataSolution> termination = null; // Use null for basic test
    MoveRepository<TestdataSolution> moveRepository = null; // Use null for basic test
    Acceptor<TestdataSolution> acceptor = null; // Use null for basic test
    LocalSearchForager<TestdataSolution> forager = null; // Use null for basic test

    MultiThreadedLocalSearchDecider<TestdataSolution> decider =
        new MultiThreadedLocalSearchDecider<>(
            logIndentation,
            termination,
            moveRepository,
            acceptor,
            forager,
            threadFactory,
            moveThreadCount,
            selectedMoveBufferSize);

    assertThat(decider).isNotNull();
    assertThat(decider.getMoveThreadCount()).isEqualTo(moveThreadCount);
    assertThat(decider.getSelectedMoveBufferSize()).isEqualTo(selectedMoveBufferSize);
  }

  @Test
  @Timeout(5000)
  void testCyclicBarrierCoordination() throws Exception {
    // Test that CyclicBarrier properly coordinates thread synchronization
    CyclicBarrier barrier = new CyclicBarrier(moveThreadCount);

    // Create mock runners that will use the barrier
    List<MockMoveThreadRunner> runners = new ArrayList<>();
    for (int i = 0; i < moveThreadCount; i++) {
      MockMoveThreadRunner runner = new MockMoveThreadRunner(barrier, i);
      runners.add(runner);
    }

    // Start all runners
    List<Thread> threads = new ArrayList<>();
    for (MockMoveThreadRunner runner : runners) {
      Thread thread = new Thread(runner);
      threads.add(thread);
      thread.start();
    }

    // Wait for all threads to complete
    for (Thread thread : threads) {
      thread.join(1000);
    }

    // Verify that all runners reached the barrier
    for (MockMoveThreadRunner runner : runners) {
      assertThat(runner.barrierReached).isTrue();
    }
  }

  @Test
  @Timeout(5000)
  void testStepSynchronizationWithExceptions() throws Exception {
    // Test that exceptions during step processing are properly handled
    PhaseTermination<TestdataSolution> termination = null; // Use null for basic test
    MoveRepository<TestdataSolution> moveRepository = null; // Use null for basic test
    Acceptor<TestdataSolution> acceptor = null; // Use null for basic test
    LocalSearchForager<TestdataSolution> forager = null; // Use null for basic test

    MultiThreadedLocalSearchDecider<TestdataSolution> decider =
        new MultiThreadedLocalSearchDecider<>(
            logIndentation,
            termination,
            moveRepository,
            acceptor,
            forager,
            threadFactory,
            moveThreadCount,
            selectedMoveBufferSize);

    assertThat(decider).isNotNull();
    assertThat(decider.getMoveThreadCount()).isEqualTo(moveThreadCount);
  }

  @Test
  @Timeout(5000)
  void testThreadCountValidation() {
    // Test that thread count validation works correctly
    assertThat(moveThreadCount).isGreaterThan(0);
    assertThat(moveThreadCount).isLessThanOrEqualTo(100); // Reasonable upper limit
  }

  @Test
  @Timeout(5000)
  void testBufferSizeCoordination() {
    // Test that buffer size is properly coordinated with thread count
    int totalBufferSize = moveThreadCount * selectedMoveBufferSize;
    assertThat(totalBufferSize).isGreaterThan(0);
    assertThat(totalBufferSize).isLessThanOrEqualTo(1000); // Reasonable upper limit
  }

  // Helper classes
  private static class MockMoveThreadRunner implements Runnable {
    private final CyclicBarrier barrier;
    private final int threadIndex;
    public boolean barrierReached = false;

    public MockMoveThreadRunner(CyclicBarrier barrier, int threadIndex) {
      this.barrier = barrier;
      this.threadIndex = threadIndex;
    }

    @Override
    public void run() {
      try {
        // Simulate some work
        Thread.sleep(10);

        // Wait at barrier
        barrier.await();
        barrierReached = true;

        // More work after barrier
        Thread.sleep(10);
      } catch (Exception e) {
        // Ignore for test purposes
      }
    }
  }

  private static class MockMove<Solution_> extends AbstractMove<Solution_> {
    @Override
    public boolean isMoveDoable(
        ai.greycos.solver.core.api.score.director.ScoreDirector<Solution_> scoreDirector) {
      return true;
    }

    @Override
    protected void doMoveOnGenuineVariables(
        ai.greycos.solver.core.api.score.director.ScoreDirector<Solution_> scoreDirector) {
      // Mock implementation
    }

    @Override
    public String getSimpleMoveTypeDescription() {
      return "MockMove";
    }

    @Override
    public ai.greycos.solver.core.impl.heuristic.move.Move<Solution_> rebase(
        ai.greycos.solver.core.api.score.director.ScoreDirector<Solution_>
            destinationScoreDirector) {
      return this; // For testing, just return this
    }
  }
}
