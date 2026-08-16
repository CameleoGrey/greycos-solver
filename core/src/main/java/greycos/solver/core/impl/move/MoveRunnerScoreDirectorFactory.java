package greycos.solver.core.impl.move;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.AbstractScoreDirector;
import greycos.solver.core.impl.score.director.AbstractScoreDirectorFactory;

import org.jspecify.annotations.NullMarked;

@NullMarked
final class MoveRunnerScoreDirectorFactory<Solution_, Score_ extends Score<Score_>>
    extends AbstractScoreDirectorFactory<
        Solution_, Score_, MoveRunnerScoreDirectorFactory<Solution_, Score_>> {

  public MoveRunnerScoreDirectorFactory(
      SolutionDescriptor<Solution_> solutionDescriptor, EnvironmentMode environmentMode) {
    super(solutionDescriptor, environmentMode);
  }

  public MoveRunnerScoreDirectorFactory(SolutionDescriptor<Solution_> solutionDescriptor) {
    this(solutionDescriptor, null);
  }

  @Override
  public AbstractScoreDirector.AbstractScoreDirectorBuilder<Solution_, Score_, ?, ?>
      createScoreDirectorBuilder() {
    return new MoveRunnerScoreDirector.Builder<>(this);
  }
}
