package ai.greycos.solver.core.impl.score.stream.test;

import java.util.Map;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.greycos.solver.core.api.score.constraint.Indictment;
import ai.greycos.solver.core.impl.score.director.InnerScore;
import ai.greycos.solver.core.impl.score.stream.common.AbstractConstraintStreamScoreDirectorFactory;

public final class DefaultSingleConstraintAssertion<Solution_, Score_ extends Score<Score_>>
    extends AbstractSingleConstraintAssertion<Solution_, Score_> {

  DefaultSingleConstraintAssertion(
      AbstractConstraintStreamScoreDirectorFactory<Solution_, Score_, ?> scoreDirectorFactory,
      Score_ score,
      Map<String, ConstraintMatchTotal<Score_>> constraintMatchTotalMap,
      Map<Object, Indictment<Score_>> indictmentMap) {
    super(scoreDirectorFactory);
    update(InnerScore.fullyAssigned(score), constraintMatchTotalMap, indictmentMap);
  }

  @Override
  Solution_ getSolution() {
    throw new IllegalStateException(
        "Impossible state as the solution is initialized at the constructor.");
  }
}
