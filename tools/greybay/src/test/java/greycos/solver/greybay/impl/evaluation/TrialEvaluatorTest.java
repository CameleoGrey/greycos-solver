package greycos.solver.greybay.impl.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongSupplier;

import greycos.solver.core.api.cotwin.variable.InconsistentSolutionException;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolutionAssessment;
import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.change.ProblemChange;
import greycos.solver.core.api.solver.event.BestSolutionChangedEvent;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.api.solver.event.SolverEventListener;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.greybay.api.BusinessVerifier;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.EvaluationStatus;
import greycos.solver.greybay.api.ProblemAdapter;
import greycos.solver.greybay.api.ProblemCapabilities;
import greycos.solver.greybay.api.ProblemDescriptor;
import greycos.solver.greybay.api.VerificationResult;

import org.junit.jupiter.api.Test;

class TrialEvaluatorTest {
  private static final Duration HORIZON = Duration.ofSeconds(1);
  private static final long T = HORIZON.toNanos();
  private static final ProblemDescriptor DESCRIPTOR =
      new ProblemDescriptor(
          "problem", "sha256:problem-content", new ProblemCapabilities(true, false));

  @Test
  void creditsExactDeadlineAndIgnoresLaterEventAndReturnedBest() {
    var fixture = new Fixture();
    var onTime = new Sample("on-time", SimpleScore.of(10));
    fixture.solver.script =
        () -> {
          fixture.clock.set(T);
          fixture.solver.publish(onTime, true, 900);
          fixture.clock.incrementAndGet();
          fixture.solver.publish(new Sample("late", SimpleScore.of(20)), true, 1);
          fixture.solver.returned = new Sample("returned", SimpleScore.of(30));
        };
    var result = fixture.evaluate();
    assertThat(result.status()).isEqualTo(EvaluationStatus.COMPLETE);
    assertThat(result.score()).isEqualTo(onTime.score());
    assertThat(result.selectedObserverNanos()).isEqualTo(T);
    assertThat(result.selectedSolverMillis()).isEqualTo(900);
    assertThat(fixture.assessed).isSameAs(onTime);
  }

  @Test
  void lateDeliveryIsIncompleteDespiteEarlierSolverTimestamp() {
    var fixture = new Fixture();
    fixture.solver.script =
        () -> {
          fixture.clock.set(T + 1);
          fixture.solver.publish(new Sample("late", SimpleScore.ONE), true, 0);
        };
    assertThat(fixture.evaluate().status()).isEqualTo(EvaluationStatus.INCOMPLETE_RESULT);
    assertThat(fixture.assessed).isNull();
  }

  @Test
  void initializedStartingEventCountsAndFirstScoreTieRetainsSnapshot() {
    var fixture = new Fixture();
    var initial = new Sample("initial", SimpleScore.ONE);
    fixture.solver.script =
        () -> {
          fixture.solver.publish(initial, true, 0);
          fixture.clock.set(T / 2);
          fixture.solver.publish(new Sample("equal-later", SimpleScore.ONE), true, 500);
        };
    var result = fixture.evaluate();
    assertThat(result.status()).isEqualTo(EvaluationStatus.COMPLETE);
    assertThat(result.selectedObserverNanos()).isZero();
    assertThat(fixture.assessed).isSameAs(initial);
  }

  @Test
  void absentUninitializedAndStructurallyFlawedEventsNeverBecomeResults() {
    for (int scenario = 0; scenario < 3; scenario++) {
      var fixture = new Fixture();
      int selectedScenario = scenario;
      fixture.solver.script =
          () -> {
            if (selectedScenario == 1) {
              fixture.solver.publish(new Sample("uninitialized", SimpleScore.ONE), false, 0);
            } else if (selectedScenario == 2) {
              fixture.solver.publish(new Sample("structural", new SimpleScore(-1, 100)), true, 0);
            }
            fixture.solver.returned = new Sample("complete-returned", SimpleScore.ONE);
          };
      assertThat(fixture.evaluate().status()).isEqualTo(EvaluationStatus.INCOMPLETE_RESULT);
      assertThat(fixture.assessed).isNull();
    }
  }

