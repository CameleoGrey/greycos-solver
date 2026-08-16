package greycos.solver.core.impl.score.stream.test;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.test.MultiConstraintVerification;
import greycos.solver.core.impl.score.stream.common.AbstractConstraintStreamScoreDirectorFactory;

import org.jspecify.annotations.NonNull;

public final class DefaultMultiConstraintVerification<Solution_, Score_ extends Score<Score_>>
    extends AbstractConstraintVerification<Solution_, Score_>
    implements MultiConstraintVerification<Solution_> {

  private final ConstraintProvider constraintProvider;

  DefaultMultiConstraintVerification(
      AbstractConstraintStreamScoreDirectorFactory<Solution_, Score_, ?> scoreDirectorFactory,
      ConstraintProvider constraintProvider) {
    super(scoreDirectorFactory);
    this.constraintProvider = constraintProvider;
  }

  @Override
  public @NonNull DefaultMultiConstraintAssertion<Solution_, Score_> given(
      @NonNull Object @NonNull ... facts) {
    assertCorrectArguments(facts);
    return sessionBasedAssertionBuilder.multiConstraintGiven(constraintProvider, facts);
  }

  @Override
  public @NonNull DefaultShadowVariableAwareMultiConstraintAssertion<Solution_, Score_>
      givenSolution(@NonNull Solution_ solution) {
    return new DefaultShadowVariableAwareMultiConstraintAssertion<>(
        constraintProvider, scoreDirectorFactory, solution);
  }
}
