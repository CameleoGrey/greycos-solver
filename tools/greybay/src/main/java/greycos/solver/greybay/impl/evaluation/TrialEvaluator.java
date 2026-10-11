package greycos.solver.greybay.impl.evaluation;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;

import greycos.solver.core.api.cotwin.variable.InconsistentSolutionException;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.SolutionAssessment;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.event.SolverEventListener;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.greybay.api.BusinessVerifier;
import greycos.solver.greybay.api.EvaluationStatus;
import greycos.solver.greybay.api.ProblemAdapter;
import greycos.solver.greybay.api.ProblemDescriptor;
import greycos.solver.greybay.api.VerificationResult;
import greycos.solver.greybay.impl.objective.ScoreCopies;

/** Sequential, full-horizon evaluation with a synchronous availability cutoff. */
public final class TrialEvaluator<Solution_, Score_ extends Score<Score_>> {
  @FunctionalInterface
  public interface Watchdog {
    /** Installs repeated checks; close must wait for an in-progress check to finish. */
    AutoCloseable watch(Runnable check);
  }

  private final LongSupplier nanoTime;
  private final Function<SolverConfig, EvaluationSession<Solution_, Score_>> sessionFactory;
  private final Watchdog watchdog;

  public TrialEvaluator() {
    this(System::nanoTime, TrialEvaluator::openSession, TrialEvaluator::startWatchdog);
  }

  public TrialEvaluator(
      LongSupplier nanoTime,
      Function<SolverConfig, EvaluationSession<Solution_, Score_>> sessionFactory,
      Watchdog watchdog) {
    this.nanoTime = Objects.requireNonNull(nanoTime);
    this.sessionFactory = Objects.requireNonNull(sessionFactory);
    this.watchdog = Objects.requireNonNull(watchdog);
  }

  public EvaluationOutcome<Score_> evaluate(
      SolverConfig effectiveConfig,
      ProblemAdapter<Solution_> adapter,
      ProblemDescriptor expected,
      long seed,
      Duration horizon,
      BusinessVerifier<Solution_> verifier,
      BooleanSupplier stopRequested) {
    return evaluate(
        effectiveConfig, adapter, expected, seed, horizon, verifier, stopRequested, () -> {});
  }

