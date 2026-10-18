package greycos.solver.core.impl.partitionedsearch;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.impl.solver.monitoring.SolverMetricSamples;
import greycos.solver.core.impl.solver.monitoring.SolverWorkSnapshot;

/** Owns a child solver even if submission fails or cancellation prevents the task from starting. */
final class PartitionTask<Solution_> implements Callable<SolverWorkSnapshot> {

  private final PartitionSolver<Solution_> solver;
  private final Solution_ initialSolution;
  private final AtomicReference<ExecutionState> state = new AtomicReference<>(ExecutionState.NEW);

  PartitionTask(PartitionSolver<Solution_> solver, Solution_ initialSolution) {
    this.solver = solver;
    this.initialSolution = initialSolution;
  }

  int getPartIndex() {
    return solver.getPartIndex();
  }

  @Override
  public SolverWorkSnapshot call() {
    if (!state.compareAndSet(ExecutionState.NEW, ExecutionState.RUNNING)) {
      return SolverWorkSnapshot.ZERO;
    }
    try {
      solver.solve(initialSolution);
      return SolverMetricSamples.localWork(solver.getSolverScope());
    } finally {
      state.set(ExecutionState.CLOSED);
    }
  }

  void cancelBeforeStart() {
    if (state.compareAndSet(ExecutionState.NEW, ExecutionState.CLOSED)) {
      solver.getSolverScope().getScoreDirector().close();
    }
  }

  private enum ExecutionState {
    NEW,
    RUNNING,
    CLOSED
  }
}
