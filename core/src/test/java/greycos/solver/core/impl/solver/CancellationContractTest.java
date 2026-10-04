package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.solver.SolverJob;
import greycos.solver.core.api.solver.SolverManager;
import greycos.solver.core.api.solver.SolverStatus;
import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.SolverManagerConfig;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Termination requests and bounded external waits remain independent of application callbacks. */
@Timeout(100)
class CancellationContractTest {

  @Test
  void callbackCancellationDoesNotSatisfyLaterExternalTerminationCall() throws Exception {
    var phaseEntered = new CountDownLatch(1);
    var releasePhase = new CountDownLatch(1);
    var jobPublished = new CountDownLatch(1);
    var callbackTerminationReturned = new CountDownLatch(1);
    var jobReference = new AtomicReference<SolverJob<TestdataSolution>>();
    var failure = new AtomicReference<Throwable>();
    try (var manager = manager(heldPhaseConfig(phaseEntered, releasePhase))) {
      var job =
          manager
              .solveBuilder()
              .withProblemId("repeat")
              .withProblem(problem())
              .withSolverJobStartedEventConsumer(
                  event -> {
                    await(jobPublished, 10);
                    await(phaseEntered, 10);
                    jobReference.get().terminateEarly();
                    callbackTerminationReturned.countDown();
                  })
              .withExceptionHandler((id, throwable) -> failure.set(throwable))
              .run();
      jobReference.set(job);
      jobPublished.countDown();
      try {
        await(callbackTerminationReturned, 10);
        assertThat(job.getSolverStatus()).isEqualTo(SolverStatus.SOLVING_ACTIVE);
        var caller = new TerminationCall(job);
        boolean returnedWhilePhaseHeld = caller.completedWithin(1);
        caller.printBlockedObservation("repeated external call before phase release", job);
        releasePhase.countDown();
        var observation = caller.result.get(5, TimeUnit.SECONDS);
        System.out.printf("PROBE repeated external call: %s%n", observation);
        assertThat(returnedWhilePhaseHeld)
            .as("external terminateEarly waits for held solver phase to finish")
            .isFalse();
      } finally {
        releasePhase.countDown();
        finalSolution(job);
        assertThat(failure.get()).isNull();
      }
    }
  }

  @Test
  void blockedProblemFinderMustNotExtendDeadline() throws Exception {
    blockedFinderDeadline(false);
  }

  @Test
  void publicOneMinuteDeadlineIncludesBlockedProblemFinder() throws Exception {
    blockedFinderDeadline(true);
  }

  private void blockedFinderDeadline(boolean publicDeadline) throws Exception {
    var finderEntered = new CountDownLatch(1);
    var releaseFinder = new CountDownLatch(1);
    var failure = new AtomicReference<Throwable>();
    try (var manager = manager(config())) {
      var job =
          manager
              .solveBuilder()
              .withProblemId("finder")
              .withProblemFinder(
                  id -> {
                    finderEntered.countDown();
                    awaitIgnoringInterrupts(releaseFinder);
                    return problem();
                  })
              .withExceptionHandler((id, throwable) -> failure.set(throwable))
              .run();
      TerminationCall caller = null;
      boolean completedWithinDeadline;
      try {
        await(finderEntered, 10);
        caller = new TerminationCall(job, publicDeadline ? null : Duration.ofMillis(200));
        completedWithinDeadline = caller.completedWithin(publicDeadline ? 63 : 3);
        caller.printBlockedObservation("blocked finder", job);
        if (publicDeadline && completedWithinDeadline) {
          assertThat(caller.result.get().elapsedMillis()).isBetween(59_500.0, 63_000.0);
        }
        assertThat(job.getSolverStatus()).isEqualTo(SolverStatus.NOT_SOLVING);
      } finally {
        releaseFinder.countDown();
        if (caller != null) {
          System.out.printf(
              "PROBE finder after release: %s%n", caller.result.get(5, TimeUnit.SECONDS));
        }
        finalSolution(job);
        assertThat(failure.get()).isNull();
      }
      assertThat(completedWithinDeadline)
          .as("terminateEarly returns within one minute plus three seconds of scheduling tolerance")
          .isTrue();
    }
  }