  public EvaluationOutcome<Score_> evaluate(
      SolverConfig effectiveConfig,
      ProblemAdapter<Solution_> adapter,
      ProblemDescriptor expected,
      long seed,
      Duration horizon,
      BusinessVerifier<Solution_> verifier,
      BooleanSupplier stopRequested,
      Runnable stopping) {
    long budgetNanos = horizonNanos(horizon);
    long graceBoundary = Math.addExact(budgetNanos, grace(horizon).toNanos());
    long preparationStart = nanoTime.getAsLong();
    if (stopRequested.getAsBoolean()) return absent(EvaluationStatus.INTERRUPTED, 0, 0, null);
    Solution_ problem;
    try {
      validateDescriptor(adapter, expected);
      problem = Objects.requireNonNull(adapter.loadFresh(expected.id()), "loaded problem");
      validateDescriptor(adapter, expected);
    } catch (RuntimeException failure) {
      throw new EvaluationMeasurementException(
          "Cannot load and verify problem (" + expected.id() + ").", failure);
    }
    if (stopRequested.getAsBoolean())
      return absent(EvaluationStatus.INTERRUPTED, elapsed(preparationStart), 0, null);
    var config = new SolverConfig(effectiveConfig);
    config.setRandomSeed(seed);
    EvaluationSession<Solution_, Score_> session;
    try {
      session = sessionFactory.apply(config);
    } catch (RuntimeException failure) {
      return absent(
          stopRequested.getAsBoolean() ? EvaluationStatus.INTERRUPTED : EvaluationStatus.FAILED,
          elapsed(preparationStart),
          0,
          failure);
    }
    var solver = session.solver();
    var selected = new AtomicReference<Snapshot<Solution_, Score_>>();
    var solveStarted = new AtomicBoolean();
    var returned = new AtomicBoolean();
    var watchdogFailure = new AtomicReference<Throwable>();
    long[] solveStart = new long[1];
    SolverEventListener<Solution_> listener =
        event -> {
          try {
            long observed = elapsed(solveStart[0]);
            if (observed > budgetNanos || !event.isNewBestSolutionInitialized()) return;
            @SuppressWarnings("unchecked")
            Score_ score = (Score_) event.getNewBestScore();
            if (score.structuralScore() != 0) return;
            var previous = selected.get();
            if (previous == null || score.compareTo(previous.score()) > 0) {
              selected.set(
                  new Snapshot<>(
                      event.getNewBestSolution(),
                      ScoreCopies.copy(score),
                      observed,
                      event.getTimeMillisSpent()));
            }
          } catch (EvaluationMeasurementException failure) {
            throw failure;
          } catch (RuntimeException failure) {
            throw new EvaluationMeasurementException(
                "The synchronous best-solution observer failed.", failure);
          }
        };
    long preparationNanos = 0;
    long solveNanos = 0;
    Throwable solveFailure = null;
    boolean lifecycleFailure = false;
    boolean listenerAttached = false;
    AutoCloseable watch = null;
    try {
      solver.addEventListener(listener);
      listenerAttached = true;
      if (!stopRequested.getAsBoolean()) {
        watch =
            watchdog.watch(
                () -> {
                  if (!solveStarted.get() || returned.get()) return;
                  try {
                    boolean budgetExceeded = elapsed(solveStart[0]) >= graceBoundary;
                    // The solve may have returned while this watchdog check was delayed.
                    if (returned.get()) return;
                    if (budgetExceeded || stopRequested.getAsBoolean()) {
                      stopping.run();
                      // Repeated requests survive DefaultSolver's startup termination reset.
                      solver.terminateEarly();
                    }
                  } catch (RuntimeException | Error failure) {
                    watchdogFailure.compareAndSet(null, failure);
                  }
                });
        if (!stopRequested.getAsBoolean()) {
          preparationNanos = elapsed(preparationStart);
          solveStart[0] = nanoTime.getAsLong();
          solveStarted.set(true);
          try {
            solver.solve(problem);
          } finally {
            solveNanos = elapsed(solveStart[0]);
            returned.set(true);
          }
        }
      }
    } catch (RuntimeException | Error failure) {
      solveFailure = failure;
      lifecycleFailure = !solveStarted.get();
    } finally {
      if (!solveStarted.get()) preparationNanos = elapsed(preparationStart);
      try {
        if (watch != null) watch.close();
      } catch (Exception | Error failure) {
        solveFailure = combine(solveFailure, failure);
        lifecycleFailure = true;
      }
      try {
        if (solveStarted.get()) session.awaitWorkers(stopping);
        else session.disposeBeforeSolve();
      } catch (RuntimeException | Error failure) {
        solveFailure = combine(solveFailure, failure);
        lifecycleFailure = true;
      }
      try {
        if (listenerAttached) solver.removeEventListener(listener);
      } catch (RuntimeException | Error failure) {
        solveFailure = combine(solveFailure, failure);
        lifecycleFailure = true;
      }
    }
    if (solveFailure instanceof Error error) throw error;
    if (solveFailure instanceof EvaluationMeasurementException measurement) throw measurement;
    if (lifecycleFailure)
      throw new EvaluationMeasurementException(
          "The evaluation resource lifecycle failed.", solveFailure);
    if (watchdogFailure.get() instanceof Error error) throw error;
    if (watchdogFailure.get() != null)
      throw new EvaluationMeasurementException(
          "The evaluation watchdog failed.", watchdogFailure.get());
    if (stopRequested.getAsBoolean() || Thread.currentThread().isInterrupted())
      return absent(EvaluationStatus.INTERRUPTED, preparationNanos, solveNanos, solveFailure);
    if (solveNanos >= graceBoundary)
      return absent(EvaluationStatus.BUDGET_EXCEEDED, preparationNanos, solveNanos, solveFailure);
    if (solveFailure != null)
      return absent(EvaluationStatus.FAILED, preparationNanos, solveNanos, solveFailure);
    var snapshot = selected.get();
    if (snapshot == null)
      return absent(EvaluationStatus.INCOMPLETE_RESULT, preparationNanos, solveNanos, null);
    long assessmentStart = nanoTime.getAsLong();
    SolutionAssessment<Score_> assessment;
    try {
      assessment = session.assess(snapshot.solution());
    } catch (InconsistentSolutionException failure) {
      return outcome(
          EvaluationStatus.INVALID_RESULT,
          snapshot.score(),
          -1,
          snapshot,
          solveNanos,
          preparationNanos,
          elapsed(assessmentStart),
          VerificationResult.notConfigured(),
          failure);
    } catch (RuntimeException failure) {
      throw new EvaluationMeasurementException(
          "Assessment failed for problem (" + expected.id() + ").", failure);
    }
    if (!snapshot.score().equals(assessment.score()) || !assessment.isInitialized()) {
      throw new EvaluationMeasurementException(
          "Captured score/initialization for problem ("
              + expected.id()
              + ") differs from assessment: captured ("
              + snapshot.score()
              + ", initialized), assessed ("
              + assessment.score()
              + ", missingRequiredAssignmentCount="
              + assessment.missingRequiredAssignmentCount()
              + ").");
    }
    VerificationResult verification = VerificationResult.notConfigured();
    EvaluationStatus status = EvaluationStatus.COMPLETE;
    if (!assessment.isStructurallySound() || !finite(assessment.score())) {
      status = EvaluationStatus.INVALID_RESULT;
    } else if (verifier != null) {
      try {
        verification =
            Objects.requireNonNull(
                verifier.verify(expected.id(), snapshot.solution()), "verification result");
        if (verification.status() == VerificationResult.Status.NOT_CONFIGURED)
          throw new IllegalStateException("The configured verifier returned NOT_CONFIGURED.");
        if (verification.status() == VerificationResult.Status.INVALID)
          status = EvaluationStatus.INVALID_RESULT;
      } catch (RuntimeException failure) {
        throw new EvaluationMeasurementException(
            "Business verifier failed for problem (" + expected.id() + ").", failure);
      }
    }
    if (stopRequested.getAsBoolean()) status = EvaluationStatus.INTERRUPTED;
    return outcome(
        status,
        assessment.score(),
        assessment.missingRequiredAssignmentCount(),
        snapshot,
        solveNanos,
        preparationNanos,
        elapsed(assessmentStart),
        verification,
        null);
  }

