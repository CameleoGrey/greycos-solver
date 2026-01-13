package ai.greycos.solver.core.testcotwin.constraintconfiguration;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

@Deprecated(forRemoval = true, since = "1.13.0")
public final class TestdataConstraintWeightEasyScoreCalculator
    implements EasyScoreCalculator<TestdataConstraintConfigurationSolution, SimpleScore> {

  @Override
  public @NonNull SimpleScore calculateScore(
      @NonNull TestdataConstraintConfigurationSolution solution) {
    SimpleScore constraintWeight = solution.getConstraintConfiguration().getFirstWeight();
    return constraintWeight.multiply(solution.getEntityList().size());
  }
}
