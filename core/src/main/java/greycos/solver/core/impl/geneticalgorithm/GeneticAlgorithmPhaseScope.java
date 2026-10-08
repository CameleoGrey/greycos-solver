package greycos.solver.core.impl.geneticalgorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

/** All mutable population state belongs to one invocation of the phase. */
public final class GeneticAlgorithmPhaseScope<Solution_> extends AbstractPhaseScope<Solution_> {
  private GeneticAlgorithmStepScope<Solution_> lastCompletedStepScope;
  private final List<Consumer<GeneticAlgorithmStepScope<Solution_>>> committedStepListeners =
      new ArrayList<>();
  private long committedAttemptCount;
  private long generation;
  private int populationSize;
  private int distinctPopulationSize;
  private long noProgressAttemptCount;
  private long localImprovementProbeCount;
  private long localImprovementAcceptedCount;
  private String terminationReason = "configured termination";

  public GeneticAlgorithmPhaseScope(SolverScope<Solution_> solverScope, int phaseIndex) {
    super(solverScope, phaseIndex);
    lastCompletedStepScope = new GeneticAlgorithmStepScope<>(this, -1);
  }

  @Override
  public GeneticAlgorithmStepScope<Solution_> getLastCompletedStepScope() {
    return lastCompletedStepScope;
  }

  public void setLastCompletedStepScope(GeneticAlgorithmStepScope<Solution_> step) {
    lastCompletedStepScope = step;
  }

  /** Internal accounting listeners run only after ordinary step completion callbacks succeed. */
  public void addCommittedStepListener(Consumer<GeneticAlgorithmStepScope<Solution_>> listener) {
    committedStepListeners.add(Objects.requireNonNull(listener));
  }

  /**
   * Commits a completed attempt before publishing its monotonic work. A listener failure is a
   * postcommit failure: the attempt remains completed and every accounting listener is notified.
   */
  public void commitStep(GeneticAlgorithmStepScope<Solution_> step) {
    if (step == lastCompletedStepScope) {
      return;
    }
    if (step.getPhaseScope() != this || step.getStepIndex() != getNextStepIndex()) {
      throw new IllegalArgumentException(
          "The committed genetic algorithm step must be the next step of this phase.");
    }
    recordOutcome(step.getOutcome());
    lastCompletedStepScope = step;
    committedAttemptCount++;
    Throwable failure = null;
    for (var listener : committedStepListeners) {
      try {
        listener.accept(step);
      } catch (RuntimeException | Error listenerFailure) {
        if (failure == null) failure = listenerFailure;
        else if (failure != listenerFailure) failure.addSuppressed(listenerFailure);
      }
    }
    if (failure instanceof RuntimeException exception) throw exception;
    if (failure instanceof Error error) throw error;
  }

  /** Excludes the provisional outer credit while retaining completed probes in failed attempts. */
  public long getCommittedMoveEvaluationCount() {
    return Objects.requireNonNull(startingMoveEvaluationCount, "The phase has not started.")
        + committedAttemptCount
        + localImprovementProbeCount;
  }

  public long getGeneration() {
    return generation;
  }

  public void setGeneration(long generation) {
    this.generation = generation;
  }

  public int getPopulationSize() {
    return populationSize;
  }

  public int getDistinctPopulationSize() {
    return distinctPopulationSize;
  }

  public void setPopulationSize(int size, int distinctSize) {
    populationSize = size;
    distinctPopulationSize = distinctSize;
  }

  public long getNoProgressAttemptCount() {
    return noProgressAttemptCount;
  }

  public void resetNoProgressAttemptCount() {
    noProgressAttemptCount = 0;
  }

  /** Completed probes, including probes in interrupted offspring. */
  public long getLocalImprovementProbeCount() {
    return localImprovementProbeCount;
  }

  public long getLocalImprovementAcceptedCount() {
    return localImprovementAcceptedCount;
  }

  public void recordLocalImprovementProbe() {
    localImprovementProbeCount++;
  }

  public void recordLocalImprovementAccepted() {
    localImprovementAcceptedCount++;
  }

  public void recordOutcome(GeneticAlgorithmOutcome outcome) {
    noProgressAttemptCount =
        outcome == GeneticAlgorithmOutcome.EVALUATED ? 0 : noProgressAttemptCount + 1;
  }

  public String getTerminationReason() {
    return terminationReason;
  }

  public void setTerminationReason(String reason) {
    terminationReason = reason;
  }
}
