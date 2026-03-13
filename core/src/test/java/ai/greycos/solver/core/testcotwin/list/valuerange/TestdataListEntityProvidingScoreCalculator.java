package ai.greycos.solver.core.testcotwin.list.valuerange;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public class TestdataListEntityProvidingScoreCalculator
    implements EasyScoreCalculator<TestdataListEntityProvidingSolution, SimpleScore> {

  @Override
  public @NonNull SimpleScore calculateScore(
      @NonNull TestdataListEntityProvidingSolution solution) {
    int score = 0;
    for (var entity : solution.getEntityList()) {
      if (entity.getValueList().size() >= 2) {
        score += 2;
      } else {
        score += 1;
      }
    }
    return SimpleScore.of(score);
  }
}
