package greycos.solver.core.impl.solver.monitoring;

import java.util.LinkedHashMap;
import java.util.Map;

/** Retains the last accepted cumulative work snapshot of each island occurrence. */
public final class IslandWorkAccounting {
  private final Map<String, SolverWorkSnapshot> snapshots = new LinkedHashMap<>();
  private SolverWorkSnapshot retired = SolverWorkSnapshot.ZERO;
  private boolean sealed;

  public synchronized void register(String source) {
    if (sealed) {
      return;
    }
    if (snapshots.putIfAbsent(source, SolverWorkSnapshot.ZERO) != null) {
      throw new IllegalStateException("Island work source (" + source + ") is already registered.");
    }
  }

  public synchronized boolean publish(String source, SolverWorkSnapshot snapshot) {
    if (sealed) {
      return false;
    }
    var previous = snapshots.get(source);
    if (previous == null) {
      throw new IllegalStateException("Island work source (" + source + ") is not registered.");
    }
    if (snapshot.scoreCalculationCount() < previous.scoreCalculationCount()
        || snapshot.moveEvaluationCount() < previous.moveEvaluationCount()) {
      throw new IllegalStateException(
          "Cumulative work decreased for island source (" + source + ").");
    }
    snapshots.put(source, snapshot);
    return true;
  }

  public synchronized SolverWorkSnapshot snapshot() {
    var total = retired;
    for (var snapshot : snapshots.values()) {
      total = total.plus(snapshot);
    }
    return total;
  }

  /** Retains completed work without retaining one source entry per restart episode. */
  public synchronized void retire(String source) {
    var snapshot = snapshots.remove(source);
    if (snapshot != null) retired = retired.plus(snapshot);
  }

  public synchronized void seal() {
    sealed = true;
  }
}
