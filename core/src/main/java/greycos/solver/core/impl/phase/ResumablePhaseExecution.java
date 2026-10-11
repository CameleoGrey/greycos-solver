package greycos.solver.core.impl.phase;

import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;

/**
 * An owner-thread-confined execution of one phase. A yield preserves the phase's live state and
 * does not emit lifecycle callbacks. Solver lifecycle and error cleanup remain the caller's
 * responsibility.
 *
 * @param <Solution_> the solution type
 */
public interface ResumablePhaseExecution<Solution_> {

  /**
   * Executes at most the positive grant of completed steps. A completed step includes all of its
   * end callbacks and the update of the phase's last completed step. A terminal decision without a
   * move does not consume a step. Natural completion ends the phase exactly once.
   *
   * @return the number of completed steps, possibly zero on completion
   */
  long advance(long completedStepGrant);

  boolean isComplete();

  /** Ends an active execution at its current safe boundary; idempotent after normal completion. */
  void finish();

  /** The resident scope; a phase skipped before its lifecycle starts has an unstarted scope. */
  AbstractPhaseScope<Solution_> getPhaseScope();
}