  @Test
  void timeoutCleanupMustNotExtendOneMinuteWait() throws Exception {
    var phaseEntered = new CountDownLatch(1);
    var releasePhase = new CountDownLatch(1);
    var callbackEntered = new CountDownLatch(1);
    var releaseCallback = new CountDownLatch(1);
    var failure = new AtomicReference<Throwable>();
    try (var manager = manager(heldPhaseConfig(phaseEntered, releasePhase))) {
      var job =
          manager
              .solveBuilder()
              .withProblemId("timeout-consumer")
              .withProblem(problem())
              .withBestSolutionEventConsumer(
                  event -> {
                    callbackEntered.countDown();
                    awaitIgnoringInterrupts(releaseCallback);
                  })
              .withExceptionHandler((id, throwable) -> failure.set(throwable))
              .run();
      TerminationCall caller = null;
      boolean completedWithinDeadline;
      try {
        await(phaseEntered, 10);
        await(callbackEntered, 10);
        caller = new TerminationCall(job, Duration.ofMillis(200));
        completedWithinDeadline = caller.completedWithin(3);
        caller.printBlockedObservation("timeout cleanup", job);
        releaseCallback.countDown();
        System.out.printf(
            "PROBE callback release alone: %s; phaseStillHeld=%s%n",
            caller.result.get(5, TimeUnit.SECONDS), releasePhase.getCount() == 1);
      } finally {
        releaseCallback.countDown();
        releasePhase.countDown();
        if (caller != null) {
          caller.result.get(5, TimeUnit.SECONDS);
        }
        finalSolution(job);
        assertThat(failure.get()).isNull();
      }
      assertThat(completedWithinDeadline)
          .as("timeout cleanup must not await a callback beyond the one-minute limit")
          .isTrue();
    }
  }

  @Test
  void normalTerminationAllowsConsumerToOutliveReturnAsDocumented() throws Exception {
    var phaseEntered = new CountDownLatch(1);
    var releasePhase = new CountDownLatch(1);
    var callbackEntered = new CountDownLatch(1);
    var releaseCallback = new CountDownLatch(1);
    var failure = new AtomicReference<Throwable>();
    try (var manager = manager(heldPhaseConfig(phaseEntered, releasePhase))) {
      var job =
          manager
              .solveBuilder()
              .withProblemId("normal-consumer")
              .withProblem(problem())
              .withBestSolutionEventConsumer(
                  event -> {
                    callbackEntered.countDown();
                    awaitIgnoringInterrupts(releaseCallback);
                  })
              .withExceptionHandler((id, throwable) -> failure.set(throwable))
              .run();
      try {
        await(phaseEntered, 10);
        await(callbackEntered, 10);
        var caller = new TerminationCall(job);
        releasePhase.countDown();
        var observation = caller.result.get(5, TimeUnit.SECONDS);
        System.out.printf(
            "PROBE normal termination: %s; callbackStillHeld=%s%n",
            observation, releaseCallback.getCount() == 1);
        assertThat(observation.status()).isEqualTo(SolverStatus.NOT_SOLVING);
        assertThat(releaseCallback.getCount()).isEqualTo(1);
      } finally {
        releasePhase.countDown();
        releaseCallback.countDown();
        finalSolution(job);
        assertThat(failure.get()).isNull();
      }
    }
  }

