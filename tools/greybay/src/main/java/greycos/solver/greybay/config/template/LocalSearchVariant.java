package greycos.solver.greybay.config.template;

/** Separate variants prevent composite acceptance and mutually exclusive control fields. */
public enum LocalSearchVariant {
  LATE_ACCEPTANCE,
  TABU_SIZE,
  TABU_RATIO,
  SIMULATED_ANNEALING,
  GREAT_DELUGE_RATIO,
  GREAT_DELUGE_SCORE
}
