package greycos.solver.core.config.geneticalgorithm;

/** Mutation operators supported by the genetic algorithm. */
public enum GeneticAlgorithmMutationType {
  /** Assign independently sampled values to selected assignments. */
  CHANGE,
  /** Rotate values among selected assignments. */
  SWAP,
  /** Exchange the endpoints of selected adjacent edges. */
  SWAP_EDGES,
  /** Randomly reorder a contiguous segment. */
  SCRAMBLE,
  /** Remove one value and insert it at another position. */
  INSERTION,
  /** Reverse a contiguous segment. */
  INVERSE
}