  @Test
  void concurrentExternalCallsBothWaitForTheActiveSolver() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var manager = manager(heldPhaseConfig(entered, release))) {
      var job = manager.solve("concurrent", problem());
      try {
        await(entered, 5);
        var first = new TerminationCall(job);
        org.awaitility.Awaitility.await()
            .atMost(Duration.ofSeconds(5))
            .until(job::isTerminatedEarly);
        var second = new TerminationCall(job);
        assertThat(first.completedWithinMillis(100)).isFalse();
        assertThat(second.completedWithinMillis(100)).isFalse();
        assertThatThrownBy(() -> job.addProblemChange((solution, director) -> {}))
            .isInstanceOf(IllegalStateException.class);
        release.countDown();
        assertThat(first.result.get(5, TimeUnit.SECONDS).status())
            .isEqualTo(SolverStatus.NOT_SOLVING);
        assertThat(second.result.get(5, TimeUnit.SECONDS).status())
            .isEqualTo(SolverStatus.NOT_SOLVING);
      } finally {
        release.countDown();
        finalSolution(job);
      }
    }
  }

  @Test
  void repeatedCallCompletesBookkeepingAlreadySelectedByAnotherCaller() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var bookkeepingEntered = new CountDownLatch(1);
    var releaseBookkeeping = new CountDownLatch(1);
    var firstBookkeeping = new AtomicBoolean(true);
    var actualManager =
        (DefaultSolverManager<TestdataSolution>) manager(heldPhaseConfig(entered, release));
    try (var manager = org.mockito.Mockito.spy(actualManager)) {
      org.mockito.Mockito.doAnswer(
              invocation -> {
                if (firstBookkeeping.compareAndSet(true, false)) {
                  bookkeepingEntered.countDown();
                  await(releaseBookkeeping, 5);
                }
                return invocation.callRealMethod();
              })
          .when(manager)
          .unregisterSolverJob(
              org.mockito.ArgumentMatchers.eq("queued"), org.mockito.ArgumentMatchers.any());
      var active = manager.solve("active", problem());
      try {
        await(entered, 5);
        var queued = manager.solve("queued", problem());
        var first = new TerminationCall(queued);
        await(bookkeepingEntered, 5);
        assertThat(queued.getSolverStatus()).isEqualTo(SolverStatus.NOT_SOLVING);
        new TerminationCall(queued).result.get(3, TimeUnit.SECONDS);
        // Returning solely because NOT_SOLVING is visible would leave this ID registered.
        var replacement = manager.solve("queued", problem());
        releaseBookkeeping.countDown();
        first.result.get(3, TimeUnit.SECONDS);
        assertThat(manager.getSolverStatus("queued")).isEqualTo(SolverStatus.SOLVING_SCHEDULED);
        replacement.terminateEarly();
      } finally {
        releaseBookkeeping.countDown();
        release.countDown();
        finalSolution(active);
      }
    }
  }

  @Test
  void interruptedWaitRetainsRequestAcrossStartupReset() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var phaseCalls = new AtomicInteger();
    var solverConfig =
        config()
            .withPhases(
                new CustomPhaseConfig()
                    .withCustomPhaseCommands(
                        (PhaseCommand<TestdataSolution>) context -> phaseCalls.incrementAndGet()));
    try (var manager = manager(solverConfig)) {
      var job =
          manager
              .solveBuilder()
              .withProblemId("interrupted-startup")
              .withProblemFinder(
                  id -> {
                    entered.countDown();
                    awaitIgnoringInterrupts(release);
                    return problem();
                  })
              .run();
      try {
        await(entered, 5);
        var interruptedFlag =
            new FutureTask<>(
                () -> {
                  Thread.currentThread().interrupt();
                  job.terminateEarly();
                  return Thread.currentThread().isInterrupted();
                });
        Thread.ofPlatform().daemon().start(interruptedFlag);
        assertThat(interruptedFlag.get(3, TimeUnit.SECONDS)).isTrue();
        assertThat(job.isTerminatedEarly()).isTrue();
        assertThat(job.getSolverStatus()).isEqualTo(SolverStatus.SOLVING_ACTIVE);
        assertThatThrownBy(() -> job.addProblemChange((solution, director) -> {}))
            .isInstanceOf(IllegalStateException.class);
        var repeated = new TerminationCall(job);
        assertThat(repeated.completedWithinMillis(100)).isFalse();
        release.countDown();
        repeated.result.get(5, TimeUnit.SECONDS);
        finalSolution(job);
        assertThat(phaseCalls).hasValue(0);
      } finally {
        release.countDown();
      }
    }
  }

  @Test
  void interruptedExternalWaitReturnsPromptlyAfterRecordingRequest() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var manager = manager(heldPhaseConfig(entered, release))) {
      var job = manager.solve("interrupt-active", problem());
      try {
        await(entered, 5);
        var preserved =
            new FutureTask<>(
                () -> {
                  job.terminateEarly();
                  return Thread.currentThread().isInterrupted();
                });
        var caller = Thread.ofPlatform().daemon().start(preserved);
        org.awaitility.Awaitility.await()
            .atMost(Duration.ofSeconds(5))
            .until(job::isTerminatedEarly);
        caller.interrupt();
        assertThat(preserved.get(3, TimeUnit.SECONDS)).isTrue();
        assertThat(job.getSolverStatus()).isEqualTo(SolverStatus.SOLVING_ACTIVE);
      } finally {
        release.countDown();
        finalSolution(job);
      }
    }
  }

  @Test
  void blockedQueuedContinuationDoesNotDelayCancellationOfOtherChangesOrCaller() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var continuationEntered = new CountDownLatch(1);
    var releaseContinuation = new CountDownLatch(1);
    var finderCalls = new AtomicInteger();
    try (var manager = manager(heldPhaseConfig(entered, release))) {
      var active = manager.solve("active", problem());
      DefaultSolverJob<TestdataSolution> job = null;
      try {
        await(entered, 5);
        job =
            (DefaultSolverJob<TestdataSolution>)
                manager
                    .solveBuilder()
                    .withProblemId("queued")
                    .withProblemFinder(
                        id -> {
                          finderCalls.incrementAndGet();
                          return problem();
                        })
                    .run();
        var queued = job;
        var changes =
            List.of(
                job.addProblemChange((s, d) -> {}),
                job.addProblemChange((s, d) -> {}),
                job.addProblemChange((s, d) -> {}));
        var heldContinuation =
            changes
                .getFirst()
                .handle(
                    (ignored, failure) -> {
                      continuationEntered.countDown();
                      awaitIgnoringInterrupts(releaseContinuation);
                      return null;
                    });
        var reentrantContinuation =
            changes
                .get(1)
                .handle(
                    (ignored, failure) -> {
                      assertThat(Thread.currentThread().isVirtual()).isTrue();
                      assertThat(SolverEventThreadContext.isActive()).isTrue();
                      queued.terminateEarly();
                      queued.close();
                      assertThat(queued.getSolverStatus()).isEqualTo(SolverStatus.NOT_SOLVING);
                      return null;
                    });
        var caller = new TerminationCall(job, Duration.ofSeconds(2));
        caller.result.get(3, TimeUnit.SECONDS);
        await(continuationEntered, 5);
        assertThat(changes).allSatisfy(change -> assertThat(change).isCancelled());
        assertThat(heldContinuation).isNotCompleted();
        assertThat(job)
            .extracting("terminationActions")
            .extracting("pendingChanges")
            .isEqualTo(List.of());
        reentrantContinuation.get(3, TimeUnit.SECONDS);
        assertThat(finderCalls).hasValue(0);
        var closure =
            new FutureTask<Void>(
                () -> {
                  queued.close();
                  return null;
                });
        Thread.ofPlatform().daemon().start(closure);
        assertThatThrownBy(() -> closure.get(100, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
        releaseContinuation.countDown();
        closure.get(3, TimeUnit.SECONDS);
        heldContinuation.get(3, TimeUnit.SECONDS);
      } finally {
        releaseContinuation.countDown();
        release.countDown();
        if (job != null) job.close();
        finalSolution(active);
      }
    }
  }

  @Test
  void cancellationContinuationMayCloseManagerWithOtherQueuedJobs() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var manager = manager(heldPhaseConfig(entered, release))) {
      var active = manager.solve("active", problem());
      try {
        await(entered, 5);
        var first = manager.solve("first", problem());
        var second = manager.solve("second", problem());
        var firstChange = first.addProblemChange((s, d) -> {});
        var secondChange = second.addProblemChange((s, d) -> {});
        var continuation =
            firstChange.handle(
                (ignored, failure) -> {
                  manager.close();
                  second.terminateEarly();
                  return null;
                });
        new TerminationCall(first).result.get(3, TimeUnit.SECONDS);
        continuation.get(3, TimeUnit.SECONDS);
        org.awaitility.Awaitility.await()
            .atMost(Duration.ofSeconds(3))
            .until(secondChange::isCancelled);
        assertThat(first.getSolverStatus()).isEqualTo(SolverStatus.NOT_SOLVING);
        assertThat(second.getSolverStatus()).isEqualTo(SolverStatus.NOT_SOLVING);
      } finally {
        release.countDown();
        finalSolution(active);
      }
    }
  }

  @Test
  void expiredActiveJobCannotUnregisterReplacementWhenItsFinderEventuallyReturns()
      throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var replacementEntered = new CountDownLatch(1);
    var releaseReplacement = new CountDownLatch(1);
    try (var manager = manager(config())) {
      var old =
          (DefaultSolverJob<TestdataSolution>)
              manager
                  .solveBuilder()
                  .withProblemId("reused")
                  .withProblemFinder(
                      id -> {
                        entered.countDown();
                        awaitIgnoringInterrupts(release);
                        return problem();
                      })
                  .run();
      try {
        await(entered, 5);
        new TerminationCall(old, Duration.ofMillis(100)).result.get(3, TimeUnit.SECONDS);
        var replacement =
            manager
                .solveBuilder()
                .withProblemId("reused")
                .withProblemFinder(
                    id -> {
                      replacementEntered.countDown();
                      awaitIgnoringInterrupts(releaseReplacement);
                      return problem();
                    })
                .run();
        release.countDown();
        await(replacementEntered, 5);
        old.close();
        assertThat(manager.getSolverStatus("reused")).isEqualTo(SolverStatus.SOLVING_ACTIVE);
        assertThat(replacement.getSolverStatus()).isEqualTo(SolverStatus.SOLVING_ACTIVE);
        releaseReplacement.countDown();
        finalSolution(replacement);
      } finally {
        release.countDown();
        releaseReplacement.countDown();
      }
    }
  }

  @Test
  void publicManagerCloseDrainsAnUnregisteredQueuedJobsContinuation() throws Exception {
    var phaseEntered = new CountDownLatch(1);
    var releasePhase = new CountDownLatch(1);
    var continuationEntered = new CountDownLatch(1);
    var releaseContinuation = new CountDownLatch(1);
    try (var manager = manager(heldPhaseConfig(phaseEntered, releasePhase))) {
      var active = manager.solve("active", problem());
      try {
        await(phaseEntered, 5);
        var queued = manager.solve("queued", problem());
        var change = queued.addProblemChange((s, d) -> {});
        var continuation =
            change.handle(
                (ignored, failure) -> {
                  continuationEntered.countDown();
                  awaitIgnoringInterrupts(releaseContinuation);
                  return null;
                });
        new TerminationCall(queued).result.get(3, TimeUnit.SECONDS);
        await(continuationEntered, 5);
        var closure =
            new FutureTask<Void>(
                () -> {
                  manager.close();
                  return null;
                });
        Thread.ofPlatform().daemon().start(closure);
        assertThatThrownBy(() -> closure.get(100, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
        releaseContinuation.countDown();
        closure.get(3, TimeUnit.SECONDS);
        continuation.get(3, TimeUnit.SECONDS);
        assertThat(releasePhase.getCount()).isEqualTo(1);
        assertThat(manager).extracting("solverJobsAwaitingCleanup").isEqualTo(Set.of());
      } finally {
        releaseContinuation.countDown();
        releasePhase.countDown();
        finalSolution(active);
      }
    }
  }

  @Test
  void publicManagerCloseDrainsConsumerAfterTimeoutUnregistersItsJob() throws Exception {
    var phaseEntered = new CountDownLatch(1);
    var releasePhase = new CountDownLatch(1);
    var consumerEntered = new CountDownLatch(1);
    var releaseConsumer = new CountDownLatch(1);
    try (var manager = manager(heldPhaseConfig(phaseEntered, releasePhase))) {
      var job =
          manager
              .solveBuilder()
              .withProblemId("timed-out")
              .withProblem(problem())
              .withBestSolutionEventConsumer(
                  event -> {
                    consumerEntered.countDown();
                    awaitIgnoringInterrupts(releaseConsumer);
                  })
              .run();
      try {
        await(phaseEntered, 5);
        await(consumerEntered, 5);
        new TerminationCall(job, Duration.ofMillis(100)).result.get(3, TimeUnit.SECONDS);
        assertThat(manager.getSolverStatus("timed-out")).isEqualTo(SolverStatus.NOT_SOLVING);
        var closure =
            new FutureTask<Void>(
                () -> {
                  manager.close();
                  return null;
                });
        Thread.ofPlatform().daemon().start(closure);
        assertThatThrownBy(() -> closure.get(100, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
        releaseConsumer.countDown();
        closure.get(3, TimeUnit.SECONDS);
        assertThat(releasePhase.getCount()).isEqualTo(1);
        assertThat(manager).extracting("solverJobsAwaitingCleanup").isEqualTo(Set.of());
      } finally {
        releaseConsumer.countDown();
        releasePhase.countDown();
        finalSolution(job);
      }
    }
  }

  @Test
  void managerCloseCompletesAllPreviouslySelectedFinalCancellationsBeforeDispatch()
      throws Exception {
    var phaseEntered = new CountDownLatch(1);
    var releasePhase = new CountDownLatch(1);
    var cancellationEntered = new CountDownLatch(1);
    var releaseCancellation = new CountDownLatch(1);
    var firstCancellation = new AtomicBoolean(true);
    try (var manager = manager(heldPhaseConfig(phaseEntered, releasePhase))) {
      var active = manager.solve("active", problem());
      try {
        await(phaseEntered, 5);
        var firstInput = problem();
        var first = manager.solve("first", firstInput);
        var second = manager.solve("second", problem());
        var finalFuture =
            new FutureTask<TestdataSolution>(() -> firstInput) {
              @Override
              public boolean cancel(boolean mayInterruptIfRunning) {
                if (firstCancellation.compareAndSet(true, false)) {
                  cancellationEntered.countDown();
                  await(releaseCancellation, 5);
                }
                return super.cancel(mayInterruptIfRunning);
              }
            };
        // Pause the first caller immediately after terminal selection, before its internal
        // FutureTask cancellation. The queued executor task will be removed by manager.close().
        var field = DefaultSolverJob.class.getDeclaredField("finalBestSolutionFuture");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var futureReference = (AtomicReference<Future<TestdataSolution>>) field.get(first);
        futureReference.set(finalFuture);
        var firstChange = first.addProblemChange((s, d) -> {});
        var secondChange = second.addProblemChange((s, d) -> {});
        var firstContinuation =
            firstChange.handle(
                (ignored, failure) -> {
                  try {
                    return second.getFinalBestSolution();
                  } catch (Exception e) {
                    throw new AssertionError(e);
                  }
                });
        var secondContinuation =
            secondChange.handle(
                (ignored, failure) -> {
                  try {
                    return first.getFinalBestSolution();
                  } catch (Exception e) {
                    throw new AssertionError(e);
                  }
                });
        var cancellation = new TerminationCall(first);
        await(cancellationEntered, 5);
        assertThat(first.getSolverStatus()).isEqualTo(SolverStatus.NOT_SOLVING);
        var closure =
            new FutureTask<Void>(
                () -> {
                  manager.close();
                  return null;
                });
        Thread.ofPlatform().daemon().start(closure);
        closure.get(3, TimeUnit.SECONDS);
        assertThat(finalFuture).isCancelled();
        assertThat(firstChange).isCancelled();
        assertThat(secondChange).isCancelled();
        firstContinuation.get(3, TimeUnit.SECONDS);
        assertThat(secondContinuation.get(3, TimeUnit.SECONDS)).isSameAs(firstInput);
        releaseCancellation.countDown();
        cancellation.result.get(3, TimeUnit.SECONDS);
      } finally {
        releaseCancellation.countDown();
        releasePhase.countDown();
        finalSolution(active);
      }
    }
  }

  @Test
  void cleanupOwnershipIsReleasedAutomaticallyForFinishedJobs() throws Exception {
    try (var manager = manager(config())) {
      var normal = manager.solve("normal", problem());
      finalSolution(normal);
      assertThat(manager).extracting("solverJobsAwaitingCleanup").isEqualTo(Set.of());
    }
    var phaseEntered = new CountDownLatch(1);
    var releasePhase = new CountDownLatch(1);
    try (var manager = manager(heldPhaseConfig(phaseEntered, releasePhase))) {
      var active = manager.solve("active", problem());
      try {
        await(phaseEntered, 5);
        var queued = manager.solve("queued", problem());
        queued.addProblemChange((s, d) -> {});
        new TerminationCall(queued).result.get(3, TimeUnit.SECONDS);
        org.awaitility.Awaitility.await()
            .atMost(Duration.ofSeconds(3))
            .untilAsserted(
                () ->
                    assertThat(manager)
                        .extracting("solverJobsAwaitingCleanup")
                        .isEqualTo(Set.of(active)));
        assertThatThrownBy(() -> manager.solve("active", problem()))
            .isInstanceOf(IllegalStateException.class);
        org.awaitility.Awaitility.await()
            .atMost(Duration.ofSeconds(3))
            .untilAsserted(
                () ->
                    assertThat(manager)
                        .extracting("solverJobsAwaitingCleanup")
                        .isEqualTo(Set.of(active)));
      } finally {
        releasePhase.countDown();
        finalSolution(active);
      }
    }
  }

  private static SolverConfig config() {
    return PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
        .withMoveThreadCount("NONE")
        .withPhases(new ConstructionHeuristicPhaseConfig());
  }

  private static SolverConfig heldPhaseConfig(CountDownLatch entered, CountDownLatch release) {
    return config()
        .withPhases(
            new ConstructionHeuristicPhaseConfig(),
            new CustomPhaseConfig()
                .withCustomPhaseCommands(
                    (PhaseCommand<TestdataSolution>)
                        context -> {
                          entered.countDown();
                          awaitIgnoringInterrupts(release);
                        }));
  }

  private static SolverManager<TestdataSolution> manager(SolverConfig config) {
    return SolverManager.create(config, new SolverManagerConfig().withParallelSolverCount("1"));
  }

  private static TestdataSolution problem() {
    return PlannerTestUtils.generateTestdataSolution("probe", 2);
  }

  private static void finalSolution(SolverJob<TestdataSolution> job) throws Exception {
    var result = new FutureTask<>(job::getFinalBestSolution);
    Thread.ofPlatform().daemon().start(result);
    result.get(5, TimeUnit.SECONDS);
  }

  private static void await(CountDownLatch latch, int seconds) {
    try {
      assertThat(latch.await(seconds, TimeUnit.SECONDS)).as("latch released").isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  private static void awaitIgnoringInterrupts(CountDownLatch latch) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
    while (latch.getCount() != 0) {
      try {
        assertThat(latch.await(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS))
            .as("application code released before watchdog")
            .isTrue();
      } catch (InterruptedException ignored) {
        // Model application work that finishes independently of solver interruption.
      }
    }
    Thread.interrupted();
  }

  private record Observation(double elapsedMillis, SolverStatus status) {}

  private static final class TerminationCall {
    private final long start = System.nanoTime();
    private final FutureTask<Observation> result;
    private final Thread thread;

    private TerminationCall(SolverJob<TestdataSolution> job) {
      this(job, Duration.ofSeconds(5));
    }

    private TerminationCall(SolverJob<TestdataSolution> job, Duration timeout) {
      result =
          new FutureTask<>(
              () -> {
                if (timeout == null) {
                  job.terminateEarly();
                } else {
                  ((DefaultSolverJob<TestdataSolution>) job)
                      .terminateEarly(start + timeout.toNanos());
                }
                return new Observation(
                    (System.nanoTime() - start) / 1_000_000.0, job.getSolverStatus());
              });
      thread = Thread.ofPlatform().daemon().start(result);
    }

    private boolean completedWithinMillis(int millis) throws Exception {
      try {
        result.get(millis, TimeUnit.MILLISECONDS);
        return true;
      } catch (TimeoutException e) {
        return false;
      }
    }

    private boolean completedWithin(int seconds) throws Exception {
      try {
        result.get(seconds, TimeUnit.SECONDS);
        return true;
      } catch (TimeoutException e) {
        return false;
      }
    }

    private void printBlockedObservation(String label, SolverJob<TestdataSolution> job) {
      System.out.printf(
          "PROBE %s: elapsedMillis=%.3f done=%s status=%s terminatedEarly=%s stack=%s%n",
          label,
          (System.nanoTime() - start) / 1_000_000.0,
          result.isDone(),
          job.getSolverStatus(),
          job.isTerminatedEarly(),
          Arrays.toString(thread.getStackTrace()));
    }
  }
}
