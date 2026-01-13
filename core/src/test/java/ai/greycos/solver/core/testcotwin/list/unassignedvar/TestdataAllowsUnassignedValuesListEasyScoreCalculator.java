package ai.greycos.solver.core.testcotwin.list.unassignedvar;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public final class TestdataAllowsUnassignedValuesListEasyScoreCalculator
    implements EasyScoreCalculator<TestdataAllowsUnassignedValuesListSolution, SimpleScore> {

  @Override
  public @NonNull SimpleScore calculateScore(
      @NonNull TestdataAllowsUnassignedValuesListSolution solution) {
    int i = 0;
    for (TestdataAllowsUnassignedValuesListEntity entity : solution.getEntityList()) {
      i += entity.getValueList().size();
    }
    return SimpleScore.of(-i);
  }
}
