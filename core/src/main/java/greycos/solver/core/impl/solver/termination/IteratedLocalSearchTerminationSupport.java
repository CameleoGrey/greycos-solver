package greycos.solver.core.impl.solver.termination;

import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchPhaseScope;

/** Startup validation of an enclosing search budget, excluding cancellation plumbing alone. */
public final class IteratedLocalSearchTerminationSupport {
  private IteratedLocalSearchTerminationSupport() {}

  public static boolean hasApplicableLimit(Termination<?> termination) {
    if (termination instanceof BasicPlumbingTermination<?>) {
      return false;
    }
    if (termination instanceof SolverBridgePhaseTermination<?> bridge) {
      return hasApplicableLimit(bridge.solverTermination);
    }
    if (termination instanceof AbstractCompositeTermination<?> composite) {
      return composite.terminationList.stream()
          .anyMatch(IteratedLocalSearchTerminationSupport::hasApplicableLimit);
    }
    return !(termination instanceof PhaseTermination<?> phaseTermination)
        || phaseTermination.isApplicableTo(IteratedLocalSearchPhaseScope.class);
  }
}
