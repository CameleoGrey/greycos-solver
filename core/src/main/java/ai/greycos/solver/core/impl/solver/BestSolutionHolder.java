package ai.greycos.solver.core.impl.solver;

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
import java.util.function.UnaryOperator;

import ai.greycos.solver.core.api.solver.Solver;
import ai.greycos.solver.core.api.solver.change.ProblemChange;
import ai.greycos.solver.core.api.solver.event.EventProducerId;

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

  @Nullable BestSolutionContainingProblemChanges<Solution_> take() {
    var latestVersionedBestSolution = resetVersionedBestSolution();
    if (latestVersionedBestSolution == null) {
      return null;
    }

    var bestSolutionVersion = latestVersionedBestSolution.version();
    var latestProcessedVersion = this.lastProcessedVersion.getAndUpdate(bestSolutionVersion::max);
    if (latestProcessedVersion.compareTo(bestSolutionVersion) > 0) {
      return null;
    }
    var boundaryVersion = bestSolutionVersion.add(BigInteger.ONE);
    var oldProblemChangesPerVersion =
        replaceMapSynchronized(map -> createNewProblemChangesMap(map.tailMap(boundaryVersion)));
    var containedProblemChanges =
        oldProblemChangesPerVersion.headMap(boundaryVersion).values().stream()
            .flatMap(Collection::stream)
            .toList();
    return new BestSolutionContainingProblemChanges<>(
        latestVersionedBestSolution.bestSolution(),
        latestVersionedBestSolution.producerId(),
        containedProblemChanges);
  }

  private synchronized @Nullable VersionedBestSolution<Solution_> resetVersionedBestSolution() {
    var oldVersionedBestSolution = this.versionedBestSolution;
    this.versionedBestSolution = null;
    return oldVersionedBestSolution;
  }

  private synchronized SortedMap<BigInteger, List<CompletableFuture<Void>>> replaceMapSynchronized(
      UnaryOperator<SortedMap<BigInteger, List<CompletableFuture<Void>>>> replaceFunction) {
    var oldMap = problemChangesPerVersionMap;
    problemChangesPerVersionMap = replaceFunction.apply(oldMap);
    return oldMap;
  }

  void set(
      Solution_ bestSolution,
      EventProducerId producerId,
      BooleanSupplier isEveryProblemChangeProcessed) {
    if (isEveryProblemChangeProcessed.getAsBoolean()) {
      synchronized (this) {
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
      var futureProblemChangeList =
          problemChangesPerVersionMap.computeIfAbsent(currentVersion, version -> new ArrayList<>());
      futureProblemChangeList.add(futureProblemChange);
      solver.addProblemChanges(problemChangeList);
    }
    return futureProblemChange;
  }

  void cancelPendingChanges() {
    replaceMapSynchronized(map -> createNewProblemChangesMap()).values().stream()
        .flatMap(Collection::stream)
        .forEach(pendingProblemChange -> pendingProblemChange.cancel(false));
  }

  void cancelPendingChangesQuietly() {
    replaceMapSynchronized(map -> createNewProblemChangesMap()).values().stream()
        .flatMap(Collection::stream)
        .forEach(
            pendingProblemChange -> {
              try {
                pendingProblemChange.cancel(false);
              } catch (java.util.concurrent.CancellationException e) {
                // Ignore - future was already completed
              }
            });
  }

  private record VersionedBestSolution<Solution_>(
      Solution_ bestSolution, EventProducerId producerId, BigInteger version) {}
}
