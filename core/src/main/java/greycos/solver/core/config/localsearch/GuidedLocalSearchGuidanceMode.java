package greycos.solver.core.config.localsearch;

import jakarta.xml.bind.annotation.XmlEnum;

/** Determines which business-score levels guided local search can explore. */
@XmlEnum
public enum GuidedLocalSearchGuidanceMode {
  /** Guides business levels using progress epochs and bounded hard-score excursions. */
  ALL_LEVELS,
  /** Guides one level while protecting the original score prefix above it. */
  FIXED_TARGET
}
