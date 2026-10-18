package greycos.solver.core.api.solver;

/** Determines which object-centric views to include in a full score analysis. */
public enum IndictmentFetchPolicy {
  /** Preserve the usual analysis without collecting indicted objects or original contributors. */
  NONE,
  /** Include terminal tuple objects, or the objects selected by {@code indictWith(...)}. */
  TERMINAL,
  /** Include terminal indictments and a separate view of original contributing objects. */
  TERMINAL_AND_ORIGINAL
}
