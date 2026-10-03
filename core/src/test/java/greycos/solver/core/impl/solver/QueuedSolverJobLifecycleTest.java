package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.SolverConfigOverride;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.SolverJob;
import greycos.solver.core.api.solver.SolverStatus;
import greycos.solver.core.api.solver.change.ProblemChange;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.SolverManagerConfig;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class QueuedSolverJobLifecycleTest {

  @Test
  void queuedCancellationCompletesChangesOutsideLocksAndBeforeReentrantFinalGetter()
      throws Exception {
    try (var fixture = new BlockedFinder(false)) {
      var input = problem("queued");
      var finderCalls = new AtomicInteger();
      var job =
          (DefaultSolverJob<TestdataSolution>)
              fixture
                  .manager
                  .solveBuilder()
                  .withProblemId("queued")
                  .withProblemFinder(
                      id -> {
                        finderCalls.incrementAndGet();
                        return input;
                      })
                  .run();
      var change = job.addProblemChange((solution, director) -> {});
      var continuation =
          change.handle(
              (ignored, failure) -> {
                assertThat(failure).isInstanceOf(CancellationException.class);
                // This would block forever if the final future were canceled after change futures.
                assertThat(finalSolution(job)).isSameAs(input);
                // Reenter from another thread to detect future completion under either job lock.
                runBounded(job::terminateEarly);
                assertThatThrownBy(() -> job.addProblemChange((solution, director) -> {}))
                    .isInstanceOf(IllegalStateException.class);
                return null;
              });

      runBounded(job::terminateEarly);
      continuation.get(5, TimeUnit.SECONDS);
      assertThat(change).isCancelled();
      assertThat(job.getSolverStatus()).isEqualTo(SolverStatus.NOT_SOLVING);
      assertThat(fixture.manager.getSolverStatus("queued")).isEqualTo(SolverStatus.NOT_SOLVING);
      assertThat(job.isTerminatedEarly()).isTrue();
      assertThat(finderCalls).hasValue(1); // Only the canceled-input getter above called it.
      assertThatThrownBy(job::call).isInstanceOf(CancellationException.class);
      assertThat(finderCalls).hasValue(1);

      var replacement = fixture.manager.solve("queued", problem("replacement"));
      job.close();
      fixture.manager.unregisterSolverJob("queued", job);
      assertThat(replacement.getSolverStatus()).isEqualTo(SolverStatus.SOLVING_SCHEDULED);
      assertThat(fixture.manager.getSolverStatus("queued"))
          .isEqualTo(SolverStatus.SOLVING_SCHEDULED);
      replacement.terminateEarly();
    }
  }

  @Test
  void activeTerminationClosesAdmissionWhileAcceptedChangesAwaitConsumerDelivery()
      throws Exception {
    var phaseEntered = new CountDownLatch(1);
    var releasePhase = new CountDownLatch(1);
    var terminationReturned = new CountDownLatch(1);
    var releaseConsumer = new CountDownLatch(1);
    var jobReference = new AtomicReference<SolverJob<TestdataSolution>>();
    var consumerClaimed = new AtomicBoolean();
    var failureReference = new AtomicReference<Throwable>();
    var solverConfig =
        config(true)
            .withPhases(
                new ConstructionHeuristicPhaseConfig(),
                new CustomPhaseConfig()
                    .withCustomPhaseCommands(
                        (PhaseCommand<TestdataSolution>)
                            context -> {
                              if (context.getWorkingSolution().getValueList().size() == 3) {
                                phaseEntered.countDown();
                                await(releasePhase);
                              }
                            }));
    try (var manager =
        new DefaultSolverManager<>(
            SolverFactory.<TestdataSolution>create(solverConfig), managerConfig())) {
      var job =
          manager
              .solveBuilder()
              .withProblemId("active-drain")
              .withProblem(problem("active-drain"))
              .withBestSolutionEventConsumer(
                  event -> {
                    if (event.solution().getValueList().size() == 3
                        && consumerClaimed.compareAndSet(false, true)) {
                      await(phaseEntered);
                      // Consumer termination returns without waiting for its own delivery to
                      // finish.
                      jobReference.get().terminateEarly();
                      terminationReturned.countDown();
                      await(releaseConsumer);
                    }
                  })
              .withExceptionHandler((id, failure) -> failureReference.set(failure))
              .run();
      jobReference.set(job);
      var acceptedChange =
          job.addProblemChange(
              (solution, director) ->
                  director.addProblemFact(
                      new TestdataValue("added"), solution.getValueList()::add));
      try {
        await(terminationReturned);
        assertThat(job.getSolverStatus()).isEqualTo(SolverStatus.SOLVING_ACTIVE);
        assertThat(job.isTerminatedEarly()).isTrue();
        assertThat(acceptedChange).isNotCompleted();
        assertThatThrownBy(() -> job.addProblemChange((solution, director) -> {}))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                () -> manager.addProblemChange("active-drain", (solution, director) -> {}))
            .isInstanceOf(IllegalStateException.class);
        assertThat(acceptedChange).isNotCompleted();
        releasePhase.countDown();
        releaseConsumer.countDown();
        acceptedChange.get(5, TimeUnit.SECONDS);
        assertThat(getBounded(job::getFinalBestSolution).getValueList()).hasSize(3);
        assertThat(acceptedChange).isNotCancelled();
        assertThat(failureReference.get()).isNull();
      } finally {
        releasePhase.countDown();
        releaseConsumer.countDown();
      }
    }
  }

  @Test
  void callableLosingStartupCancelsFutureBeforeItCanPublishAnExceptionalResult() throws Exception {
    var cancellationSelected = new CountDownLatch(1);
    var releaseCancellation = new CountDownLatch(1);
    var finderCalls = new AtomicInteger();
    var factory = SolverFactory.<TestdataSolution>create(config(false));
    try (var manager = new DefaultSolverManager<>(factory, managerConfig())) {
      var input = problem("losing-start");
      var job =
          new DefaultSolverJob<>(
              manager,
              factory.buildSolver(),
              "losing-start",
              id -> {
                finderCalls.incrementAndGet();
                return input;
              },
              null,
              null,
              null,
              null,
              (id, failure) -> {});
      var firstCancellation = new AtomicBoolean(true);
      var future =
          new FutureTask<>(job) {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
              if (firstCancellation.getAndSet(false)) {
                cancellationSelected.countDown();
                await(releaseCancellation);
              }
              return super.cancel(mayInterruptIfRunning);
            }
          };
      job.setFinalBestSolutionFuture(future);
      var closure =
          new FutureTask<Void>(
              () -> {
                job.close();
                return null;
              });
      var closer = Thread.ofPlatform().start(closure);
      Thread starter = null;
      try {
        await(cancellationSelected);
        starter = Thread.ofPlatform().start(future);
        join(starter);
        assertThat(future.isCancelled()).isTrue();
        assertThat(finderCalls).hasValue(0);
        assertThat(getBounded(job::getFinalBestSolution)).isSameAs(input);
        assertThat(finderCalls).hasValue(1);
        releaseCancellation.countDown();
        closure.get(5, TimeUnit.SECONDS);
      } finally {
        releaseCancellation.countDown();
        join(closer);
        if (starter != null) join(starter);
      }
    }
  }

  @Test
  void managerCloseCancelsQueuedJobsWithoutWaitingForAnActiveProblemFinder() throws Exception {
    try (var fixture = new BlockedFinder(true)) {
      var input = problem("queued");
      var job = fixture.manager.solve("queued", input);
      var change = job.addProblemChange((solution, director) -> {});
      var continuation = change.handle((ignored, failure) -> finalSolution(job));

      runBounded(fixture.manager::close);
      assertThat(fixture.releaseFinder.getCount()).isEqualTo(1);
      assertThat(job.getSolverStatus()).isEqualTo(SolverStatus.NOT_SOLVING);
      assertThat(fixture.manager.getSolverStatus("queued")).isEqualTo(SolverStatus.NOT_SOLVING);
      assertThat(change).isCancelled();
      assertThat(continuation.get(5, TimeUnit.SECONDS)).isSameAs(input);
      assertThat(job.isTerminatedEarly()).isFalse();
      assertThatThrownBy(() -> job.addProblemChange((solution, director) -> {}))
          .isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> fixture.manager.solve("late", problem("late")))
          .isInstanceOf(RejectedExecutionException.class);

      // The finder deliberately clears interruption. Shutdown must survive solve() resetting
      // plumbing termination, including for a daemon solver which otherwise waits indefinitely.
      fixture.releaseFinder.countDown();
      assertThat(getBounded(fixture.firstJob::getFinalBestSolution)).isNotNull();
      assertThat(fixture.firstJob.isTerminatedEarly()).isFalse();
      assertThat(fixture.firstJob.getMoveEvaluationCount()).isZero();
      assertThat(fixture.firstJob.getSolverStatus()).isEqualTo(SolverStatus.NOT_SOLVING);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void managerCloseCancelsEveryQueuedFinalBeforeCompletingCrossDependentChanges(
      boolean reverseSubmission) throws Exception {
    try (var fixture = new BlockedFinder(false)) {
      var firstInput = problem(reverseSubmission ? "B" : "A");
      var secondInput = problem(reverseSubmission ? "A" : "B");
      var firstJob = fixture.manager.solve(reverseSubmission ? "B" : "A", firstInput);
      var secondJob = fixture.manager.solve(reverseSubmission ? "A" : "B", secondInput);
      var firstChange = firstJob.addProblemChange((solution, director) -> {});
      var secondChange = secondJob.addProblemChange((solution, director) -> {});
      // Both callbacks are synchronous; either map iteration order must finish both final futures
      // before invoking the first callback. shutdownNow() has already removed both queued tasks.
      var firstContinuation = firstChange.handle((ignored, failure) -> finalSolution(secondJob));
      var secondContinuation = secondChange.handle((ignored, failure) -> finalSolution(firstJob));
      var closure =
          new FutureTask<Void>(
              () -> {
                fixture.manager.close();
                return null;
              });
      var closer = Thread.ofPlatform().start(closure);
      try {
        closure.get(5, TimeUnit.SECONDS);
        assertThat(fixture.releaseFinder.getCount()).isEqualTo(1);
        assertThat(firstJob.getSolverStatus()).isEqualTo(SolverStatus.NOT_SOLVING);
        assertThat(secondJob.getSolverStatus()).isEqualTo(SolverStatus.NOT_SOLVING);
        assertThat(firstChange).isCancelled();
        assertThat(secondChange).isCancelled();
        assertThat(firstContinuation.get(5, TimeUnit.SECONDS)).isSameAs(secondInput);
        assertThat(secondContinuation.get(5, TimeUnit.SECONDS)).isSameAs(firstInput);
      } finally {
        // On regression, interrupt the getter inside the synchronous callback so queued cleanup
        // can continue and no closer thread remains blocked after this bounded test fails.
        if (!closure.isDone()) {
          closer.interrupt();
        }
        fixture.releaseFinder.countDown();
        join(closer);
      }
    }
  }

  @Test
  void changesAcceptedWhileScheduledAreAcknowledgedAfterTheirFinalSolutionIsConsumed()
      throws Exception {
    var finalEntered = new CountDownLatch(1);
    var releaseFinal = new CountDownLatch(1);
    try (var fixture = new BlockedFinder(false)) {
      var consumedValueCount = new AtomicInteger();
      var job =
          fixture
              .manager
              .solveBuilder()
              .withProblemId("queued")
              .withProblem(problem("queued"))
              .withFinalBestSolutionEventConsumer(
                  event -> {
                    consumedValueCount.set(event.solution().getValueList().size());
                    finalEntered.countDown();
                    await(releaseFinal);
                  })
              .run();
      var change =
          job.addProblemChange(
              (solution, director) ->
                  director.addProblemFact(
                      new TestdataValue("added"), solution.getValueList()::add));
      assertThat(job.getSolverStatus()).isEqualTo(SolverStatus.SOLVING_SCHEDULED);
      fixture.releaseFinder.countDown();
      try {
        await(finalEntered);
        assertThat(consumedValueCount).hasValue(3);
        assertThat(change).isNotCompleted();
      } finally {
        releaseFinal.countDown();
      }
      change.get(5, TimeUnit.SECONDS);
      assertThat(getBounded(job::getFinalBestSolution).getValueList()).hasSize(3);
      assertThat(change).isCompleted();
      assertThat(change).isNotCancelled();
    } finally {
      releaseFinal.countDown();
    }
  }

  @Test
  void shutdownDuringSolverConstructionRejectsSubmissionWithoutPublishingAJob() throws Exception {
    var buildEntered = new CountDownLatch(1);
    var releaseBuild = new CountDownLatch(1);
    var realFactory = SolverFactory.<TestdataSolution>create(config(false));
    SolverFactory<TestdataSolution> factory = mock(SolverFactory.class);
    when(factory.buildSolver()).thenAnswer(invocation -> realFactory.buildSolver());
    when(factory.buildSolver(any(SolverConfigOverride.class)))
        .thenAnswer(
            invocation -> {
              buildEntered.countDown();
              await(releaseBuild);
              return realFactory.buildSolver(invocation.getArgument(0));
            });
    try (var manager = new DefaultSolverManager<>(factory, managerConfig())) {
      var submission = new FutureTask<>(() -> manager.solve("building", problem("building")));
      var submitter = Thread.ofPlatform().start(submission);
      try {
        await(buildEntered);
        runBounded(manager::close);
        releaseBuild.countDown();
        assertThatThrownBy(() -> submission.get(5, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(RejectedExecutionException.class);
        assertThat(manager.getSolverStatus("building")).isEqualTo(SolverStatus.NOT_SOLVING);
      } finally {
        releaseBuild.countDown();
        join(submitter);
      }
    }
  }

  @Test
  void shutdownBeforeWorkerRunsCancelsItsInstalledFutureAndSkipsTheFinder() throws Exception {
    var workerGate = new CountDownLatch(1);
    var finderCalls = new AtomicInteger();
    var control = new ThreadControl();
    control.workerGate = workerGate;
    ControlledThreadFactory.CONTROL.set(control);
    try (var manager =
        new DefaultSolverManager<>(
            SolverFactory.<TestdataSolution>create(config(false)),
            managerConfig().withThreadFactoryClass(ControlledThreadFactory.class))) {
      var input = problem("not-started");
      var job =
          manager
              .solveBuilder()
              .withProblemId("not-started")
              .withProblemFinder(
                  id -> {
                    finderCalls.incrementAndGet();
                    return input;
                  })
              .run();
      var change = job.addProblemChange((solution, director) -> {});
      runBounded(manager::close);
      assertThat(job.getSolverStatus()).isEqualTo(SolverStatus.NOT_SOLVING);
      assertThat(change).isCancelled();
      workerGate.countDown();
      join(control.worker.get());
      assertThat(finderCalls).hasValue(0);
      assertThat(getBounded(job::getFinalBestSolution)).isSameAs(input);
      assertThat(finderCalls).hasValue(1);
    } finally {
      workerGate.countDown();
      ControlledThreadFactory.CONTROL.set(null);
      if (control.worker.get() != null) join(control.worker.get());
    }
  }

  @Test
  void submissionAndShutdownSerializeRegistrationWithExecutorAdmission() throws Exception {
    var executeEntered = new CountDownLatch(1);
    var releaseExecute = new CountDownLatch(1);
    var workerGate = new CountDownLatch(1);
    var control = new ThreadControl();
    control.workerGate = workerGate;
    control.beforeThreadCreation =
        () -> {
          executeEntered.countDown();
          await(releaseExecute);
        };
    ControlledThreadFactory.CONTROL.set(control);
    try (var manager =
        new DefaultSolverManager<>(
            SolverFactory.<TestdataSolution>create(config(false)),
            managerConfig().withThreadFactoryClass(ControlledThreadFactory.class))) {
      var submission = new FutureTask<>(() -> manager.solve("racing", problem("racing")));
      var submitter = Thread.ofPlatform().start(submission);
      Thread closer = null;
      try {
        await(executeEntered);
        var change = manager.addProblemChange("racing", (solution, director) -> {});
        var closure =
            new FutureTask<Void>(
                () -> {
                  manager.close();
                  return null;
                });
        closer = Thread.ofPlatform().start(closure);
        awaitBlocked(closer);
        releaseExecute.countDown();
        var job = submission.get(5, TimeUnit.SECONDS);
        closure.get(5, TimeUnit.SECONDS);
        assertThat(job.getSolverStatus()).isEqualTo(SolverStatus.NOT_SOLVING);
        assertThat(change).isCancelled();
        assertThat(manager.getSolverStatus("racing")).isEqualTo(SolverStatus.NOT_SOLVING);
      } finally {
        releaseExecute.countDown();
        workerGate.countDown();
        join(submitter);
        if (closer != null) join(closer);
      }
    } finally {
      releaseExecute.countDown();
      workerGate.countDown();
      ControlledThreadFactory.CONTROL.set(null);
      if (control.worker.get() != null) join(control.worker.get());
    }
  }

  @Test
  void changeAdmissionAndCancellationAreAtomicIncludingSolverQueueRegistration() throws Exception {
    var holder = new BestSolutionHolder<TestdataSolution>();
    Solver<TestdataSolution> solver = mock(Solver.class);
    var enqueueEntered = new CountDownLatch(1);
    var releaseEnqueue = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              enqueueEntered.countDown();
              await(releaseEnqueue);
              return null;
            })
        .when(solver)
        .addProblemChanges(any());
    List<ProblemChange<TestdataSolution>> changes = List.of((solution, director) -> {});
    var registration = new FutureTask<>(() -> holder.addProblemChange(solver, changes));
    var registrar = Thread.ofPlatform().start(registration);
    Thread closer = null;
    try {
      await(enqueueEntered);
      var closure =
          new FutureTask<Void>(
              () -> {
                holder.cancelPendingChanges();
                return null;
              });
      closer = Thread.ofPlatform().start(closure);
      awaitBlocked(closer);
      releaseEnqueue.countDown();
      var accepted = registration.get(5, TimeUnit.SECONDS);
      closure.get(5, TimeUnit.SECONDS);
      assertThat(accepted).isCancelled();
      assertThatThrownBy(() -> holder.addProblemChange(solver, changes))
          .isInstanceOf(IllegalStateException.class);
    } finally {
      releaseEnqueue.countDown();
      join(registrar);
      if (closer != null) join(closer);
    }
  }

  @Test
  void stoppingAdmissionRetainsRepresentedChangesAndCompletesUnrepresentedChangesOutsideHolderLock()
      throws Exception {
    var holder = new BestSolutionHolder<TestdataSolution>();
    Solver<TestdataSolution> solver = mock(Solver.class);
    List<ProblemChange<TestdataSolution>> changes = List.of((solution, director) -> {});
    var represented = holder.addProblemChange(solver, changes);
    holder.set(problem("represented"), EventProducerId.solvingStarted(), () -> true);
    var pending = holder.addProblemChange(solver, changes);
    holder.closeProblemChangeAdmission();
    var rejectedSolver = mock(Solver.class);
    assertThatThrownBy(() -> holder.addProblemChange(rejectedSolver, changes))
        .isInstanceOf(IllegalStateException.class);
    verify(rejectedSolver, never()).addProblemChanges(any());
    assertThat(represented).isNotCompleted();
    holder.take().completeProblemChanges();
    assertThat(represented).isCompleted();
    assertThat(pending).isNotCompleted();
    var continuation =
        pending.handle(
            (ignored, failure) -> {
              runBounded(
                  () ->
                      assertThatThrownBy(() -> holder.addProblemChange(solver, changes))
                          .isInstanceOf(IllegalStateException.class));
              return null;
            });
    runBounded(holder::cancelPendingChanges);
    continuation.get(5, TimeUnit.SECONDS);
    assertThat(pending).isCancelled();
  }

  @Test
  void failedSubmissionUnregistersAndCancelsAcceptedChangesOutsideManagerAdmission()
      throws Exception {
    var control = new ThreadControl();
    var submissionFailure = new IllegalStateException("thread creation failed");
    var changeReference = new AtomicReference<CompletableFuture<Void>>();
    var continuationReference = new AtomicReference<CompletableFuture<Void>>();
    ControlledThreadFactory.CONTROL.set(control);
    try (var manager =
        new DefaultSolverManager<>(
            SolverFactory.<TestdataSolution>create(config(false)),
            managerConfig().withThreadFactoryClass(ControlledThreadFactory.class))) {
      control.beforeThreadCreation =
          () -> {
            var change = manager.addProblemChange("rejected", (solution, director) -> {});
            changeReference.set(change);
            continuationReference.set(
                change.handle(
                    (ignored, failure) -> {
                      assertThat(manager.getSolverStatus("rejected"))
                          .isEqualTo(SolverStatus.NOT_SOLVING);
                      runBounded(manager::close);
                      return null;
                    }));
            throw submissionFailure;
          };
      assertThatThrownBy(() -> manager.solve("rejected", problem("rejected")))
          .isSameAs(submissionFailure);
      assertThat(changeReference.get()).isCancelled();
      continuationReference.get().get(5, TimeUnit.SECONDS);
      assertThat(manager.getSolverStatus("rejected")).isEqualTo(SolverStatus.NOT_SOLVING);
    } finally {
      ControlledThreadFactory.CONTROL.set(null);
    }
  }

  @Test
  void finderFailureAndThrowingExceptionHandlerStillCancelChangesAfterReleasingStartup()
      throws Exception {
    var finderEntered = new CountDownLatch(1);
    var releaseFinder = new CountDownLatch(1);
    var finderFailure = new IllegalArgumentException("finder failed");
    try (var manager =
        new DefaultSolverManager<>(
            SolverFactory.<TestdataSolution>create(config(false)), managerConfig())) {
      var job =
          manager
              .solveBuilder()
              .withProblemId("failing")
              .withProblemFinder(
                  id -> {
                    finderEntered.countDown();
                    await(releaseFinder);
                    throw finderFailure;
                  })
              .withExceptionHandler(
                  (id, failure) -> {
                    throw new IllegalStateException("handler failed");
                  })
              .run();
      try {
        await(finderEntered);
        var change = job.addProblemChange((solution, director) -> {});
        var continuation =
            change.handle(
                (ignored, failure) -> {
                  runBounded(job::terminateEarly);
                  return null;
                });
        releaseFinder.countDown();
        assertThatThrownBy(() -> getBounded(job::getFinalBestSolution)).hasRootCause(finderFailure);
        continuation.get(5, TimeUnit.SECONDS);
        assertThat(change).isCancelled();
        assertThat(job.getSolverStatus()).isEqualTo(SolverStatus.NOT_SOLVING);
      } finally {
        releaseFinder.countDown();
      }
    }
  }

  private static SolverConfig config(boolean daemon) {
    return PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
        .withDaemon(daemon)
        .withMoveThreadCount("NONE")
        .withPhases(new ConstructionHeuristicPhaseConfig());
  }

  private static SolverManagerConfig managerConfig() {
    return new SolverManagerConfig().withParallelSolverCount("1");
  }

  private static TestdataSolution problem(String code) {
    return PlannerTestUtils.generateTestdataSolution(code, 2);
  }

  private static TestdataSolution finalSolution(SolverJob<TestdataSolution> job) {
    try {
      return job.getFinalBestSolution();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw new AssertionError(e);
    }
  }

  private static void runBounded(Runnable action) {
    try {
      getBounded(
          () -> {
            action.run();
            return null;
          });
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static <T> T getBounded(java.util.concurrent.Callable<T> action) throws Exception {
    var task = new FutureTask<>(action);
    var thread = Thread.ofPlatform().daemon().start(task);
    try {
      return task.get(5, TimeUnit.SECONDS);
    } finally {
      if (!task.isDone()) thread.interrupt();
      join(thread);
    }
  }

  private static void awaitBlocked(Thread thread) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (thread.getState() != Thread.State.BLOCKED) {
      assertThat(thread.isAlive()).isTrue();
      assertThat(System.nanoTime())
          .as("thread reaches the contended admission monitor")
          .isLessThan(deadline);
      Thread.onSpinWait();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).as("latch completed").isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  private static void awaitIgnoringInterrupts(CountDownLatch latch) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (latch.getCount() != 0) {
      try {
        assertThat(latch.await(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS))
            .as("released blocked application code")
            .isTrue();
      } catch (InterruptedException ignored) {
        // Simulate application code which completes its work despite shutdown interruption.
      }
    }
    Thread.interrupted();
  }

  private static void join(Thread thread) throws InterruptedException {
    thread.join(5000);
    assertThat(thread.isAlive()).as("bounded worker finished").isFalse();
  }

  private static final class BlockedFinder implements AutoCloseable {
    private final CountDownLatch releaseFinder = new CountDownLatch(1);
    private final DefaultSolverManager<TestdataSolution> manager;
    private final SolverJob<TestdataSolution> firstJob;

    private BlockedFinder(boolean daemon) {
      manager =
          new DefaultSolverManager<>(
              SolverFactory.<TestdataSolution>create(config(daemon)), managerConfig());
      var finderEntered = new CountDownLatch(1);
      firstJob =
          manager
              .solveBuilder()
              .withProblemId("first")
              .withProblemFinder(
                  id -> {
                    finderEntered.countDown();
                    awaitIgnoringInterrupts(releaseFinder);
                    return problem("first");
                  })
              .run();
      await(finderEntered);
    }

    @Override
    public void close() throws Exception {
      releaseFinder.countDown();
      manager.close();
      getBounded(firstJob::getFinalBestSolution);
    }
  }

  private static final class ThreadControl {
    private Runnable beforeThreadCreation = () -> {};
    private CountDownLatch workerGate = new CountDownLatch(0);
    private final AtomicReference<Thread> worker = new AtomicReference<>();
  }

  public static final class ControlledThreadFactory implements ThreadFactory {
    private static final AtomicReference<ThreadControl> CONTROL = new AtomicReference<>();

    @Override
    public Thread newThread(Runnable runnable) {
      var control = CONTROL.get();
      control.beforeThreadCreation.run();
      var thread =
          new Thread(
              () -> {
                awaitIgnoringInterrupts(control.workerGate);
                runnable.run();
              });
      control.worker.set(thread);
      return thread;
    }
  }
}
