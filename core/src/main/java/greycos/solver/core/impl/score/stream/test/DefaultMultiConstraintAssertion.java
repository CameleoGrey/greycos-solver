package greycos.solver.core.impl.score.stream.test;

import java.util.Map;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.impl.score.constraint.ConstraintMatchTotal;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.stream.common.AbstractConstraintStreamScoreDirectorFactory;

public final class DefaultMultiConstraintAssertion<Solution_, Score_ extends Score<Score_>>
    extends AbstractMultiConstraintAssertion<Solution_, Score_> {

  DefaultMultiConstraintAssertion(
      ConstraintProvider constraintProvider,
      AbstractConstraintStreamScoreDirectorFactory<Solution_, Score_, ?> scoreDirectorFactory,
      Score_ actualScore,
      Map<ConstraintRef, ConstraintMatchTotal<Score_>> constraintMatchTotalMap) {
    super(constraintProvider, scoreDirectorFactory);
    update(InnerScore.fullyAssigned(actualScore), constraintMatchTotalMap);
  }

  @Override
  Solution_ getSolution() {
    throw new IllegalStateException(
        "Impossible state as the solution is initialized at the constructor.");
  }
}
