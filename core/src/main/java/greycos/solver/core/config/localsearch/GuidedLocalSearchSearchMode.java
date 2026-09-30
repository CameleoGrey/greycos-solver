package greycos.solver.core.config.localsearch;

import jakarta.xml.bind.annotation.XmlEnum;

/** Determines when guided local search finishes a neighborhood round. */
@XmlEnum
public enum GuidedLocalSearchSearchMode {
  /**
   * Searches a bounded sample. Exhausting the sample indicates sampled stagnation, not a proven
   * local minimum.
   */
  SAMPLED,
  /**
   * Searches a finite, untruncated neighborhood. Only complete exhaustion can establish a local
   * minimum for that neighborhood.
   */
  EXHAUSTIVE
}
