package ai.greycos.solver.core.testdomain.shadow.full;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class TestdataShadowedFullEasyScoreCalculator
    implements EasyScoreCalculator<TestdataShadowedFullSolution, SimpleScore> {
  @Override
  public SimpleScore calculateScore(TestdataShadowedFullSolution solution) {
    return SimpleScore.ZERO;
  }
}
