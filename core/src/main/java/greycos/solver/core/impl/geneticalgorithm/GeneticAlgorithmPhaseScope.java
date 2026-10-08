package greycos.solver.core.impl.geneticalgorithm;

import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

/** All mutable population state belongs to one invocation of the phase. */
public final class GeneticAlgorithmPhaseScope<Solution_> extends AbstractPhaseScope<Solution_> {
  private GeneticAlgorithmStepScope<Solution_> lastCompletedStepScope;
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
