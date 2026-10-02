package greycos.solver.core.api.solver.multistage;

import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.Score;

/**
 * Creates ordered stages for candidates acting on one configured basic variable.
 *
 * <p>The solver creates an independent provider for each working solution, including workers,
 * islands and partitions. Providers need a public no-argument constructor. Initialization and
 * cleanup repeat when a phase is reset or its working solution is replaced.
 *
 * <p>Keep only read-only phase caches and working-object references obtained during initialization.
 * Candidate behavior must depend on the current solution, candidate index and supplied generator,
 * not on callback history. Working objects and mutable provider state must not be shared with
 * another provider or thread. Direct changes to the solution or problem facts are unsupported.
 *
 * @param <Solution_> the planning solution type
 * @param <Entity_> the entity type declaring the configured variable
 * @param <Value_> the planning value type
 * @param <Score_> the score type
 */
public interface BasicVariableStageProvider<
    Solution_, Entity_, Value_, Score_ extends Score<Score_>> {

  /** Initializes read-only caches against this provider's working solution at phase start. */
  default void initialize(Solution_ workingSolution) {}

  /**
   * Returns the nonnegative size of the finite candidate population for the current step. Zero
   * denotes an empty neighborhood. The count and candidate-index mapping must remain stable within
   * the step and agree across cloned working solutions. This method must not mutate state.
   */
  long getCandidateCount();

  /**
   * Creates a finite ordered list of stages for the given candidate index. The index is in {@code
   * [0, getCandidateCount())}. Each stage runs after the preceding stage's selected operation has
   * been applied and its shadow variables updated. An empty list makes no change.
   *
   * <p>Use only the supplied generator for random choices, including within the returned stages. Do
   * not retain it beyond this candidate or return stages that refer to objects from another working
   * solution. Callbacks are not invoked when the completed candidate is replayed.
   */
  List<BasicVariableCustomStage<Solution_, Entity_, Value_, Score_>> createStages(
      long candidateIndex, RandomGenerator random);

  /** Releases references and resources from initialization, including when the phase fails. */
  default void phaseEnded() {}
}
