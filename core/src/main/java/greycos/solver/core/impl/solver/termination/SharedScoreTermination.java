package greycos.solver.core.impl.solver.termination;

import java.util.Objects;
import java.util.function.Supplier;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

/**
 * A score target bound to one island population's immutable progress. Child copies keep the same
 * source, so a nested population cannot narrow an inherited target to its own local incumbent.
 */
final class SharedScoreTermination<Solution_> extends AbstractUniversalTermination<Solution_>
    implements ChildThreadSupportingTermination<Solution_, SolverScope<Solution_>> {

  private final Termination<Solution_> definition;
  private final Supplier<IslandTerminationBudget.Progress> progress;
  private final boolean solverOrigin;

  SharedScoreTermination(
      Termination<Solution_> definition,
      Supplier<IslandTerminationBudget.Progress> progress,
      boolean solverOrigin) {
    if (!(definition instanceof BestScoreTermination
        || definition instanceof BestScoreFeasibleTermination)) {
      throw new IllegalArgumentException("Not a score target termination: " + definition);
    }
    this.definition = definition;
    this.progress = Objects.requireNonNull(progress);
    this.solverOrigin = solverOrigin;
  }

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public boolean isSolverTerminated(SolverScope<Solution_> scope) {
    var score = progress.get().score();
    if (score == null || !score.isFullyAssigned()) {
      return false;
    }
    if (definition instanceof BestScoreTermination<Solution_> target) {
      return ((Score) score.raw()).compareTo(target.getBestScoreLimit()) >= 0;
    }
    return score.raw().isFeasible();
  }

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public double calculateSolverTimeGradient(SolverScope<Solution_> scope) {
    var snapshot = progress.get();
    var score = snapshot.score();
    var baseline =
        solverOrigin
            ? snapshot.solverFirstInitializedScore()
            : snapshot.phaseFirstInitializedScore();
    if (score == null || !score.isFullyAssigned() || baseline == null) {
      return 0.0;
    }
    if (definition instanceof BestScoreTermination<Solution_> target) {
      return target.calculateTimeGradient(
          (Score) baseline, target.getBestScoreLimit(), (Score) score.raw());
    }
    return ((BestScoreFeasibleTermination<Solution_>) definition)
        .calculateFeasibilityTimeGradient(
            InnerScore.fullyAssigned((Score) baseline), (Score) score.raw());
  }

  @Override
  public boolean isPhaseTerminated(AbstractPhaseScope<Solution_> scope) {
    return isSolverTerminated(scope.getSolverScope());
  }

  @Override
  public double calculatePhaseTimeGradient(AbstractPhaseScope<Solution_> scope) {
    return calculateSolverTimeGradient(scope.getSolverScope());
  }

  @Override
  public Termination<Solution_> createChildThreadTermination(
      SolverScope<Solution_> scope, ChildThreadType childThreadType) {
    // No lifecycle or mutable scope state is shared; every read captures an immutable snapshot.
    return this;
  }
}
