package greycos.solver.core.config.alns;

import jakarta.xml.bind.annotation.XmlEnum;

/** Selects the independent ALNS work evaluated by move workers. */
@XmlEnum
public enum AlnsMoveThreadingMode {
  /** Preserves ordered probe evaluation within one repair; this is the default mode. */
  PROBES,
  /**
   * Evaluates multiple randomized repairs of the same destroyed state before selecting a candidate.
   */
  REPAIR_ATTEMPTS
}
