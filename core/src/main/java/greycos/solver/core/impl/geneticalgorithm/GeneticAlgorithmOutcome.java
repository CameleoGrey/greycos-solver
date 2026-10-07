package greycos.solver.core.impl.geneticalgorithm;

/** Outcomes of completed attempts; population admission is an independent decision. */
public enum GeneticAlgorithmOutcome {
  EVALUATED,
  DUPLICATE,
  INVALID,
  NO_CHANGE
}
