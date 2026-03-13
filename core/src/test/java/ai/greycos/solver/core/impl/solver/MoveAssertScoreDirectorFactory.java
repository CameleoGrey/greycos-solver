package ai.greycos.solver.core.impl.solver;

import java.util.function.Consumer;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.neighborhood.MoveRepository;
import ai.greycos.solver.core.impl.score.director.AbstractScoreDirector;
import ai.greycos.solver.core.impl.score.director.AbstractScoreDirectorFactory;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class MoveAssertScoreDirectorFactory<Solution_, Score_ extends Score<Score_>>
    extends AbstractScoreDirectorFactory<
        Solution_, Score_, MoveAssertScoreDirectorFactory<Solution_, Score_>> {

  private final Consumer<Solution_> moveSolutionConsumer;
  @Nullable private final MoveRepository<Solution_> moveRepository;

  public MoveAssertScoreDirectorFactory(
      SolutionDescriptor<Solution_> solutionDescriptor,
      Consumer<Solution_> moveSolutionConsumer,
      @Nullable MoveRepository<Solution_> moveRepository) {
    super(solutionDescriptor);
    this.moveSolutionConsumer = moveSolutionConsumer;
    this.moveRepository = moveRepository;
  }

  @Override
  public AbstractScoreDirector.AbstractScoreDirectorBuilder<Solution_, Score_, ?, ?>
      createScoreDirectorBuilder() {
    return new MoveAssertScoreDirector.Builder<>(this)
        .withMoveSolutionConsumer(moveSolutionConsumer)
        .withMoveRepository(moveRepository);
  }
}