  public static long horizonNanos(Duration horizon) {
    Objects.requireNonNull(horizon, "horizon");
    if (horizon.isNegative() || horizon.isZero() || horizon.getNano() % 1_000_000 != 0)
      throw new IllegalArgumentException(
          "The solve horizon (" + horizon + ") must be positive whole milliseconds.");
    try {
      long nanos = horizon.toNanos();
      Math.addExact(nanos, grace(horizon).toNanos());
      return nanos;
    } catch (ArithmeticException failure) {
      throw new IllegalArgumentException(
          "The solve horizon and grace (" + horizon + ") exceed nanosecond elapsed arithmetic.",
          failure);
    }
  }

  public static Duration grace(Duration horizon) {
    long milliseconds = horizon.toMillis();
    long fivePercentCeiling = milliseconds / 20 + (milliseconds % 20 == 0 ? 0 : 1);
    return Duration.ofMillis(Math.max(1000, fivePercentCeiling));
  }

  private long elapsed(long start) {
    long result = nanoTime.getAsLong() - start;
    if (result < 0)
      throw new EvaluationMeasurementException(
          "The monotonic elapsed time is negative (" + result + ").");
    return result;
  }

  private static <Solution_> void validateDescriptor(
      ProblemAdapter<Solution_> adapter, ProblemDescriptor expected) {
    var actual = adapter.describe(expected.id());
    if (!expected.equals(actual))
      throw new EvaluationMeasurementException(
          "Problem identity changed: expected (" + expected + "), actual (" + actual + ").");
  }

  private static boolean finite(Score<?> score) {
    for (Number value : score.toLevelNumbers()) {
      if (value instanceof Double d && !Double.isFinite(d)
          || value instanceof Float f && !Float.isFinite(f)) return false;
    }
    return true;
  }

