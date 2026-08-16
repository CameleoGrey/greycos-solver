package greycos.solver.core.testcotwin.equals;

import java.util.Objects;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public class TestdataEqualsByCodeEasyScoreCalculator
    implements EasyScoreCalculator<TestdataEqualsByCodeSolution, SimpleScore> {
  @Override
  public @NonNull SimpleScore calculateScore(@NonNull TestdataEqualsByCodeSolution solution) {
    int score = 0;
    for (TestdataEqualsByCodeEntity left : solution.getEntityList()) {
      TestdataEqualsByCodeValue value = left.getValue();
      if (value == null) {
        continue;
      }
      for (TestdataEqualsByCodeEntity right : solution.getEntityList()) {
        if (left != right && Objects.equals(right.getValue(), value)) {
          score -= 1;
        }
      }
    }
    return SimpleScore.of(score);
  }
}
