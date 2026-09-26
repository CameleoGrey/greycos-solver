package greycos.solver.core.api.solver.phase;

import java.util.function.Function;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;
import greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The context of a command that is executed during a custom phase. It provides access to the
 * working solution and allows executing moves.
 *
 * @param <Solution_> the type of the solution
 * @see PhaseCommand
 */
@NullMarked
public interface PhaseCommandContext<Solution_> extends Lookup {

  PlanningSolutionMetaModel<Solution_> getSolutionMetaModel();

  Solution_ getWorkingSolution();

  boolean isPhaseTerminated();

  /**
   * Executes the given move and updates the working solution without recalculating the score for
   * performance reasons.
   *
   * @param move the move to execute
   * @throws IllegalArgumentException if the move causes the solution to have a negative {@link
   *     Score#structuralScore()}. If you are unsure if a move will result in a structurally valid
   *     solution, use {@link #executeTemporarily(Move)} to check if a move results in a structural
   *     flawed solution before executing it.
   */
  void execute(Move<Solution_> move);

  /**
   * Executes the given move and updates the working solution, and returns the new score of the
   * working solution.
   *
   * @param move the move to execute
   * @return the new score of the working solution after executing the move
   * @throws IllegalArgumentException if the move causes the solution to have a negative {@link
   *     Score#structuralScore()}. If you are unsure if a move will result in a structurally valid
   *     solution, use {@link #executeTemporarily(Move)} to check if a move results in a structural
   *     flawed solution before executing it.
   */
  <Score_ extends Score<Score_>> Score_ executeAndCalculateScore(Move<Solution_> move);

  /**
   * Executes the given move temporarily and returns the result of the given consumer. The working
   * solution is reverted to its original state after the consumer has been executed, and the score
   * is restored to its original value without requiring a recalculation of the score.
   *
   * @param move the move to execute temporarily
   * @param temporarySolutionConsumer the consumer to execute with the temporarily modified
   *     solution; this solution must not be modified any further.
   * @return the result of the consumer
   * @throws IllegalArgumentException if the move causes the solution to have a negative {@link
   *     Score#structuralScore()}. Use {@link #executeTemporarily(Move, Function, Function)}
   *     instead, where structurally flawed solutions are handled by a separate consumer.
   */
  <Result_> @Nullable Result_ executeTemporarily(
      Move<Solution_> move, Function<Solution_, @Nullable Result_> temporarySolutionConsumer);

  /**
   * As defined by {@link #executeTemporarily(Move, Function)}, except having a separate consumer to
   * handle solutions with a negative {@link Score#structuralScore()}.
   *
   * @param move the move to execute temporarily
   * @param temporarySolutionConsumer the consumer to execute with the temporarily modified
   *     structurally valid solution; this solution must not be modified any further.
   * @param structurallyFlawedSolutionConsumer the consumer that is called when a move results in a
   *     structurally flawed solution. This solution must not be modified any further.
   * @return the result of the consumer
   */
  <Result_> @Nullable Result_ executeTemporarily(
      Move<Solution_> move,
      Function<Solution_, @Nullable Result_> temporarySolutionConsumer,
      Function<Solution_, @Nullable Result_> structurallyFlawedSolutionConsumer);

  /**
   * Executes the given move temporarily and returns the score of the temporarily modified solution.
   * The working solution is reverted to its original state after the consumer has been executed,
   * and the score is restored to its original value without requiring a recalculation of the score.
   *
   * @param move the move to execute temporarily
   * @return the score of the temporarily modified solution after executing the move
   */
  <Score_ extends Score<Score_>> Score_ executeTemporarily(Move<Solution_> move);

  /** Like {@link #executeTemporarily(Move)}, also recalculating the score after restoration. */
  <Score_ extends Score<Score_>> Score_ executeTemporarilyAndCalculateScore(Move<Solution_> move);

  /**
   * As defined by {@link #executeTemporarily(Move, Function)}, with the guarantee of recalculating
   * a fresh score at the end of the method's invocation.
   *
   * @param move the move to execute temporarily
   * @param temporarySolutionConsumer the consumer to execute with the temporarily modified
   *     solution; this solution must not be modified any further.
   * @throws IllegalArgumentException if the move causes the solution to have a negative {@link
   *     Score#structuralScore()}.
   */
  <Result_> @Nullable Result_ executeTemporarilyAndCalculateScore(
      Move<Solution_> move, Function<Solution_, @Nullable Result_> temporarySolutionConsumer);

  /**
   * As defined by {@link #executeTemporarily(Move, Function, Function)}, with the guarantee of a
   * fresh score at the end of the method's invocation.
   *
   * @param move the move to execute temporarily
   * @param temporarySolutionConsumer the consumer to execute with the temporarily modified
   *     structurally valid solution; this solution must not be modified any further.
   * @param structurallyFlawedSolutionConsumer the consumer that is called when a move results in a
   *     structurally flawed solution. This solution must not be modified any further.
   */
  <Result_> @Nullable Result_ executeTemporarilyAndCalculateScore(
      Move<Solution_> move,
      Function<Solution_, @Nullable Result_> temporarySolutionConsumer,
      Function<Solution_, @Nullable Result_> structurallyFlawedSolutionConsumer);

  @Override
  <T> @Nullable T lookUpWorkingObject(@Nullable T problemFactOrPlanningEntity);
}
