package ai.greycos.solver.core.impl.phase.custom;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.solver.phase.PhaseCommandContext;
import ai.greycos.solver.core.impl.move.MoveDirector;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;
import ai.greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
final class DefaultPhaseCommandContext<Solution_> implements PhaseCommandContext<Solution_> {

  private final MoveDirector<Solution_, ?> moveDirector;
  private final BooleanSupplier isPhaseTerminated;

  DefaultPhaseCommandContext(
      MoveDirector<Solution_, ?> moveDirector, BooleanSupplier isPhaseTerminated) {
    this.moveDirector = Objects.requireNonNull(moveDirector);
    this.isPhaseTerminated = Objects.requireNonNull(isPhaseTerminated);
  }

  @Override
  public PlanningSolutionMetaModel<Solution_> getSolutionMetaModel() {
    return moveDirector.getScoreDirector().getSolutionDescriptor().getMetaModel();
  }

  @Override
  public Solution_ getWorkingSolution() {
    return moveDirector.getScoreDirector().getWorkingSolution();
  }

  @Override
  public boolean isPhaseTerminated() {
    return isPhaseTerminated.getAsBoolean();
  }

  @Override
  public <T> @Nullable T lookUpWorkingObject(@Nullable T problemFactOrPlanningEntity) {
    return moveDirector.lookUpWorkingObject(problemFactOrPlanningEntity);
  }

  @Override
  public void execute(Move<Solution_> move) {
    moveDirector.execute(move);
  }

  @Override
  public <Score_ extends Score<Score_>> Score_ executeAndCalculateScore(Move<Solution_> move) {
    moveDirector.execute(move);
    return ((ai.greycos.solver.core.impl.score.director.InnerScoreDirector<Solution_, Score_>)
            moveDirector.getScoreDirector())
        .calculateScore()
        .raw();
  }

  @Override
  public <Result_> @Nullable Result_ executeTemporarily(
      Move<Solution_> move, Function<Solution_, @Nullable Result_> temporarySolutionConsumer) {
    return moveDirector.executeTemporary(move, temporarySolutionConsumer, false);
  }

  @Override
  public <Score_ extends Score<Score_>> Score_ executeTemporarily(Move<Solution_> move) {
    Score_ score =
        executeTemporarily(
            move,
            solution -> moveDirector.getScoreDirector().getSolutionDescriptor().getScore(solution));
    return Objects.requireNonNull(
        score, () -> "The move (%s) failed to calculate a score.".formatted(move));
  }

  @Override
  public <Result_> @Nullable Result_ executeTemporarilyAndCalculateScore(
      Move<Solution_> move, Function<Solution_, @Nullable Result_> temporarySolutionConsumer) {
    return moveDirector.executeTemporary(move, temporarySolutionConsumer, true);
  }

  @Override
  public <Score_ extends Score<Score_>> Score_ executeTemporarilyAndCalculateScore(
      Move<Solution_> move) {
    Score_ score =
        executeTemporarilyAndCalculateScore(
            move,
            solution -> moveDirector.getScoreDirector().getSolutionDescriptor().getScore(solution));
    return Objects.requireNonNull(
        score, () -> "The move (%s) failed to calculate a score.".formatted(move));
  }
}
