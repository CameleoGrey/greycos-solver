package greycos.solver.core.impl.solver;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.change.ProblemChange;
import greycos.solver.core.api.solver.event.EventProducerId;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The goal of this class is to register problem changes and best solutions in a thread-safe way.
 * Problem changes are {@link #addProblemChange(Solver, List) put in a queue} and later associated
 * with the best solution which contains them. The best solution is associated with a version number
 * that is incremented each time a {@link #set new best solution is set}. The best solution is
 * {@link #take() taken} together with all problem changes that were registered before the best
 * solution was set.
 *
 * <p>This class needs to be thread-safe.
 *
 * @param <Solution_>
 */
@NullMarked
final class BestSolutionHolder<Solution_> {

  private final AtomicReference<BigInteger> lastProcessedVersion =
      new AtomicReference<>(BigInteger.valueOf(-1));

  private volatile SortedMap<BigInteger, List<CompletableFuture<Void>>>
      problemChangesPerVersionMap = createNewProblemChangesMap();
  private volatile @Nullable VersionedBestSolution<Solution_> versionedBestSolution = null;
  private volatile BigInteger currentVersion = BigInteger.ZERO;
  // Protected by this monitor, together with registration and solver queue admission.
  private boolean problemChangeAdmissionClosed;
  private final ProblemChangeCancellation cancellation = new ProblemChangeCancellation();

  private static SortedMap<BigInteger, List<CompletableFuture<Void>>> createNewProblemChangesMap() {
    return createNewProblemChangesMap(Collections.emptySortedMap());
  }

  private static SortedMap<BigInteger, List<CompletableFuture<Void>>> createNewProblemChangesMap(
      SortedMap<BigInteger, List<CompletableFuture<Void>>> map) {
    return new TreeMap<>(map);
  }

  synchronized boolean isEmpty() {
    return this.versionedBestSolution == null;
  }

  synchronized @Nullable BestSolutionContainingProblemChanges<Solution_> take() {
    var latestVersionedBestSolution = versionedBestSolution;
    versionedBestSolution = null;
    if (latestVersionedBestSolution == null) {
      return null;
    }

    var bestSolutionVersion = latestVersionedBestSolution.version();
    var latestProcessedVersion = this.lastProcessedVersion.getAndUpdate(bestSolutionVersion::max);
    if (latestProcessedVersion.compareTo(bestSolutionVersion) > 0) {
      return null;
    }
    var boundaryVersion = bestSolutionVersion.add(BigInteger.ONE);
    var oldProblemChangesPerVersion = problemChangesPerVersionMap;
    problemChangesPerVersionMap =
        createNewProblemChangesMap(oldProblemChangesPerVersion.tailMap(boundaryVersion));
    var containedProblemChanges =
        oldProblemChangesPerVersion.headMap(boundaryVersion).values().stream()
            .flatMap(Collection::stream)
            .toList();
    return new BestSolutionContainingProblemChanges<>(
        latestVersionedBestSolution.bestSolution(),
        latestVersionedBestSolution.producerId(),
        containedProblemChanges);
  }

  void set(
      Solution_ bestSolution,
      EventProducerId producerId,
      BooleanSupplier isEveryProblemChangeProcessed) {
    // Registration also acquires this monitor before the solver's problem-change queue lock.
    // Keep the processed check and version assignment together; otherwise a newly queued change
    // could be acknowledged by a solution produced before that change was applied.
    synchronized (this) {
      if (isEveryProblemChangeProcessed.getAsBoolean()) {
        versionedBestSolution =
            new VersionedBestSolution<>(bestSolution, producerId, currentVersion);
        currentVersion = currentVersion.add(BigInteger.ONE);
      }
    }
  }

  CompletableFuture<Void> addProblemChange(
      Solver<Solution_> solver, List<ProblemChange<Solution_>> problemChangeList) {
    var futureProblemChange = new CompletableFuture<Void>();
    synchronized (this) {
      if (problemChangeAdmissionClosed) {
        throw new IllegalStateException(
            "Cannot add problem changes after the solver job has stopped accepting them.");
      }
      var futureProblemChangeList =
          problemChangesPerVersionMap.computeIfAbsent(currentVersion, version -> new ArrayList<>());
      futureProblemChangeList.add(futureProblemChange);
      solver.addProblemChanges(problemChangeList);
    }
    return futureProblemChange;
  }

  synchronized void closeProblemChangeAdmission() {
    problemChangeAdmissionClosed = true;
  }

  synchronized List<CompletableFuture<Void>> closeAndDrainPendingChanges() {
    problemChangeAdmissionClosed = true;
    var pendingChanges =
        problemChangesPerVersionMap.values().stream().flatMap(Collection::stream).toList();
    problemChangesPerVersionMap = createNewProblemChangesMap();
    return pendingChanges;
  }

  void cancelPendingChanges() {
    var changes = closeAndDrainPendingChanges();
    cancelPendingChanges(changes);
    // Retain immediate state publication for internal normal-completion callers, independently
    // of the application continuations running on cancellation tasks.
    ProblemChangeCancellation.awaitPublication(changes);
  }

  void cancelPendingChanges(List<CompletableFuture<Void>> pendingChanges) {
    cancellation.dispatch(pendingChanges);
  }

  void awaitCancellationPublication(long deadlineNanos) throws InterruptedException {
    cancellation.awaitPublication(deadlineNanos);
  }

  void awaitCancellationCompletion() {
    cancellation.awaitCompletion();
  }

  void cancelPendingChangesQuietly() {
    cancelPendingChanges();
  }

  private record VersionedBestSolution<Solution_>(
      Solution_ bestSolution, EventProducerId producerId, BigInteger version) {}
}
