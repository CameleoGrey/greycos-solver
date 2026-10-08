package greycos.solver.core.impl.geneticalgorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.impl.solver.thread.DefaultSolverThreadFactory;
import greycos.solver.core.impl.solver.thread.ThreadUtils;

/**
 * Bounded private persistent evaluators for one GA invocation. Population policy, randomness,
 * publication and solver accounting belong exclusively to the creating coordinator thread.
 */
public final class GeneticAlgorithmEvaluatorPool<Solution_, Score_ extends Score<Score_>>
    implements AutoCloseable {
  private final InnerScoreDirector<Solution_, Score_> coordinatorDirector;
  private final GeneticAlgorithmWorkspace<Solution_, Score_> coordinatorWorkspace;
  private final SolverScope<Solution_> solverScope;
  private final ThreadFactory threadFactory;
  private final Thread coordinator = Thread.currentThread();
  private final int workerCount;
  private final Object signal = new Object();
  private final AtomicReference<IllegalStateException> failure = new AtomicReference<>();
  private final List<Worker> workers;
  private final CountDownLatch initialized;
  private volatile boolean stopping;
  private volatile boolean aborting;
  private ExecutorService executor;
  private GeneticAlgorithmEvaluationCodec<Solution_> codec;
  private BiConsumer<Long, InnerScoreDirector<Solution_, Score_>> evaluationObserver;
  private boolean started;
  private boolean joined;
  private int outstanding;
  private int nextWorker;
  private volatile long submittedCount;
  private volatile long consumedCount;
  private volatile long transferredCalculationCount;
  private volatile long setupNanos;
  private volatile long shutdownNanos;

  public GeneticAlgorithmEvaluatorPool(
      InnerScoreDirector<Solution_, Score_> coordinatorDirector,
      GeneticAlgorithmWorkspace<Solution_, Score_> coordinatorWorkspace,
      SolverScope<Solution_> solverScope,
      int workerCount) {
    this(
        coordinatorDirector,
        coordinatorWorkspace,
        solverScope,
        workerCount,
        new DefaultSolverThreadFactory("GeneticAlgorithmEvaluator"));
  }

  public GeneticAlgorithmEvaluatorPool(
      InnerScoreDirector<Solution_, Score_> coordinatorDirector,
      GeneticAlgorithmWorkspace<Solution_, Score_> coordinatorWorkspace,
      SolverScope<Solution_> solverScope,
      int workerCount,
      ThreadFactory threadFactory) {
    if (workerCount < 1) {
      throw new IllegalArgumentException(
          "The genetic algorithm evaluator count (" + workerCount + ") must be positive.");
    }
    this.coordinatorDirector = Objects.requireNonNull(coordinatorDirector);
    this.coordinatorWorkspace = Objects.requireNonNull(coordinatorWorkspace);
    this.solverScope = Objects.requireNonNull(solverScope);
    this.workerCount = workerCount;
    this.threadFactory = Objects.requireNonNull(threadFactory);
    initialized = new CountDownLatch(workerCount);
    var createdWorkers = new ArrayList<Worker>(workerCount);
    for (int i = 0; i < workerCount; i++) createdWorkers.add(new Worker(i));
    workers = List.copyOf(createdWorkers);
  }

  /** Must finish before the coordinator changes any genuine assignment or shadow. */
  public void start() {
    start(() -> false);
  }

  /** Internal correctness harness hook, invoked only on the owning worker after valid scoring. */
  void setEvaluationObserver(BiConsumer<Long, InnerScoreDirector<Solution_, Score_>> observer) {
    requireCoordinator();
    if (started)
      throw new IllegalStateException("Set the evaluator observer before starting workers.");
    evaluationObserver = Objects.requireNonNull(observer);
  }

  /** Returns false after a requested startup cancellation has stopped and joined the workers. */
  public boolean start(BooleanSupplier terminationCheck) {
    requireCoordinator();
    if (started || stopping)
      throw new IllegalStateException("The evaluator pool can only be started once.");
    started = true;
    long start = System.nanoTime();
    Throwable original = null;
    try {
      if (terminationCheck.getAsBoolean()) {
        stopping = true;
        joined = true;
        return false;
      }
      codec = new GeneticAlgorithmEvaluationCodec<>(coordinatorDirector, coordinatorWorkspace);
      executor =
          Executors.newFixedThreadPool(
              workerCount,
              ThreadUtils.requireNonNullThreads(threadFactory, "Genetic Algorithm evaluator"));
      solverScope.getWorkerRegistry().registerExecutor(executor, "Genetic Algorithm evaluator");
      for (var worker : workers) executor.execute(worker);
      while (initialized.getCount() != 0) {
        checkFailure();
        transferCalculationCount();
        if (terminationCheck.getAsBoolean()) {
          abort();
          return false;
        }
        initialized.await(10, TimeUnit.MILLISECONDS);
      }
      checkFailure();
      transferCalculationCount();
      if (terminationCheck.getAsBoolean()) {
        abort();
        return false;
      }
      return true;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      original =
          new IllegalStateException(
              "The genetic algorithm evaluator startup was interrupted.", interrupted);
      throw (IllegalStateException) original;
    } catch (RuntimeException | Error error) {
      original = error;
      throw error;
    } finally {
      setupNanos = System.nanoTime() - start;
      if (original != null) {
        try {
          abort();
        } catch (RuntimeException | Error cleanup) {
          if (cleanup != original) original.addSuppressed(cleanup);
        }
      }
      transferCalculationCount();
    }
  }

  public Ticket<Score_> submit(long workId, GeneticAlgorithmGenome genome) {
    requireCoordinator();
    checkFailure();
    if (!started || stopping || initialized.getCount() != 0) {
      throw new IllegalStateException(
          "The genetic algorithm evaluator pool is not accepting work.");
    }
    if (outstanding >= workerCount) {
      throw new IllegalStateException(
          "The genetic algorithm evaluator window already contains "
              + outstanding
              + " unconsumed jobs.");
    }
    var encoded = codec.encode(genome);
    var ticket = new Ticket<Score_>(this, workId);
    if (!workers.get(nextWorker).jobs.offer(new Job(ticket, encoded))) {
      throw new IllegalStateException("The bounded genetic algorithm evaluator mailbox is full.");
    }
    nextWorker = (nextWorker + 1) % workerCount;
    outstanding++;
    submittedCount++;
    return ticket;
  }

  /** Failures bypass ordered result consumption, including a blocked earlier job. */
  public Result<Score_> poll(Ticket<Score_> ticket, long timeoutMillis)
      throws InterruptedException {
    requireCoordinator();
    if (ticket.owner != this)
      throw new IllegalArgumentException("The evaluator ticket belongs to another pool.");
    if (timeoutMillis < 0)
      throw new IllegalArgumentException("The evaluator poll timeout must be nonnegative.");
    long remaining = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    long start = System.nanoTime();
    synchronized (signal) {
      while (true) {
        transferCalculationCount();
        checkFailure();
        var result = ticket.result;
        if (result != null) {
          if (!ticket.consumed) {
            ticket.consumed = true;
            consumedCount++;
            outstanding--;
          }
          return result;
        }
        if (stopping)
          throw new IllegalStateException("The evaluator ticket was cancelled before completion.");
        if (remaining <= 0) return null;
        TimeUnit.NANOSECONDS.timedWait(
            signal, Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(10)));
        remaining = TimeUnit.MILLISECONDS.toNanos(timeoutMillis) - (System.nanoTime() - start);
      }
    }
  }

  public void checkFailure() {
    var error = failure.get();
    if (error != null) throw error;
  }

  /** Imports actual physical worker work exactly once; never grants logical attempt credit. */
  public long transferCalculationCount() {
    requireCoordinator();
    long total = getCalculationCount();
    long delta = total - transferredCalculationCount;
    if (delta < 0)
      throw new IllegalStateException(
          "Genetic algorithm worker calculation counts moved backwards.");
    if (delta != 0) {
      coordinatorDirector.incrementCalculationCount(delta);
      transferredCalculationCount = total;
    }
    return delta;
  }

  public long getCalculationCount() {
    long total = 0;
    for (var worker : workers) total += worker.calculationCount;
    return total;
  }

  public Diagnostics getDiagnostics() {
    var details = new ArrayList<WorkerDiagnostics>(workers.size());
    int ready = 0;
    int closed = 0;
    long sessions = 0;
    long evaluated = 0;
    for (var worker : workers) {
      details.add(
          new WorkerDiagnostics(
              worker.index,
              worker.calculationCount,
              worker.sessionCount,
              worker.evaluatedCount,
              worker.ready,
              worker.closed));
      if (worker.ready) ready++;
      if (worker.closed) closed++;
      sessions += worker.sessionCount;
      evaluated += worker.evaluatedCount;
    }
    return new Diagnostics(
        workerCount,
        ready,
        closed,
        sessions,
        submittedCount,
        evaluated,
        consumedCount,
        getCalculationCount(),
        transferredCalculationCount,
        setupNanos,
        shutdownNanos,
        List.copyOf(details));
  }

  @Override
  public void close() {
    shutdown(false);
  }

  public void abort() {
    shutdown(true);
  }

  private void shutdown(boolean force) {
    requireCoordinator();
    if (joined) {
      transferCalculationCount();
      checkFailure();
      return;
    }
    long start = System.nanoTime();
    stopping = true;
    aborting |= force;
    for (var worker : workers) worker.jobs.clear();
    wakeCoordinator();
    boolean interrupted = Thread.interrupted();
    try {
      if (executor == null) {
        joined = true;
        return;
      }
      if (force || interrupted || failure.get() != null) executor.shutdownNow();
      else executor.shutdown();
      long deadline =
          System.nanoTime() + TimeUnit.SECONDS.toNanos(ThreadUtils.getDefaultShutdownTimeout());
      while (!executor.isTerminated() && System.nanoTime() < deadline) {
        try {
          executor.awaitTermination(25, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruption) {
          interrupted = true;
          aborting = true;
          executor.shutdownNow();
        }
        if (failure.get() != null) executor.shutdownNow();
        transferCalculationCount();
      }
      if (!executor.isTerminated()) {
        aborting = true;
        executor.shutdownNow();
        try {
          executor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException interruption) {
          interrupted = true;
        }
      }
      joined = executor.isTerminated();
      if (!joined) {
        recordFailure(
            new IllegalStateException(
                "Genetic algorithm evaluator workers did not terminate after cancellation."));
      }
      checkFailure();
    } finally {
      shutdownNanos += System.nanoTime() - start;
      transferCalculationCount();
      if (interrupted) Thread.currentThread().interrupt();
    }
  }

  private void requireCoordinator() {
    if (Thread.currentThread() != coordinator) {
      throw new IllegalStateException(
          "Only the genetic algorithm coordinator may control evaluator work.");
    }
  }

  private void wakeCoordinator() {
    synchronized (signal) {
      signal.notifyAll();
    }
  }

  private void recordFailure(IllegalStateException error) {
    if (!failure.compareAndSet(null, error)) {
      var first = failure.get();
      if (first != error && first.getCause() != error) first.addSuppressed(error);
    }
    aborting = true;
    stopping = true;
    for (var worker : workers) {
      var thread = worker.thread;
      if (thread != null && thread != Thread.currentThread()) thread.interrupt();
    }
    wakeCoordinator();
  }

  public static final class Ticket<Score_ extends Score<Score_>> {
    private final Object owner;
    private final long workId;
    private volatile Result<Score_> result;
    private boolean consumed;

    private Ticket(Object owner, long workId) {
      this.owner = owner;
      this.workId = workId;
    }
  }

  public record Result<Score_ extends Score<Score_>>(
      long workId, boolean valid, InnerScore<Score_> score, int changedAssignmentCount) {}

  /**
   * Session counts observe retained Bavet session identities, excluding scratch assertion sessions.
   */
  public record WorkerDiagnostics(
      int workerIndex,
      long calculationCount,
      long sessionCount,
      long evaluatedCount,
      boolean initialized,
      boolean closed) {}

  /**
   * Final counts are exact once all workers have stopped. Live counts may lag an active
   * transaction.
   */
  public record Diagnostics(
      int workerCount,
      int initializedWorkerCount,
      int closedWorkerCount,
      long sessionCount,
      long submittedCount,
      long evaluatedCount,
      long consumedCount,
      long calculationCount,
      long transferredCalculationCount,
      long setupNanos,
      long shutdownNanos,
      List<WorkerDiagnostics> workers) {
    public Diagnostics {
      workers = List.copyOf(workers);
    }
  }

  private final class Job {
    private final Ticket<Score_> ticket;
    private final GeneticAlgorithmEvaluationCodec.EncodedGenome genome;

    private Job(Ticket<Score_> ticket, GeneticAlgorithmEvaluationCodec.EncodedGenome genome) {
      this.ticket = ticket;
      this.genome = genome;
    }
  }

  private final class Worker implements Runnable {
    private final int index;
    private final LinkedBlockingQueue<Job> jobs = new LinkedBlockingQueue<>(workerCount);
    private volatile Thread thread;
    private volatile long calculationCount;
    private volatile long sessionCount;
    private volatile long evaluatedCount;
    private volatile boolean ready;
    private volatile boolean closed;
    private Object lastSession;

    private Worker(int index) {
      this.index = index;
    }

    @Override
    public void run() {
      thread = Thread.currentThread();
      InnerScoreDirector<Solution_, Score_> director = null;
      long workId = -1;
      try {
        if (stopping) return;
        director = coordinatorDirector.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
        var score = director.calculateScore();
        snapshot(director);
        if (!score.equals(coordinatorWorkspace.score())) {
          throw new IllegalStateException(
              "Evaluator initial score ("
                  + score
                  + ") differs from coordinator score ("
                  + coordinatorWorkspace.score()
                  + ").");
        }
        var workspace = new GeneticAlgorithmWorkspace<>(director, score);
        var decoder = codec.createDecoder(director, workspace);
        ready = true;
        initialized.countDown();
        while (!stopping) {
          var job = jobs.poll(25, TimeUnit.MILLISECONDS);
          if (job == null || stopping) continue;
          workId = job.ticket.workId;
          var genome = decoder.decode(job.genome);
          var previous = workspace.genome();
          var previousScore = workspace.score();
          var transition = workspace.transition(genome);
          Result<Score_> result;
          if (!transition.valid()) {
            result = new Result<>(workId, false, null, transition.changedAssignmentCount());
          } else {
            // A fresh logical evaluation still scores when this worker happens to own its genome.
            var candidateScore = director.calculateScore();
            if (!candidateScore.isFullyAssigned() || candidateScore.isStructurallyFlawed()) {
              workspace.restore(previous, previousScore);
              result = new Result<>(workId, false, null, transition.changedAssignmentCount());
            } else {
              var environment = director.getEnvironmentMode();
              var context = "Genetic Algorithm evaluator " + index + " work " + workId;
              if (environment.isFullyAsserted())
                director.assertWorkingScoreFromScratch(candidateScore, context);
              if (environment.isIntrusivelyAsserted())
                director.assertShadowVariablesAreNotStale(candidateScore, context);
              workspace.scored(candidateScore);
              if (evaluationObserver != null) evaluationObserver.accept(workId, director);
              result =
                  new Result<>(workId, true, candidateScore, transition.changedAssignmentCount());
            }
          }
          evaluatedCount++;
          snapshot(director);
          job.ticket.result = result;
          wakeCoordinator();
        }
      } catch (Throwable error) {
        if (!(error instanceof InterruptedException && (stopping || aborting))) {
          recordFailure(
              new IllegalStateException(
                  "Genetic algorithm evaluator worker ("
                      + index
                      + ") failed at work ("
                      + workId
                      + ").",
                  error));
        }
      } finally {
        if (!ready) initialized.countDown();
        if (director != null) {
          try {
            snapshot(director);
          } catch (Throwable error) {
            recordFailure(
                new IllegalStateException(
                    "Reading genetic algorithm evaluator (" + index + ") final work failed.",
                    error));
          }
          try {
            director.close();
          } catch (Throwable error) {
            recordFailure(
                new IllegalStateException(
                    "Closing genetic algorithm evaluator (" + index + ") failed.", error));
          } finally {
            closed = true;
          }
        }
        wakeCoordinator();
      }
    }

    private void snapshot(InnerScoreDirector<Solution_, Score_> director) {
      calculationCount = director.getCalculationCount();
      if (director instanceof BavetConstraintStreamScoreDirector<?, ?> bavet) {
        var session = bavet.getSession();
        if (session != null && session != lastSession) {
          sessionCount++;
          lastSession = session;
        }
      }
    }
  }
}
