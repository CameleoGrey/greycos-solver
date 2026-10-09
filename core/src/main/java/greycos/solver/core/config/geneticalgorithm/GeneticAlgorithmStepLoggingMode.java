package greycos.solver.core.config.geneticalgorithm;

import jakarta.xml.bind.annotation.XmlEnum;

/**
 * Selects which completed genetic algorithm steps are logged at DEBUG level. The logger must still
 * enable DEBUG; other diagnostic messages are unaffected.
 */
@XmlEnum
public enum GeneticAlgorithmStepLoggingMode {
  /** Logs every completed step. This is the default. */
  ALL,
  /**
   * Logs only steps that strictly improve the owning solver's best score. Ties and improvements
   * over the previous step that do not exceed the best score are omitted.
   */
  BEST_SCORE_IMPROVED
}
