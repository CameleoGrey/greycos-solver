package ai.greycos.solver.core.testdomain.list.pinned.index;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public final class TestdataPinnedWithIndexListEasyScoreCalculator
    implements EasyScoreCalculator<TestdataPinnedWithIndexListSolution, SimpleScore> {

  @Override
  public @NonNull SimpleScore calculateScore(
      @NonNull TestdataPinnedWithIndexListSolution solution) {
    return SimpleScore.of(-solution.getEntityList().size());
  }
}
