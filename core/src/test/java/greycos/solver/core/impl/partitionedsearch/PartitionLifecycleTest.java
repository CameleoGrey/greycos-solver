package greycos.solver.core.impl.partitionedsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.thread.ThreadUtils;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataSolutionPartitioner;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(15)
class PartitionLifecycleTest {

  private static final List<Thread> WORKERS = new CopyOnWriteArrayList<>();

  @BeforeEach
  void resetWorkers() {
    WORKERS.clear();
  }

  @AfterEach
  void ensureWorkersStopped() throws Exception {
    try {
      for (var worker : WORKERS) {
        worker.join(3_000);
        assertThat(worker.isAlive()).as("Partition worker %s stopped", worker.getName()).isFalse();
      }
    } finally {
      for (var worker : WORKERS) {
        worker.interrupt();
      }
      for (var worker : WORKERS) {
        worker.join(3_000);
      }
      WORKERS.clear();
    }
  }

  @Test
  void interruptedChildStillCompletesItsFuture() throws Exception {
    var ran = new CountDownLatch(1);
    var solver =
        solver(
            context -> {
              ran.countDown();
              Thread.currentThread().interrupt();
            },
            RecordingThreadFactory.class);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var result = executor.submit(() -> solver.solve(TestdataSolution.generateSolution(3, 2)));
      assertThat(ran.await(3, TimeUnit.SECONDS)).isTrue();
      assertThat(result.get(3, TimeUnit.SECONDS)).isNotNull();
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void terminateEarlyWakesParentWithoutImprovements() throws Exception {
    var started = new CountDownLatch(2);
    var solver =
        solver(
            context -> {
              started.countDown();
              while (!context.isPhaseTerminated()) {
                Thread.onSpinWait();
              }
            },
            RecordingThreadFactory.class);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var result = executor.submit(() -> solver.solve(TestdataSolution.generateSolution(3, 2)));
      assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
      assertThat(solver.terminateEarly()).isTrue();
      assertThat(result.get(3, TimeUnit.SECONDS)).isNotNull();
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void cooperativeTerminationMergesImprovementsPublishedWhileWorkersStop() throws Exception {
    var started = new CountDownLatch(2);
    var observedTermination = new CountDownLatch(2);
    var finishCommands = new CountDownLatch(1);
    var parent = new AtomicReference<Thread>();
    var solver =
        solver(
            context -> {
              started.countDown();
              while (!context.isPhaseTerminated()) {
                Thread.onSpinWait();
              }
              observedTermination.countDown();
              awaitUninterruptibly(finishCommands);
              var solution = context.getWorkingSolution();
              var entity = solution.getEntityList().getFirst();
              var variable =
                  context
                      .getSolutionMetaModel()
                      .genuineEntity(TestdataEntity.class)
                      .basicVariable("value", TestdataValue.class);
              context.executeAndCalculateScore(
                  Moves.change(
                      variable,
                      entity,
                      solution.getValueList().get(entity.getCode().endsWith("0") ? 0 : 1)));
            },
            RecordingThreadFactory.class);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var result =
          executor.submit(
              () -> {
                parent.set(Thread.currentThread());
                return solver.solve(TestdataSolution.generateUninitializedSolution(3, 2));
              });
      assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
      solver.terminateEarly();
      assertThat(observedTermination.await(3, TimeUnit.SECONDS)).isTrue();
      // Hold both final publications until the parent has entered worker shutdown. This prevents
      // the old close-before-shutdown implementation from passing by consuming an earlier event.
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while (Arrays.stream(parent.get().getStackTrace())
          .noneMatch(frame -> frame.getMethodName().equals("stopAndAwaitPartitions"))) {
        assertThat(System.nanoTime()).as("Parent entered worker shutdown").isLessThan(deadline);
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
      }
      finishCommands.countDown();
      var solution = result.get(3, TimeUnit.SECONDS);
      assertThat(solution.getEntityList())
          .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
    } finally {
      finishCommands.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void childFailureDuringCooperativeCleanupIsStillObserved() throws Exception {
    var started = new CountDownLatch(2);
    var original = new IllegalStateException("Partition failed while stopping.");
    var solver =
        solver(
            context -> {
              started.countDown();
              while (!context.isPhaseTerminated()) {
                Thread.onSpinWait();
              }
              if (context.getWorkingSolution().getEntityList().getFirst().getCode().endsWith("0")) {
                throw original;
              }
            },
            RecordingThreadFactory.class);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var result = executor.submit(() -> solver.solve(TestdataSolution.generateSolution(3, 2)));
      assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
      solver.terminateEarly();
      assertThatThrownBy(() -> result.get(3, TimeUnit.SECONDS))
          .cause()
          .hasMessageContaining("partIndex (0)")
          .hasCause(original);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void timedOutWorkerPreventsSolverReuseUntilItActuallyStops() throws Exception {
    var running = new CountDownLatch(1);
    var releaseWorker = new CountDownLatch(1);
    var failOnce = new AtomicBoolean(true);
    var original = new IllegalStateException("First partition failed.");
    var solver =
        (DefaultSolver<TestdataSolution>)
            solver(
                context -> {
                  if (context
                      .getWorkingSolution()
                      .getEntityList()
                      .getFirst()
                      .getCode()
                      .endsWith("0")) {
                    if (failOnce.compareAndSet(true, false)) {
                      awaitUninterruptibly(running);
                      throw original;
                    }
                  } else {
                    running.countDown();
                    awaitUninterruptibly(releaseWorker);
                  }
                },
                RecordingThreadFactory.class);
    var executor = Executors.newSingleThreadExecutor();
    int originalTimeout = ThreadUtils.getDefaultShutdownTimeout();
    ThreadUtils.setDefaultShutdownTimeout(1);
    try {
      var result = executor.submit(() -> solver.solve(TestdataSolution.generateSolution(3, 2)));
      var thrown = catchThrowable(() -> result.get(5, TimeUnit.SECONDS));
      assertThat(thrown).cause().hasCause(original);
      assertThat(thrown.getCause().getSuppressed())
          .anySatisfy(
              suppressed -> assertThat(suppressed).hasMessageContaining("workers did not stop"));
      assertThat(WORKERS).anyMatch(Thread::isAlive);
      var previousBest = solver.getSolverScope().getBestSolution();
      assertThatThrownBy(() -> solver.solve(TestdataSolution.generateSolution(3, 2)))
          .hasMessageContaining("previous solve")
          .hasMessageContaining("Partitioned Search");
      assertThat(solver.getSolverScope().getBestSolution()).isSameAs(previousBest);

      releaseWorker.countDown();
      for (var worker : WORKERS) {
        worker.join(3_000);
        assertThat(worker.isAlive()).isFalse();
      }
      var nextResult = executor.submit(() -> solver.solve(TestdataSolution.generateSolution(3, 2)));
      assertThat(nextResult.get(3, TimeUnit.SECONDS)).isNotNull();
    } finally {
      releaseWorker.countDown();
      ThreadUtils.setDefaultShutdownTimeout(originalTimeout);
      executor.shutdownNow();
      assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void childFailureCancelsTheOtherChildAndRelaysOriginalCause() throws Exception {
    var running = new CountDownLatch(1);
    var original = new IllegalStateException("Partition command failed.");
    var solver =
        solver(
            context -> {
              if (context.getWorkingSolution().getEntityList().getFirst().getCode().endsWith("0")) {
                try {
                  if (!running.await(3, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("The other partition did not start.");
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(e);
                }
                throw original;
              }
              running.countDown();
              while (!context.isPhaseTerminated()) {
                Thread.onSpinWait();
              }
            },
            RecordingThreadFactory.class);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var result = executor.submit(() -> solver.solve(TestdataSolution.generateSolution(3, 2)));
      assertThatThrownBy(() -> result.get(3, TimeUnit.SECONDS))
          .cause()
          .hasMessageContaining("partIndex (0)")
          .hasCause(original);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void parentMergeFailurePreservesOriginalCauseAndStopsWorkers() throws Exception {
    var config = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    config.withThreadFactoryClass(RecordingThreadFactory.class);
    var partition = new PartitionedSearchPhaseConfig();
    partition.setSolutionPartitionerClass(TestdataSolutionPartitioner.class);
    partition.setRunnablePartThreadLimit(
        PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_UNLIMITED);
    partition.setPhaseConfigList(
        List.of(new ConstructionHeuristicPhaseConfig(), new LocalSearchPhaseConfig()));
    config.withPhases(partition);
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var original = new AssertionError("Parent merge listener failed.");
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> stepScope) {
            throw original;
          }
        });
    var executor = Executors.newSingleThreadExecutor();
    try {
      var result =
          executor.submit(() -> solver.solve(TestdataSolution.generateUninitializedSolution(3, 2)));
      assertThatThrownBy(() -> result.get(3, TimeUnit.SECONDS)).hasCause(original);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void nestedCoordinatorsYieldTheOuterRunnablePermit(boolean island) throws Exception {
    var entered = new CountDownLatch(2);
    PhaseCommand<TestdataSolution> command =
        context -> {
          entered.countDown();
          try {
            if (!entered.await(3, TimeUnit.SECONDS)) {
              throw new IllegalStateException("An outer partition retained its runnable permit.");
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
        };
    PhaseConfig inner;
    if (island) {
      inner =
          new IslandModelPhaseConfig()
              .withIslandCount(1)
              .withPhaseConfigList(
                  List.of(new CustomPhaseConfig().withCustomPhaseCommands(command)));
    } else {
      var innerPartition = new PartitionedSearchPhaseConfig();
      innerPartition.setSolutionPartitionerClass(TestdataSolutionPartitioner.class);
      innerPartition.setRunnablePartThreadLimit(
          PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_UNLIMITED);
      innerPartition.setPhaseConfigList(
          List.of(new CustomPhaseConfig().withCustomPhaseCommands(command)));
      inner = innerPartition;
    }
    var outer = new PartitionedSearchPhaseConfig();
    outer.setSolutionPartitionerClass(TestdataSolutionPartitioner.class);
    outer.setRunnablePartThreadLimit("1");
    outer.setPhaseConfigList(List.of(inner));
    var config = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    config.withThreadFactoryClass(RecordingThreadFactory.class).withPhases(outer);
    var solver = SolverFactory.<TestdataSolution>create(config).buildSolver();
    var executor = Executors.newSingleThreadExecutor();
    try {
      var result = executor.submit(() -> solver.solve(TestdataSolution.generateSolution(3, 2)));
      assertThat(result.get(5, TimeUnit.SECONDS)).isNotNull();
      assertThat(entered.getCount()).isZero();
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void nullThreadFactoryFailsAndStopsAlreadyStartedWorkers() throws Exception {
    assertThreadFactoryFailure(NullSecondThreadFactory.class, "returned null");
  }

  @Test
  void throwingThreadFactoryStopsAlreadyStartedWorkers() throws Exception {
    assertThreadFactoryFailure(ThrowingSecondThreadFactory.class, "Thread creation failed.");
  }

  private void assertThreadFactoryFailure(Class<? extends ThreadFactory> factory, String message)
      throws Exception {
    var solver =
        solver(
            context -> {
              while (!context.isPhaseTerminated()) {
                Thread.onSpinWait();
              }
            },
            factory);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var result = executor.submit(() -> solver.solve(TestdataSolution.generateSolution(3, 2)));
      assertThatThrownBy(() -> result.get(3, TimeUnit.SECONDS))
          .cause()
          .hasMessageContaining(message);
      assertThat(WORKERS).hasSize(1);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void parentInterruptionStopsWorkersAndPreservesTheInterruptFlag() throws Exception {
    var started = new CountDownLatch(2);
    var solver =
        solver(
            context -> {
              started.countDown();
              while (!context.isPhaseTerminated()) {
                Thread.onSpinWait();
              }
            },
            RecordingThreadFactory.class);
    var failure = new AtomicReference<Throwable>();
    var interrupted = new AtomicBoolean();
    var parent =
        new Thread(
            () -> {
              try {
                solver.solve(TestdataSolution.generateSolution(3, 2));
              } catch (Throwable thrown) {
                failure.set(thrown);
              } finally {
                interrupted.set(Thread.currentThread().isInterrupted());
              }
            },
            "partition-test-parent");
    parent.start();
    try {
      assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
      parent.interrupt();
      parent.join(3_000);
      assertThat(parent.isAlive()).isFalse();
      assertThat(failure.get())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("interrupted");
      assertThat(interrupted).isTrue();
    } finally {
      parent.interrupt();
      parent.join(3_000);
    }
  }

  private Solver<TestdataSolution> solver(
      PhaseCommand<TestdataSolution> command, Class<? extends ThreadFactory> threadFactoryClass) {
    SolverConfig config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    config.withThreadFactoryClass(threadFactoryClass);
    var partition = new PartitionedSearchPhaseConfig();
    partition.setSolutionPartitionerClass(TestdataSolutionPartitioner.class);
    partition.setRunnablePartThreadLimit(
        PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_UNLIMITED);
    partition.setPhaseConfigList(List.of(new CustomPhaseConfig().withCustomPhaseCommands(command)));
    config.withPhases(partition);
    return SolverFactory.<TestdataSolution>create(config).buildSolver();
  }

  private static void awaitUninterruptibly(CountDownLatch gate) {
    boolean interrupted = false;
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    try {
      while (true) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) {
          throw new IllegalStateException("The test did not release its partition worker.");
        }
        try {
          if (gate.await(remaining, TimeUnit.NANOSECONDS)) {
            return;
          }
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public static class RecordingThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable runnable) {
      var thread = new Thread(runnable, "partition-test-worker-" + WORKERS.size());
      thread.setDaemon(true);
      WORKERS.add(thread);
      return thread;
    }
  }

  public static class NullSecondThreadFactory extends RecordingThreadFactory {
    @Override
    public Thread newThread(Runnable runnable) {
      return WORKERS.isEmpty() ? super.newThread(runnable) : null;
    }
  }

  public static class ThrowingSecondThreadFactory extends RecordingThreadFactory {
    @Override
    public Thread newThread(Runnable runnable) {
      if (!WORKERS.isEmpty()) {
        throw new IllegalStateException("Thread creation failed.");
      }
      return super.newThread(runnable);
    }
  }
}
