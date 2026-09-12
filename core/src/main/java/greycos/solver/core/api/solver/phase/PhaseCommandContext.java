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

  void execute(Move<Solution_> move);

  <Score_ extends Score<Score_>> Score_ executeAndCalculateScore(Move<Solution_> move);

  /**
   * Executes the move temporarily and passes the changed solution to the consumer. Afterwards,
   * restores the original solution state and stored score without recalculating the score.
   */
  <Result_> @Nullable Result_ executeTemporarily(
      Move<Solution_> move, Function<Solution_, @Nullable Result_> temporarySolutionConsumer);

  /** Returns the temporary score, then restores the original solution state and stored score. */
  <Score_ extends Score<Score_>> Score_ executeTemporarily(Move<Solution_> move);

  /** Like {@link #executeTemporarily(Move)}, also recalculating the score after restoration. */
  <Score_ extends Score<Score_>> Score_ executeTemporarilyAndCalculateScore(Move<Solution_> move);

  /**
   * Like {@link #executeTemporarily(Move, Function)}, also recalculating the score after
   * restoration.
   */
  <Result_> @Nullable Result_ executeTemporarilyAndCalculateScore(
      Move<Solution_> move, Function<Solution_, @Nullable Result_> temporarySolutionConsumer);
}
