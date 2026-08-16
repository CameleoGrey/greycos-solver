package greycos.solver.core.testcotwin.cascade.single;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class TestdataSingleCascadingEasyScoreCalculator
    implements EasyScoreCalculator<TestdataSingleCascadingSolution, SimpleScore> {
  @Override
  public SimpleScore calculateScore(TestdataSingleCascadingSolution solution) {
    return SimpleScore.of(
        (int) solution.getValueList().stream().filter(e -> e.getCascadeValue() != null).count());
  }
}
