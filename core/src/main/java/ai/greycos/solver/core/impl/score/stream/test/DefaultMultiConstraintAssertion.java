package ai.greycos.solver.core.impl.score.stream.test;

import java.util.Map;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.api.score.stream.ConstraintRef;
import ai.greycos.solver.core.impl.score.constraint.ConstraintMatchTotal;
import ai.greycos.solver.core.impl.score.director.InnerScore;
import ai.greycos.solver.core.impl.score.stream.common.AbstractConstraintStreamScoreDirectorFactory;

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
