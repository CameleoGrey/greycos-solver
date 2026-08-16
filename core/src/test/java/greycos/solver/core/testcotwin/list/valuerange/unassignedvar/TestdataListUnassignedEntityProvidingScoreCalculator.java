package greycos.solver.core.testcotwin.list.valuerange.unassignedvar;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public class TestdataListUnassignedEntityProvidingScoreCalculator
    implements EasyScoreCalculator<TestdataListUnassignedEntityProvidingSolution, SimpleScore> {

  @Override
  public @NonNull SimpleScore calculateScore(
      @NonNull TestdataListUnassignedEntityProvidingSolution solution) {
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
