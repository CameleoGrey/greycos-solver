package greycos.solver.core.testcotwin.list.pinned.index;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public final class TestdataPinnedWithIndexListEasyScoreCalculator
    implements EasyScoreCalculator<TestdataPinnedWithIndexListSolution, SimpleScore> {

  @Override
  public @NonNull SimpleScore calculateScore(
      @NonNull TestdataPinnedWithIndexListSolution solution) {
    return SimpleScore.of(-solution.getEntityList().size());
  }
}
