package greycos.solver.core.impl.score.stream.test;

import java.util.Map;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.impl.score.constraint.ConstraintMatchTotal;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.stream.common.AbstractConstraintStreamScoreDirectorFactory;

public final class DefaultSingleConstraintAssertion<Solution_, Score_ extends Score<Score_>>
    extends AbstractSingleConstraintAssertion<Solution_, Score_> {

  DefaultSingleConstraintAssertion(
      AbstractConstraintStreamScoreDirectorFactory<Solution_, Score_, ?> scoreDirectorFactory,
      Score_ score,
      Map<ConstraintRef, ConstraintMatchTotal<Score_>> constraintMatchTotalMap) {
    super(scoreDirectorFactory);
    update(InnerScore.fullyAssigned(score), constraintMatchTotalMap);
  }

  @Override
  Solution_ getSolution() {
    throw new IllegalStateException(
        "Impossible state as the solution is initialized at the constructor.");
  }
}