  @Test
  void delayedWatchdogCannotCreditReturnAtGraceBoundary() {
    var fixture = new Fixture();
    fixture.solver.script =
        () -> {
          fixture.solver.publish(new Sample("initial", SimpleScore.ONE), true, 0);
          fixture.clock.set(T + Duration.ofSeconds(1).toNanos());
          // Deliberately do not run the watchdog: actual elapsed return time must decide.
        };
    var result = fixture.evaluate();
    assertThat(result.status()).isEqualTo(EvaluationStatus.BUDGET_EXCEEDED);
    assertThat(fixture.solver.terminateCalls).isZero();
    assertThat(fixture.assessed).isNull();
  }

  @Test
  void ordinaryReturnOneNanosecondBeforeGraceKeepsOnTimeSnapshot() {
    var fixture = new Fixture();
    fixture.solver.script =
        () -> {
          fixture.solver.publish(new Sample("initial", SimpleScore.ONE), true, 0);
          fixture.clock.set(2 * T - 1);
        };
    assertThat(fixture.evaluate().status()).isEqualTo(EvaluationStatus.COMPLETE);
  }

  @Test
  void watchdogDelayedAcrossSolveReturnCannotInvalidateOnTimeSnapshot() {
    var fixture = new Fixture();
    var clockSampleStarted = new CountDownLatch(1);
    var resumeClockSample = new CountDownLatch(1);
    var watchdogThread =
        new Thread(() -> fixture.watchdog.check.run(), "greybay-cutoff-test-watchdog");
    fixture.nanoTime =
        () -> {
          if (Thread.currentThread() == watchdogThread) {
            // Pause after the watchdog's initial completion check but before sampling time.
            clockSampleStarted.countDown();
            await(resumeClockSample);
          }
          return fixture.clock.get();
        };
    fixture.solver.script =
        () -> {
          fixture.solver.publish(new Sample("initial", SimpleScore.ONE), true, 0);
          fixture.clock.set(3 * T / 2);
          watchdogThread.start();
          await(clockSampleStarted);
        };
    fixture.watchdog.close =
        () -> {
          // Closing begins after the solve-return time and completion flag were recorded.
          fixture.clock.set(2 * T);
          resumeClockSample.countDown();
          try {
            assertThat(watchdogThread.join(Duration.ofSeconds(5))).isTrue();
          } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while joining the watchdog.", failure);
          }
        };

    var result = fixture.evaluate();

