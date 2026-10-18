package greycos.solver.core.api.solver;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import greycos.solver.core.api.solver.change.ProblemChange;
import greycos.solver.core.api.solver.event.FinalBestSolutionEvent;
import greycos.solver.core.api.solver.event.SolverEventListener;

/**
 * Uses only API signatures that existed before run summaries, also for binary compatibility checks.
 */
public final class LegacySolverApiFixture {

  private LegacySolverApiFixture() {}

  public static FinalBestSolutionEvent<Object> legacyEvent(Object solution) {
    return () -> solution;
  }

  public static final class LegacySolver implements Solver<Object> {
    @Override
    public Object solve(Object problem) {
      return problem;
    }

    @Override
    public boolean terminateEarly() {
      return false;
    }

    @Override
    public boolean isSolving() {
      return false;
    }

    @Override
    public boolean isTerminateEarly() {
      return false;
    }

    @Override
    public void addProblemChange(ProblemChange<Object> change) {}

    @Override
    public void addProblemChanges(List<ProblemChange<Object>> changes) {}

    @Override
    public boolean isEveryProblemChangeProcessed() {
      return true;
    }

    @Override
    public void addEventListener(SolverEventListener<Object> listener) {}

    @Override
    public void removeEventListener(SolverEventListener<Object> listener) {}
  }

  public static final class LegacyJob implements SolverJob<Object> {
    private final Object solution;

    public LegacyJob(Object solution) {
      this.solution = solution;
    }

    @Override
    public Object getProblemId() {
      return 1L;
    }

    @Override
    public SolverStatus getSolverStatus() {
      return SolverStatus.NOT_SOLVING;
    }

    @Override
    public CompletableFuture<Void> addProblemChanges(List<ProblemChange<Object>> changes) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void terminateEarly() {}

    @Override
    public boolean isTerminatedEarly() {
      return false;
    }

    @Override
    public Object getFinalBestSolution() {
      return solution;
    }

    @Override
    public Duration getSolvingDuration() {
      return Duration.ZERO;
    }

    @Override
    public long getScoreCalculationCount() {
      return 0;
    }

    @Override
    public long getMoveEvaluationCount() {
      return 0;
    }

    @Override
    public ProblemSizeStatistics getProblemSizeStatistics() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getScoreCalculationSpeed() {
      return 0;
    }

    @Override
    public long getMoveEvaluationSpeed() {
      return 0;
    }
  }
}
