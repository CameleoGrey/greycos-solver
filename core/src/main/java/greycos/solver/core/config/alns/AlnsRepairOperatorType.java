package greycos.solver.core.config.alns;

import jakarta.xml.bind.annotation.XmlEnum;

@XmlEnum
public enum AlnsRepairOperatorType {
  GREEDY,
  REGRET_2,
  REGRET_3,
  RANDOMIZED_GREEDY,
  /** Chooses the best insertion across all eligible pending targets after each assignment. */
  CHEAPEST_INSERTION,
  /** Regret repair with a configurable number of alternatives, defaulting to four. */
  REGRET_K
}
