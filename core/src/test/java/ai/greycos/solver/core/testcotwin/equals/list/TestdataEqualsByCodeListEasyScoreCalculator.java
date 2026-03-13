package ai.greycos.solver.core.testcotwin.equals.list;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public final class TestdataEqualsByCodeListEasyScoreCalculator
    implements EasyScoreCalculator<TestdataEqualsByCodeListSolution, SimpleScore> {

  @Override
  public @NonNull SimpleScore calculateScore(@NonNull TestdataEqualsByCodeListSolution solution) {
    return SimpleScore.of(-solution.getEntityList().size());
  }
}
