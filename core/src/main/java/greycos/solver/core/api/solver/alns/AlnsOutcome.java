package greycos.solver.core.api.solver.alns;

/** Mutually exclusive outcome of one outer ALNS trial; repair probes are not trials. */
public enum AlnsOutcome {
  NEW_BEST,
  IMPROVED,
  ACCEPTED,
  REJECTED,
  NO_CHANGE,
  REPAIR_FAILED,
  CANCELLED
}
