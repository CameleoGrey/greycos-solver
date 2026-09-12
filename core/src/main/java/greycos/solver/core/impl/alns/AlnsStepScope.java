package greycos.solver.core.impl.alns;

import greycos.solver.core.api.solver.alns.AlnsOutcome;
import greycos.solver.core.api.solver.alns.AlnsTrialResult;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;

public final class AlnsStepScope<Solution_> extends AbstractStepScope<Solution_> {
  private final AlnsPhaseScope<Solution_> phaseScope;
  private AlnsTrialResult<?> trialResult;

  public AlnsStepScope(AlnsPhaseScope<Solution_> phaseScope) {
    this(phaseScope, phaseScope.getNextStepIndex());
  }

  public AlnsStepScope(AlnsPhaseScope<Solution_> phaseScope, int index) {
    super(index);
    this.phaseScope = phaseScope;
  }

  @Override
  public AlnsPhaseScope<Solution_> getPhaseScope() {
    return phaseScope;
  }

  public AlnsTrialResult<?> getTrialResult() {
    return trialResult;
  }

  public void setTrialResult(AlnsTrialResult<?> result) {
    trialResult = result;
  }

  public String getOperatorPairId() {
    return pairId(trialResult.destroyId(), trialResult.repairId());
  }

  static String pairId(String destroyId, String repairId) {
    return escapeId(destroyId) + "/" + escapeId(repairId);
  }

  private static String escapeId(String id) {
    return id.replace("%", "%25").replace("/", "%2F");
  }

  public AlnsOutcome getOutcome() {
    return trialResult.outcome();
  }

  public boolean isAccepted() {
    return getOutcome() == AlnsOutcome.NEW_BEST
        || getOutcome() == AlnsOutcome.IMPROVED
        || getOutcome() == AlnsOutcome.ACCEPTED;
  }

  public long getProbeCount() {
    return trialResult.probeCount();
  }

  public int getDestroyedCount() {
    return trialResult.destroyedCount();
  }

  public int getRecoveryCount() {
    return trialResult.recoveryCount();
  }

  public long getElapsedNanos() {
    return trialResult.elapsedNanos();
  }
}
