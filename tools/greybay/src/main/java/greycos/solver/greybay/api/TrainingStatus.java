package greycos.solver.greybay.api;

/** Describes the training incumbent only; improvement is not independent qualification. */
public enum TrainingStatus {
  NO_ELIGIBLE_INCUMBENT,
  NO_IMPROVEMENT_FOUND,
  NO_FULLY_FEASIBLE_CANDIDATE,
  IMPROVEMENT_FOUND
}
