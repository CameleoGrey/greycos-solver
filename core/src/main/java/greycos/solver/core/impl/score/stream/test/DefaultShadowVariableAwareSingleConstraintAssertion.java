package greycos.solver.core.impl.score.stream.test;

import java.util.Objects;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.test.ShadowVariableAwareSingleConstraintAssertion;
import greycos.solver.core.api.score.stream.test.SingleConstraintAssertion;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.stream.common.AbstractConstraintStreamScoreDirectorFactory;

public final class DefaultShadowVariableAwareSingleConstraintAssertion<
        Solution_, Score_ extends Score<Score_>>
    extends AbstractSingleConstraintAssertion<Solution_, Score_>
    implements ShadowVariableAwareSingleConstraintAssertion {

  private final AbstractConstraintStreamScoreDirectorFactory<Solution_, Score_, ?>
      scoreDirectorFactory;
  private final Solution_ solution;

  DefaultShadowVariableAwareSingleConstraintAssertion(
      AbstractConstraintStreamScoreDirectorFactory<Solution_, Score_, ?> scoreDirectorFactory,
      Solution_ solution) {
    super(scoreDirectorFactory);
    this.scoreDirectorFactory = scoreDirectorFactory;
    this.solution = Objects.requireNonNull(solution);
  }

  @Override
  public SingleConstraintAssertion settingAllShadowVariables() {
    // Most score directors don't need derived status; CS will override this.
    try (var scoreDirector =
        scoreDirectorFactory
            .createScoreDirectorBuilder()
            .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED)
            .buildDerived()) {
      scoreDirector.setWorkingSolution(solution);
      update(scoreDirector.calculateScore(), scoreDirector.getConstraintMatchTotalMap());
      toggleInitialized();
      return this;
    }
  }

  @Override
  Solution_ getSolution() {
    return solution;
  }
}
