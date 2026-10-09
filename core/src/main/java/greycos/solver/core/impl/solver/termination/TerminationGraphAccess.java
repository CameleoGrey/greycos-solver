package greycos.solver.core.impl.solver.termination;

import java.util.Objects;

/** Determines whether termination can run while a phase defers its working graph updates. */
public final class TerminationGraphAccess {

  private TerminationGraphAccess() {}

  /**
   * Recognizes audited framework implementations without invoking their predicates or callbacks.
   * Unknown definitions and bridge subclasses require a materialized working solution. All
   * composite children are checked, including generic children omitted from the phase-only view.
   */
  public static boolean isWorkingSolutionIndependent(Termination<?> termination) {
    var type = Objects.requireNonNull(termination).getClass();
    if (type == AndCompositeTermination.class || type == OrCompositeTermination.class) {
      var composite = (AbstractCompositeTermination<?>) termination;
      for (var child : composite.terminationList) {
        if (!isWorkingSolutionIndependent(child)) return false;
      }
      return true;
    }
    if (type == SolverBridgePhaseTermination.class) {
      return isWorkingSolutionIndependent(
          ((SolverBridgePhaseTermination<?>) termination).solverTermination);
    }
    if (type == PhaseToSolverTerminationBridge.class) {
      return isWorkingSolutionIndependent(
          ((PhaseToSolverTerminationBridge<?>) termination).getSolverTermination());
    }
    // These definitions use only clocks, termination flags, work counters and stored scores.
    // Island/partition wrappers and supplier-backed progress require a separate ownership audit.
    return type == BasicPlumbingTermination.class
        || type == ChildThreadPlumbingTermination.class
        || type == BestScoreTermination.class
        || type == BestScoreFeasibleTermination.class
        || type == DiminishedReturnsTermination.class
        || type == MoveCountTermination.class
        || type == ScoreCalculationCountTermination.class
        || type == StepCountTermination.class
        || type == TimeMillisSpentTermination.class
        || type == UnimprovedStepCountTermination.class
        || type == UnimprovedTimeMillisSpentTermination.class
        || type == UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination.class;
  }
}
