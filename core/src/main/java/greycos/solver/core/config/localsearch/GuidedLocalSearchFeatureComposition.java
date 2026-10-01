package greycos.solver.core.config.localsearch;

/** Selects the feature families whose penalties guide local search. */
public enum GuidedLocalSearchFeatureComposition {
  /** Derive features from genuine planning variables. */
  AUTOMATIC,
  /** Use the configured feature provider. */
  CUSTOM,
  /** Combine automatic features with features from the configured provider. */
  COMBINED
}
