package greycos.solver.core.impl.score.stream.common;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.director.InnerScoreDirector;

public interface ConstraintStreamImplSupport {

  ConstraintMatchPolicy constraintMatchPolicy();

  <Score_ extends Score<Score_>, Solution_>
      InnerScoreDirector<Solution_, Score_> buildScoreDirector(
          SolutionDescriptor<Solution_> solutionDescriptorSupplier,
          ConstraintProvider constraintProvider);

  <Solution_> ConstraintFactory buildConstraintFactory(
      SolutionDescriptor<Solution_> solutionDescriptorSupplier);
}
