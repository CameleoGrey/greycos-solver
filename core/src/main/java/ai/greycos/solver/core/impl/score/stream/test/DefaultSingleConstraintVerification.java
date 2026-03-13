package ai.greycos.solver.core.impl.score.stream.test;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.stream.test.SingleConstraintVerification;
import ai.greycos.solver.core.impl.score.stream.common.AbstractConstraintStreamScoreDirectorFactory;

import org.jspecify.annotations.NonNull;

public final class DefaultSingleConstraintVerification<Solution_, Score_ extends Score<Score_>>
    extends AbstractConstraintVerification<Solution_, Score_>
    implements SingleConstraintVerification<Solution_> {

  DefaultSingleConstraintVerification(
      AbstractConstraintStreamScoreDirectorFactory<Solution_, Score_, ?> scoreDirectorFactory) {
    super(scoreDirectorFactory);
  }

  @Override
  public @NonNull DefaultSingleConstraintAssertion<Solution_, Score_> given(
      @NonNull Object @NonNull ... facts) {
    assertCorrectArguments(facts);
    return sessionBasedAssertionBuilder.singleConstraintGiven(facts);
  }

  @Override
  public @NonNull DefaultShadowVariableAwareSingleConstraintAssertion<Solution_, Score_>
      givenSolution(@NonNull Solution_ solution) {
    return new DefaultShadowVariableAwareSingleConstraintAssertion<>(
        scoreDirectorFactory, solution);
  }
}
