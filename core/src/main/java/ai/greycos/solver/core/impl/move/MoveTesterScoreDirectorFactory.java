package ai.greycos.solver.core.impl.move;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.score.director.AbstractScoreDirector;
import ai.greycos.solver.core.impl.score.director.AbstractScoreDirectorFactory;

import org.jspecify.annotations.NullMarked;

@NullMarked
final class MoveTesterScoreDirectorFactory<Solution_, Score_ extends Score<Score_>>
    extends AbstractScoreDirectorFactory<
        Solution_, Score_, MoveTesterScoreDirectorFactory<Solution_, Score_>> {

  public MoveTesterScoreDirectorFactory(
      SolutionDescriptor<Solution_> solutionDescriptor, EnvironmentMode environmentMode) {
    super(solutionDescriptor, environmentMode);
  }

  @Override
  public AbstractScoreDirector.AbstractScoreDirectorBuilder<Solution_, Score_, ?, ?>
      createScoreDirectorBuilder() {
    return new MoveTesterScoreDirector.Builder<>(this);
  }
}
