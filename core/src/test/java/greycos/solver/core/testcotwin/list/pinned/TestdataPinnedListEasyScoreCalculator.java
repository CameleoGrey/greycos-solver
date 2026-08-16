package greycos.solver.core.testcotwin.list.pinned;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public final class TestdataPinnedListEasyScoreCalculator
    implements EasyScoreCalculator<TestdataPinnedListSolution, SimpleScore> {

  @Override
  public @NonNull SimpleScore calculateScore(@NonNull TestdataPinnedListSolution solution) {
    return SimpleScore.of(-solution.getEntityList().size());
  }
}
