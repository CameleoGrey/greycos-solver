package greycos.solver.core.config.localsearch;

import jakarta.xml.bind.annotation.XmlEnum;

/** Determines which business-score levels guided local search can explore. */
@XmlEnum
public enum GuidedLocalSearchGuidanceMode {
  /** Cycles guidance through all business levels and uses automatic decision features. */
  ALL_LEVELS,
  /** Guides one level while protecting the original score prefix above it. */
  FIXED_TARGET
}