    assertThat(result.status()).isEqualTo(EvaluationStatus.COMPLETE);
    assertThat(result.score()).isEqualTo(SimpleScore.ONE);
    assertThat(result.selectedObserverNanos()).isZero();
    assertThat(result.solveElapsedNanos()).isEqualTo(3 * T / 2);
    assertThat(fixture.solver.terminateCalls).isZero();
    assertThat(fixture.stoppingCalls).isZero();
    assertThat(fixture.actions)
        .containsSubsequence(
            "solve-returned", "watchdog-closed", "workers-ended", "listener-removed", "assessed");
  }

  @Test
  void watchdogRequestsTerminationAtGraceAndKeepsOwnershipUntilResourcesEnd() {
    var fixture = new Fixture();
    fixture.solver.script =
        () -> {
          fixture.clock.set(2 * T);
          fixture.watchdog.check.run();
          assertThat(fixture.stoppingCalls).isEqualTo(1);
          assertThat(fixture.solver.terminateCalls).isEqualTo(1);
          assertThat(fixture.actions).doesNotContain("workers-ended", "assessed");
        };
    assertThat(fixture.evaluate().status()).isEqualTo(EvaluationStatus.BUDGET_EXCEEDED);
    assertThat(fixture.actions)
        .containsSubsequence(
            "solve-returned", "watchdog-closed", "workers-ended", "listener-removed");
  }

  @Test
  void stopRequestRemainsStickyAcrossSolverStartupTerminationReset() {
    var fixture = new Fixture();
    fixture.solver.script =
        () -> {
          fixture.stop.set(true);
          fixture.watchdog.check.run();
          assertThat(fixture.solver.terminating).isTrue();
          fixture.solver.terminating = false; // Models the real startup reset.
          fixture.watchdog.check.run();
          assertThat(fixture.solver.terminating).isTrue();
        };
    assertThat(fixture.evaluate().status()).isEqualTo(EvaluationStatus.INTERRUPTED);
    assertThat(fixture.solver.terminateCalls).isEqualTo(2);
  }

  @Test
  void stopAfterSessionConstructionDisposesWithoutEnteringSolve() {
    var fixture = new Fixture();
    fixture.afterBuild = () -> fixture.stop.set(true);
    assertThat(fixture.evaluate().status()).isEqualTo(EvaluationStatus.INTERRUPTED);
    assertThat(fixture.actions)
        .contains("disposed", "listener-removed")
        .doesNotContain("solve-started", "workers-ended");
  }

  @Test
  void stopDuringWatchdogInstallationDisposesWithoutEnteringSolve() {
    var fixture = new Fixture();
    fixture.watchdog.install = () -> fixture.stop.set(true);
    assertThat(fixture.evaluate().status()).isEqualTo(EvaluationStatus.INTERRUPTED);
    assertThat(fixture.actions)
        .contains("watchdog-closed", "disposed")
        .doesNotContain("solve-started");
  }

  @Test
  void ownershipEndsBeforeAssessmentAndBusinessVerification() {
    var fixture = Fixture.withInitialEvent();
    fixture.verifier =
        verifier(
            solution -> {
              assertThat(fixture.actions)
                  .containsSubsequence("workers-ended", "listener-removed", "assessed");
              fixture.actions.add("verified");
              return VerificationResult.passed();
            });
    var result = fixture.evaluate();
    assertThat(result.verification().status()).isEqualTo(VerificationResult.Status.PASSED);
    assertThat(fixture.actions)
        .containsSubsequence(
            "solve-returned",
            "watchdog-closed",
            "workers-ended",
            "listener-removed",
            "assessed",
            "verified");
  }

  @Test
  void candidateSolverAndFactoryExceptionsAreFailedObservations() {
    var fixture = new Fixture();
    fixture.solver.script =
        () -> {
          throw new IllegalArgumentException("candidate solver");
        };
    var result = fixture.evaluate();
    assertThat(result.status()).isEqualTo(EvaluationStatus.FAILED);
    assertThat(result.failureMessage()).isEqualTo("candidate solver");
    assertThat(fixture.actions).contains("workers-ended", "listener-removed");

    fixture = new Fixture();
    fixture.afterBuild =
        () -> {
          throw new IllegalArgumentException("candidate factory");
        };
    result = fixture.evaluate();
    assertThat(result.status()).isEqualTo(EvaluationStatus.FAILED);
    assertThat(result.failureMessage()).isEqualTo("candidate factory");
  }

  @Test
  void fatalCleanupErrorDoesNotBecomeAnOrdinaryFailedCandidate() {
    var fixture = new Fixture();
    var primary = new IllegalArgumentException("candidate solver");
    var fatal = new LinkageError("cleanup linkage");
    fixture.solver.script =
        () -> {
          throw primary;
        };
    fixture.awaitWorkers =
        () -> {
          throw fatal;
        };
    assertThatThrownBy(fixture::evaluate).isSameAs(fatal);
    assertThat(fixture.actions).contains("listener-removed");
  }

  @Test
  void listenerMeasurementFailureStopsStudyInsteadOfBecomingCandidateLoss() {
    var fixture = new Fixture();
    fixture.solver.script =
        () -> {
          fixture.clock.set(-1);
          try {
            fixture.solver.publish(new Sample("invalid-clock", SimpleScore.ONE), true, 0);
          } finally {
            fixture.clock.set(
                0); // Keep cleanup clock valid; the listener failure must retain severity.
          }
        };
    assertThatThrownBy(fixture::evaluate).isInstanceOf(EvaluationMeasurementException.class);
  }

  @Test
  void watchdogFailureStopsStudyAndStillReleasesOwnership() {
    var fixture = new Fixture();
    fixture.solver.terminateFailure = new IllegalStateException("termination callback");
    fixture.solver.script =
        () -> {
          fixture.stop.set(true);
          fixture.watchdog.check.run();
        };
    assertThatThrownBy(fixture::evaluate)
        .isInstanceOf(EvaluationMeasurementException.class)
        .hasMessageContaining("watchdog");
    assertThat(fixture.actions).contains("workers-ended", "listener-removed");
  }

  @Test
  void resourceCleanupFailuresStopStudyAndContinueRemainingCleanup() {
    var fixture = Fixture.withInitialEvent();
    fixture.watchdog.close =
        () -> {
          throw new IllegalStateException("watchdog cleanup");
        };
    assertThatThrownBy(fixture::evaluate)
        .isInstanceOf(EvaluationMeasurementException.class)
        .hasMessageContaining("resource lifecycle");
    assertThat(fixture.actions).contains("workers-ended", "listener-removed");

    var uninvoked = new Fixture();
    uninvoked.afterBuild = () -> uninvoked.stop.set(true);
    uninvoked.dispose =
        () -> {
          throw new IllegalStateException("dispose failure");
        };
    assertThatThrownBy(uninvoked::evaluate).isInstanceOf(EvaluationMeasurementException.class);
    assertThat(uninvoked.actions).contains("listener-removed").doesNotContain("solve-started");
  }

  @Test
  void listenerRegistrationFailureDisposesUninvokedSolver() {
    var fixture = new Fixture();
    fixture.solver.addFailure = new IllegalStateException("listener registration");
    assertThatThrownBy(fixture::evaluate).isInstanceOf(EvaluationMeasurementException.class);
    assertThat(fixture.actions)
        .contains("disposed")
        .doesNotContain("solve-started", "workers-ended");
  }

  @Test
  void stopBeforeEvaluationDoesNotLoadOrConstructAnything() {
    var fixture = new Fixture();
    fixture.stop.set(true);
    fixture.load =
        () -> {
          throw new AssertionError("load must not run");
        };
    assertThat(fixture.evaluate().status()).isEqualTo(EvaluationStatus.INTERRUPTED);
    assertThat(fixture.actions).isEmpty();
  }

  @Test
  void stopDuringVerificationPreservesEvidenceButDoesNotCommitEligibleOutcome() {
    var fixture = Fixture.withInitialEvent();
    fixture.verifier =
        verifier(
            sample -> {
              fixture.stop.set(true);
              return VerificationResult.passed();
            });
    var result = fixture.evaluate();
    assertThat(result.status()).isEqualTo(EvaluationStatus.INTERRUPTED);
    assertThat(result.verification().status()).isEqualTo(VerificationResult.Status.PASSED);
    assertThat(result.eligible()).isFalse();
  }

  @Test
  void scoreAndInitializationMismatchStopStudy() {
    var scoreMismatch = Fixture.withInitialEvent();
    scoreMismatch.assessment = ignored -> new SolutionAssessment<>(SimpleScore.of(100), 0);
    assertThatThrownBy(scoreMismatch::evaluate)
        .isInstanceOf(EvaluationMeasurementException.class)
        .hasMessageContaining("differs from assessment");
    var initializationMismatch = Fixture.withInitialEvent();
    initializationMismatch.assessment = sample -> new SolutionAssessment<>(sample.score(), 1);
    assertThatThrownBy(initializationMismatch::evaluate)
        .isInstanceOf(EvaluationMeasurementException.class)
        .hasMessageContaining("missingRequiredAssignmentCount=1");
  }

  @Test
  void structuralAssessmentVerdictIsInvalidButUnexpectedScoringExceptionStopsStudy() {
    var invalid = Fixture.withInitialEvent();
    invalid.assessment =
        sample -> {
          throw new InconsistentSolutionException("assessment", sample, List.of());
        };
    assertThat(invalid.evaluate().status()).isEqualTo(EvaluationStatus.INVALID_RESULT);
    var failure = Fixture.withInitialEvent();
    failure.assessment =
        sample -> {
          throw new IllegalStateException("broken scorer");
        };
    assertThatThrownBy(failure::evaluate)
        .isInstanceOf(EvaluationMeasurementException.class)
        .hasMessageContaining("Assessment failed");
  }

  @Test
  void verifierInvalidVerdictAndExceptionHaveDifferentSeverity() {
    var invalid = Fixture.withInitialEvent();
    invalid.verifier = verifier(sample -> VerificationResult.invalid("business replay failed"));
    var outcome = invalid.evaluate();
    assertThat(outcome.status()).isEqualTo(EvaluationStatus.INVALID_RESULT);
    assertThat(outcome.verification().message()).isEqualTo("business replay failed");

    var failure = Fixture.withInitialEvent();
    failure.verifier =
        verifier(
            sample -> {
              throw new IllegalStateException("verifier crashed");
            });
    assertThatThrownBy(failure::evaluate)
        .isInstanceOf(EvaluationMeasurementException.class)
        .hasMessageContaining("Business verifier failed");
  }

  @Test
  void changedProblemIdentityStopsBeforeSessionCreation() {
    var fixture = new Fixture();
    fixture.descriptor =
        new ProblemDescriptor("problem", "changed-input", DESCRIPTOR.capabilities());
    assertThatThrownBy(fixture::evaluate)
        .isInstanceOf(EvaluationMeasurementException.class)
        .hasMessageContaining("Cannot load and verify");
    assertThat(fixture.actions).doesNotContain("session-built", "solve-started");
  }

  @Test
  void seedIsExplicitAndPreparationTimeIsOutsideObserverBudget() {
    var fixture = Fixture.withInitialEvent();
    fixture.load = () -> fixture.clock.set(7 * T);
    var config = new SolverConfig().withRandomSeed(42L);
    var result =
        fixture
            .evaluator()
            .evaluate(config, fixture.adapter(), DESCRIPTOR, 19L, HORIZON, null, fixture.stop::get);
    assertThat(fixture.receivedConfig.getRandomSeed()).isEqualTo(19L);
    assertThat(config.getRandomSeed()).isEqualTo(42L);
    assertThat(result.preparationElapsedNanos()).isEqualTo(7 * T);
    assertThat(result.selectedObserverNanos()).isZero();
  }

  @Test
  void outcomeRoundTripChecksOriginalTypeRawScoreAndExactEncoding() {
    var result = Fixture.withInitialEvent().evaluate();
    assertThat(EvaluationOutcome.fromMap(result.toMap(), SimpleScore::parseScore))
        .isEqualTo(result);
    var changed = new java.util.LinkedHashMap<>(result.toMap());
    @SuppressWarnings("unchecked")
    var score = new java.util.LinkedHashMap<>((java.util.Map<String, Object>) changed.get("score"));
    score.put("type", "wrong-score-type");
    changed.put("score", score);
    assertThatThrownBy(() -> EvaluationOutcome.fromMap(changed, SimpleScore::parseScore))
        .isInstanceOf(EvaluationMeasurementException.class);
  }

  @Test
  void validatesRepresentableWholeMillisecondHorizonAndCeilingGrace() {
    assertThat(TrialEvaluator.grace(Duration.ofMillis(20_001))).isEqualTo(Duration.ofMillis(1001));
    assertThat(TrialEvaluator.grace(Duration.ofMillis(1))).isEqualTo(Duration.ofSeconds(1));
    for (var invalid :
        List.of(
            Duration.ZERO,
            Duration.ofNanos(1),
            Duration.ofMillis(-1),
            Duration.ofSeconds(Long.MAX_VALUE))) {
      assertThatThrownBy(() -> TrialEvaluator.horizonNanos(invalid))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  private static BusinessVerifier<Sample> verifier(Function<Sample, VerificationResult> action) {
    return new BusinessVerifier<>() {
      @Override
      public ComponentIdentity identity() {
        return new ComponentIdentity("verifier", "1");
      }

      @Override
      public VerificationResult verify(String id, Sample solution) {
        return action.apply(solution);
      }
    };
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while awaiting the watchdog.", failure);
    }
  }

  private record Sample(String id, SimpleScore score) {}

  private static final class Fixture {
    final AtomicLong clock = new AtomicLong();
    final AtomicBoolean stop = new AtomicBoolean();
    final List<String> actions = new ArrayList<>();
    final FakeSolver solver = new FakeSolver(actions);
    final ManualWatchdog watchdog = new ManualWatchdog(actions);
    LongSupplier nanoTime = clock::get;
    Runnable afterBuild = () -> {};
    Runnable load = () -> {};
    Runnable awaitWorkers = () -> {};
    Runnable dispose = () -> {};
    Function<Sample, SolutionAssessment<SimpleScore>> assessment =
        sample -> new SolutionAssessment<>(sample.score(), 0);
    BusinessVerifier<Sample> verifier;
    ProblemDescriptor descriptor = DESCRIPTOR;
    SolverConfig receivedConfig;
    Sample assessed;
    int stoppingCalls;

    static Fixture withInitialEvent() {
      var fixture = new Fixture();
      fixture.solver.script =
          () -> fixture.solver.publish(new Sample("initial", SimpleScore.ONE), true, 0);
      return fixture;
    }

    EvaluationOutcome<SimpleScore> evaluate() {
      return evaluator()
          .evaluate(
              new SolverConfig(),
              adapter(),
              DESCRIPTOR,
              11L,
              HORIZON,
              verifier,
              stop::get,
              () -> stoppingCalls++);
    }

    TrialEvaluator<Sample, SimpleScore> evaluator() {
      return new TrialEvaluator<>(
          nanoTime,
          config -> {
            receivedConfig = config;
            actions.add("session-built");
            afterBuild.run();
            return new EvaluationSession<>() {
              @Override
              public Solver<Sample> solver() {
                return solver;
              }

              @Override
              public SolutionAssessment<SimpleScore> assess(Sample sample) {
                actions.add("assessed");
                assessed = sample;
                return assessment.apply(sample);
              }

              @Override
              public void awaitWorkers(Runnable stopping) {
                awaitWorkers.run();
                actions.add("workers-ended");
              }

              @Override
              public void disposeBeforeSolve() {
                dispose.run();
                actions.add("disposed");
              }
            };
          },
          watchdog);
    }

    ProblemAdapter<Sample> adapter() {
      return new ProblemAdapter<>() {
        @Override
        public ComponentIdentity identity() {
          return new ComponentIdentity("adapter", "1");
        }

        @Override
        public ProblemDescriptor describe(String id) {
          return descriptor;
        }

        @Override
        public Sample loadFresh(String id) {
          load.run();
          return new Sample("input", SimpleScore.ZERO);
        }
      };
    }
  }

  private static final class ManualWatchdog implements TrialEvaluator.Watchdog {
    final List<String> actions;
    Runnable install = () -> {};
    Runnable close = () -> {};
    Runnable check;

    ManualWatchdog(List<String> actions) {
      this.actions = actions;
    }

    @Override
    public AutoCloseable watch(Runnable check) {
      this.check = check;
      install.run();
      return () -> {
        close.run();
        actions.add("watchdog-closed");
      };
    }
  }

  private static final class FakeSolver implements Solver<Sample> {
    final List<String> actions;
    Runnable script = () -> {};
    SolverEventListener<Sample> listener;
    Sample returned = new Sample("returned", SimpleScore.ZERO);
    RuntimeException terminateFailure;
    RuntimeException addFailure;
    boolean solving;
    boolean terminating;
    int terminateCalls;

    FakeSolver(List<String> actions) {
      this.actions = actions;
    }

    @Override
    public Sample solve(Sample problem) {
      actions.add("solve-started");
      solving = true;
      try {
        script.run();
        return returned;
      } finally {
        solving = false;
        actions.add("solve-returned");
      }
    }

    void publish(Sample sample, boolean initialized, long solverMillis) {
      listener.bestSolutionChanged(
          new BestSolutionChangedEvent<>() {
            @Override
            public long getTimeMillisSpent() {
              return solverMillis;
            }

            @Override
            public EventProducerId getProducerId() {
              return EventProducerId.solvingStarted();
            }

            @Override
            public Sample getNewBestSolution() {
              return sample;
            }

            @Override
            public SimpleScore getNewBestScore() {
              return sample.score();
            }

            @Override
            public boolean isNewBestSolutionInitialized() {
              return initialized;
            }

            @Override
            public boolean isEveryProblemChangeProcessed() {
              return true;
            }
          });
    }

    @Override
    public boolean terminateEarly() {
      terminateCalls++;
      if (terminateFailure != null) throw terminateFailure;
      terminating = true;
      return true;
    }

    @Override
    public boolean isSolving() {
      return solving;
    }

    @Override
    public boolean isTerminateEarly() {
      return terminating;
    }

    @Override
    public void addProblemChange(ProblemChange<Sample> change) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addProblemChanges(List<ProblemChange<Sample>> changes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEveryProblemChangeProcessed() {
      return true;
    }

    @Override
    public void addEventListener(SolverEventListener<Sample> listener) {
      if (addFailure != null) throw addFailure;
      this.listener = listener;
      actions.add("listener-added");
    }

    @Override
    public void removeEventListener(SolverEventListener<Sample> listener) {
      assertThat(this.listener).isSameAs(listener);
      this.listener = null;
      actions.add("listener-removed");
    }
  }
}
