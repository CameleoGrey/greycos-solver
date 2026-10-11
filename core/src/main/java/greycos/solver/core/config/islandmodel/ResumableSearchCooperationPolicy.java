package greycos.solver.core.config.islandmodel;

import jakarta.xml.bind.annotation.XmlEnum;

/** Determines cooperation between resident sessions at committed round boundaries. */
@XmlEnum
public enum ResumableSearchCooperationPolicy {
  /** Continue every session independently, preserving its live search state. */
  NONE,
  /**
   * Restart at most one stagnant session from the strictly better committed archive per round.
   * Session zero is protected; weakest episode-best score and then lowest session ID choose the
   * recipient. A restart creates fresh search components and a new episode seed.
   */
  RESTART_WEAKEST
}
