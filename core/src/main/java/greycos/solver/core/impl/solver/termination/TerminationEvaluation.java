package greycos.solver.core.impl.solver.termination;

import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

/** Evaluates complete termination expressions without dropping unavailable conditions from AND. */
final class TerminationEvaluation {

  private TerminationEvaluation() {}

  static <Solution_> boolean isSolverTerminated(
      Termination<Solution_> termination, SolverScope<Solution_> scope) {
    if (termination instanceof AbstractCompositeTermination<Solution_> composite) {
      var and = composite instanceof AndCompositeTermination<Solution_>;
      if (composite.terminationList.isEmpty()) {
        return false;
      }
      for (var i = 0; i < composite.terminationList.size(); i++) {
        var child = composite.terminationList.get(i);
        if (isSolverTerminated(child, scope) != and) {
          return !and;
        }
      }
      return and;
    }
    // A phase-only condition is still part of the expression, but cannot be satisfied here.
    return termination instanceof SolverTermination<Solution_> solverTermination
        && solverTermination.isSolverTerminated(scope);
  }

  static <Solution_> boolean isPhaseTerminated(
      Termination<Solution_> termination,
      AbstractPhaseScope<Solution_> scope,
      boolean solverConfiguration) {
    if (termination instanceof AbstractCompositeTermination<Solution_> composite) {
      var and = composite instanceof AndCompositeTermination<Solution_>;
      if (composite.terminationList.isEmpty()) {
        return false;
      }
      for (var i = 0; i < composite.terminationList.size(); i++) {
        var child = composite.terminationList.get(i);
        if (isPhaseTerminated(child, scope, solverConfiguration) != and) {
          return !and;
        }
      }
      return and;
    }
    // The partition adapter owns the origin of each leaf and needs the supplied child phase
    // to evaluate inherited phase-only conditions. Its solver predicate is intentionally unscoped.
    if (termination instanceof PartitionTermination<Solution_> partitionTermination) {
      return partitionTermination.isPhaseTerminated(scope);
    }
    if (solverConfiguration
        && termination instanceof SolverTermination<Solution_> solverTermination) {
      return solverTermination.isSolverTerminated(scope.getSolverScope());
    }
    if (termination instanceof PhaseTermination<Solution_> phaseTermination) {
      if (!phaseTermination.isApplicableTo(scope.getClass())) {
        return false;
      }
      return phaseTermination.isPhaseTerminated(scope);
    }
    return false;
  }

  static <Solution_> double calculateSolverTimeGradient(
      Termination<Solution_> termination, SolverScope<Solution_> scope) {
    if (termination instanceof AbstractCompositeTermination<Solution_> composite) {
      var gradient = -1.0;
      var and = composite instanceof AndCompositeTermination<Solution_>;
      for (var i = 0; i < composite.terminationList.size(); i++) {
        var child = composite.terminationList.get(i);
        gradient =
            TerminationGradient.combine(and, gradient, calculateSolverTimeGradient(child, scope));
      }
      return gradient;
    }
    return termination instanceof SolverTermination<Solution_> solverTermination
        ? solverTermination.calculateSolverTimeGradient(scope)
        : -1.0;
  }

  static <Solution_> double calculatePhaseTimeGradient(
      Termination<Solution_> termination,
      AbstractPhaseScope<Solution_> scope,
      boolean solverConfiguration) {
    if (termination instanceof AbstractCompositeTermination<Solution_> composite) {
      var gradient = -1.0;
      var and = composite instanceof AndCompositeTermination<Solution_>;
      for (var i = 0; i < composite.terminationList.size(); i++) {
        var child = composite.terminationList.get(i);
        gradient =
            TerminationGradient.combine(
                and, gradient, calculatePhaseTimeGradient(child, scope, solverConfiguration));
      }
      return gradient;
    }
    if (solverConfiguration
        && termination instanceof SolverTermination<Solution_> solverTermination) {
      return solverTermination.calculateSolverTimeGradient(scope.getSolverScope());
    }
    if (termination instanceof PhaseTermination<Solution_> phaseTermination) {
      if (!phaseTermination.isApplicableTo(scope.getClass())) {
        return -1.0;
      }
      return phaseTermination.calculatePhaseTimeGradient(scope);
    }
    return -1.0;
  }
}
