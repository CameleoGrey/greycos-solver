package greycos.solver.core.impl.solver.monitoring;

/** Purpose of an actual score calculation attempt in a resumable search session. */
public enum ResumableSearchWorkCategory {
  INITIALIZATION,
  /** Includes scored candidates and nested search probes; it is not a candidate count. */
  SEARCH,
  ASSERTION,
  VERIFICATION,
  RESTORATION,
  FINALIZATION
}
