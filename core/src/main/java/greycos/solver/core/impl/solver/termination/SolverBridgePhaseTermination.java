package greycos.solver.core.impl.solver.termination;

import java.util.Objects;

import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.jspecify.annotations.NullMarked;

/**
 * Evaluates a solver's complete termination expression within a phase. Solver-capable leaves use
 * their solver predicates and gradients; phase-only leaves use the active phase. An unavailable
 * condition cannot satisfy an AND expression.
 *
 * <p>Had this not happened, the solver-level termination running at phase-level would call {@link
 * #isPhaseTerminated(AbstractPhaseScope)} instead of {@link
 * SolverTermination#isSolverTerminated(SolverScope)}, and would therefore use the phase start
 * timestamp as its reference, and not the solver start timestamp. The effect of this in practice
 * would have been that, if a solver-level {@link TimeMillisSpentTermination} were configured to
 * terminate after 10 seconds, each phase would effectively start a new 10-second counter.
 *
 * @param <Solution_>
 */
@NullMarked
final class SolverBridgePhaseTermination<Solution_> extends AbstractPhaseTermination<Solution_>
    implements ChildThreadSupportingTermination<Solution_, SolverScope<Solution_>> {

  final SolverTermination<Solution_> solverTermination;

  public SolverBridgePhaseTermination(SolverTermination<Solution_> solverTermination) {
    this.solverTermination = Objects.requireNonNull(solverTermination);
  }

  @Override
  public boolean isPhaseTerminated(AbstractPhaseScope<Solution_> phaseScope) {
    return TerminationEvaluation.isPhaseTerminated(solverTermination, phaseScope, true);
  }

  @Override
  public double calculatePhaseTimeGradient(AbstractPhaseScope<Solution_> phaseScope) {
    return TerminationEvaluation.calculatePhaseTimeGradient(solverTermination, phaseScope, true);
  }

  @Override
  public void bestScoreImproved(AbstractStepScope<Solution_> stepScope) {
    if (solverTermination instanceof PhaseTermination<Solution_> phaseTermination) {
      phaseTermination.bestScoreImproved(stepScope);
    }
  }

  @Override
  public boolean isSolverTerminated(SolverScope<Solution_> solverScope) {
    return solverTermination.isSolverTerminated(solverScope);
  }

  @Override
  public double calculateSolverTimeGradient(SolverScope<Solution_> solverScope) {
    return solverTermination.calculateSolverTimeGradient(solverScope);
  }

  @Override
  public Termination<Solution_> createChildThreadTermination(
      SolverScope<Solution_> scope, ChildThreadType childThreadType) {
    if (childThreadType == ChildThreadType.PART_THREAD) {
      // This strips the bridge, but the partitioned phase factory will add it back.
      return ChildThreadSupportingTermination.assertChildThreadSupport(solverTermination)
          .createChildThreadTermination(scope, childThreadType);
    } else {
      throw new UnsupportedOperationException(
          "The childThreadType (%s) is not implemented.".formatted(childThreadType));
    }
  }

  @Override
  public String toString() {
    return "Bridge(" + solverTermination + ")";
  }
}
