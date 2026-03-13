package ai.greycos.solver.core.testcotwin.unassignedvar;

import java.util.Objects;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import ai.greycos.solver.core.testcotwin.TestdataValue;

import org.jspecify.annotations.NonNull;

public class TestdataAllowsUnassignedEasyScoreCalculator
    implements EasyScoreCalculator<TestdataAllowsUnassignedSolution, SimpleScore> {
  @Override
  public @NonNull SimpleScore calculateScore(@NonNull TestdataAllowsUnassignedSolution solution) {
    int score = 0;
    for (TestdataAllowsUnassignedEntity left : solution.getEntityList()) {
      TestdataValue value = left.getValue();
      if (value == null) {
        score -= 1;
      } else {
        for (TestdataAllowsUnassignedEntity right : solution.getEntityList()) {
          if (left != right && Objects.equals(right.getValue(), value)) {
            score -= 1000;
          }
        }
      }
    }
    return SimpleScore.of(score);
  }
}
