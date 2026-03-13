package ai.greycos.solver.core.impl.score.stream.bavet;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import ai.greycos.solver.core.impl.score.stream.common.ConstraintStreamImplSupport;

public record BavetConstraintStreamImplSupport(ConstraintMatchPolicy constraintMatchPolicy)
    implements ConstraintStreamImplSupport {

  @Override
  public <Score_ extends Score<Score_>, Solution_>
      InnerScoreDirector<Solution_, Score_> buildScoreDirector(
          SolutionDescriptor<Solution_> solutionDescriptorSupplier,
          ConstraintProvider constraintProvider) {
    var scoreDirectorFactory =
        new BavetConstraintStreamScoreDirectorFactory<Solution_, Score_>(
            solutionDescriptorSupplier, constraintProvider, EnvironmentMode.PHASE_ASSERT, false);
    return scoreDirectorFactory
        .createScoreDirectorBuilder()
        .withConstraintMatchPolicy(constraintMatchPolicy)
        .build();
  }

  @Override
  public <Solution_> ConstraintFactory buildConstraintFactory(
      SolutionDescriptor<Solution_> solutionDescriptorSupplier) {
    return new BavetConstraintFactory<>(solutionDescriptorSupplier, EnvironmentMode.PHASE_ASSERT);
  }
}
