package greycos.solver.core.impl.solver.termination;

import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchPhaseScope;

/** Startup validation of an enclosing search budget, excluding cancellation plumbing alone. */
public final class IteratedLocalSearchTerminationSupport {
  private IteratedLocalSearchTerminationSupport() {}

  public static boolean hasApplicableLimit(Termination<?> termination) {
    return hasApplicableLimit(termination, false);
  }

  static boolean hasApplicableLimit(Termination<?> termination, boolean solverConfiguration) {
    return hasApplicableLimit(
        termination, solverConfiguration ? Endpoint.SOLVER_BRIDGED_PHASE : Endpoint.PHASE);
  }

  static boolean hasApplicableSolverLimit(Termination<?> termination) {
    return hasApplicableLimit(termination, Endpoint.SOLVER);
  }

  private static boolean hasApplicableLimit(Termination<?> termination, Endpoint endpoint) {
    if (termination instanceof BasicPlumbingTermination<?>
        || termination instanceof ChildThreadPlumbingTermination<?>) {
      return false;
    }
    if (termination instanceof SolverBridgePhaseTermination<?> bridge) {
      return hasApplicableLimit(
          bridge.solverTermination,
          endpoint == Endpoint.SOLVER ? Endpoint.SOLVER : Endpoint.SOLVER_BRIDGED_PHASE);
    }
    if (termination.getClass() == PhaseToSolverTerminationBridge.class) {
      return hasApplicableSolverLimit(
          ((PhaseToSolverTerminationBridge<?>) termination).getSolverTermination());
    }
    if (termination instanceof AbstractCompositeTermination<?> composite) {
      if (composite.terminationList.isEmpty()) {
        return false;
      }
      return composite instanceof AndCompositeTermination<?>
          ? composite.terminationList.stream()
              .allMatch(child -> hasApplicableLimit(child, endpoint))
          : composite.terminationList.stream()
              .anyMatch(child -> hasApplicableLimit(child, endpoint));
    }
    // These evaluators own compiled expressions, not limits in their own right. Inspect their
    // bound leaves without reading another solver's mutable scopes or evaluating live predicates.
    if (termination instanceof PartitionTermination<?> partition) {
      return partition.hasApplicableIteratedLocalSearchLimit(endpoint != Endpoint.SOLVER);
    }
    if (termination instanceof IslandSequenceTermination<?> island) {
      return island.hasApplicableIteratedLocalSearchLimit();
    }
    if (endpoint != Endpoint.PHASE && termination instanceof SolverTermination<?>) {
      return true;
    }
    return endpoint != Endpoint.SOLVER
        && termination instanceof PhaseTermination<?> phaseTermination
        && phaseTermination.isApplicableTo(IteratedLocalSearchPhaseScope.class);
  }

  private enum Endpoint {
    PHASE,
    SOLVER_BRIDGED_PHASE,
    SOLVER
  }
}
