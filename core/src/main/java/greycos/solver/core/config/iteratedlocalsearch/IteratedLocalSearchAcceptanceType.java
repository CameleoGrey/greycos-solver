package greycos.solver.core.config.iteratedlocalsearch;

/** The policy for accepting the best state returned by a bounded improvement episode. */
public enum IteratedLocalSearchAcceptanceType {
  /** Accept only a strict improvement over the accepted incumbent, using native score ordering. */
  IMPROVING_ONLY
}
