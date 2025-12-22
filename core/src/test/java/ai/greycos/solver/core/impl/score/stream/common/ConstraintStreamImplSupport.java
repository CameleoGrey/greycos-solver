package ai.greycos.solver.core.impl.score.stream.common;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;

public interface ConstraintStreamImplSupport {

  ConstraintMatchPolicy constraintMatchPolicy();

  <Score_ extends Score<Score_>, Solution_>
      InnerScoreDirector<Solution_, Score_> buildScoreDirector(
          SolutionDescriptor<Solution_> solutionDescriptorSupplier,
          ConstraintProvider constraintProvider);

  <Solution_> ConstraintFactory buildConstraintFactory(
      SolutionDescriptor<Solution_> solutionDescriptorSupplier);
}
