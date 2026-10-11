package greycos.solver.greybay.api;

/** COMPLETE denotes eligible measurement; native feasibility is a separate property. */
public enum EvaluationStatus {
  COMPLETE,
  FAILED,
  INVALID_RESULT,
  INCOMPLETE_RESULT,
  BUDGET_EXCEEDED,
  INTERRUPTED
}
