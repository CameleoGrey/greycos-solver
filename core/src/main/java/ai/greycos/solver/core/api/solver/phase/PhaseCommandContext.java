package ai.greycos.solver.core.api.solver.phase;

import java.util.function.Function;

import ai.greycos.solver.core.api.cotwin.lookup.Lookup;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;
import ai.greycos.solver.core.preview.api.move.Move;

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

  <Result_> @Nullable Result_ executeTemporarily(
      Move<Solution_> move, Function<Solution_, @Nullable Result_> temporarySolutionConsumer);

  <Score_ extends Score<Score_>> Score_ executeTemporarily(Move<Solution_> move);

  <Score_ extends Score<Score_>> Score_ executeTemporarilyAndCalculateScore(Move<Solution_> move);

  <Result_> @Nullable Result_ executeTemporarilyAndCalculateScore(
      Move<Solution_> move, Function<Solution_, @Nullable Result_> temporarySolutionConsumer);
}
