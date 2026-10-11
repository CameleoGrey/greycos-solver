package greycos.solver.greybay.impl.evaluation;

/** Measurement/protocol failures stop the study instead of becoming tuning loss observations. */
public final class EvaluationMeasurementException extends IllegalStateException {
  public EvaluationMeasurementException(String message) {
    super(message);
  }

  public EvaluationMeasurementException(String message, Throwable cause) {
    super(message, cause);
  }
}