  private EvaluationOutcome<Score_> absent(
      EvaluationStatus status, long preparation, long solve, Throwable failure) {
    return outcome(
        status, null, -1, null, solve, preparation, 0, VerificationResult.notConfigured(), failure);
  }

  private EvaluationOutcome<Score_> outcome(
      EvaluationStatus status,
      Score_ score,
      int missing,
      Snapshot<Solution_, Score_> snapshot,
      long solve,
      long preparation,
      long assessment,
      VerificationResult verification,
      Throwable failure) {
    return new EvaluationOutcome<>(
        status,
        score,
        missing,
        snapshot == null ? -1 : snapshot.observerNanos(),
        snapshot == null ? -1 : snapshot.solverMillis(),
        solve,
        preparation,
        assessment,
        verification,
        failure == null ? null : failure.getClass().getName(),
        failure == null ? null : failure.getMessage(),
        failure == null
            ? List.of()
            : Arrays.stream(failure.getSuppressed()).map(Throwable::toString).toList());
  }

  private static Throwable combine(Throwable primary, Throwable other) {
    if (primary == null) return other;
    if (other instanceof Error && !(primary instanceof Error)) {
      other.addSuppressed(primary);
      return other;
    }
    if (primary != other) primary.addSuppressed(other);
    return primary;
  }

  private static <Solution_, Score_ extends Score<Score_>>
      EvaluationSession<Solution_, Score_> openSession(SolverConfig config) {
    SolverFactory<Solution_> factory = SolverFactory.create(config);
    var solver = factory.buildSolver();
    return new EvaluationSession<>() {
      @Override
      public Solver<Solution_> solver() {
        return solver;
      }

      @Override
      public SolutionAssessment<Score_> assess(Solution_ solution) {
        return SolutionManager.<Solution_, Score_>create(factory).assess(solution);
      }

      @Override
      public void awaitWorkers(Runnable stopping) {
        var registry = ((DefaultSolver<Solution_>) solver).getSolverScope().getWorkerRegistry();
        boolean interrupted = false;
        Throwable callbackFailure = null;
        try {
          while (!registry.activeWorkerLabels().isEmpty()) {
            try {
              stopping.run();
            } catch (RuntimeException | Error failure) {
              callbackFailure = combine(callbackFailure, failure);
            }
            try {
              registry.awaitQuiescence(Duration.ofMillis(100));
            } catch (InterruptedException ignored) {
              interrupted = true;
            }
          }
        } finally {
          if (interrupted) Thread.currentThread().interrupt();
        }
        if (callbackFailure instanceof Error error) throw error;
        if (callbackFailure instanceof RuntimeException exception) throw exception;
      }

      @Override
      public void disposeBeforeSolve() {
        disposeUninvoked(solver);
      }
    };
  }

  /** Only for a solver whose solve method has never been invoked. */
  public static void disposeUninvoked(Solver<?> solver) {
    if (!(solver instanceof DefaultSolver<?> builtIn))
      throw new IllegalArgumentException(
          "Unsupported internal solver implementation (" + solver.getClass().getName() + ").");
    builtIn.getSolverScope().getScoreDirector().close();
  }

  private static AutoCloseable startWatchdog(Runnable check) {
    var executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              var thread = new Thread(runnable, "greybay-evaluation-watchdog");
              thread.setDaemon(true);
              return thread;
            });
    var future = executor.scheduleAtFixedRate(check, 0, 10, TimeUnit.MILLISECONDS);
    return () -> {
      future.cancel(false);
      executor.shutdown();
      boolean interrupted = false;
      try {
        while (!executor.isTerminated()) {
          try {
            executor.awaitTermination(100, TimeUnit.MILLISECONDS);
          } catch (InterruptedException ignored) {
            interrupted = true;
          }
        }
      } finally {
        if (interrupted) Thread.currentThread().interrupt();
      }
    };
  }

  private record Snapshot<Solution_, Score_>(
      Solution_ solution, Score_ score, long observerNanos, long solverMillis) {}
}
