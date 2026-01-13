package ai.greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public class TestdataAllowsUnassignedEntityProvidingScoreCalculator
    implements EasyScoreCalculator<TestdataAllowsUnassignedEntityProvidingSolution, SimpleScore> {

  @Override
  public @NonNull SimpleScore calculateScore(
      @NonNull TestdataAllowsUnassignedEntityProvidingSolution solution) {
    int score = 0;
    for (var entity : solution.getEntityList()) {
      if (entity.getValue() == null) {
        score -= 1;
      } else {
        score += 1;
      }
    }
    return SimpleScore.of(score);
  }
}
