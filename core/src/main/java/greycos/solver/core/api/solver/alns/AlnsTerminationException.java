package greycos.solver.core.api.solver.alns;

/** Cooperative cancellation at a balanced ALNS mutation boundary. */
public final class AlnsTerminationException extends RuntimeException {
  public AlnsTerminationException() {
    super("ALNS trial terminated.", null, false, false);
  }
}
