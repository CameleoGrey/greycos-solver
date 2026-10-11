package greycos.solver.greybay.impl.study;

/** An invalid experiment must never be converted into a poor candidate observation. */
final class StudyProtocolException extends IllegalStateException {
  StudyProtocolException(String message) {
    super(message);
  }
}
