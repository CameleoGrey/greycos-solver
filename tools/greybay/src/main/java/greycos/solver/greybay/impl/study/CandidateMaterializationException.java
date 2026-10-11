package greycos.solver.greybay.impl.study;

/** A candidate callback failed without violating the fixed study protocol. */
final class CandidateMaterializationException extends RuntimeException {
  CandidateMaterializationException(RuntimeException cause) {
    super(cause.getMessage(), cause);
  }
}
