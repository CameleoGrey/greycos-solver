package greycos.solver.spring.boot.autoconfigure.dummy.normal.constraints.easy;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.spring.boot.autoconfigure.normal.cotwin.TestdataSpringSolution;

import org.jspecify.annotations.NonNull;

public class DummySpringEasyScore
    implements EasyScoreCalculator<TestdataSpringSolution, SimpleScore> {
  @Override
  public @NonNull SimpleScore calculateScore(
      @NonNull TestdataSpringSolution testdataSpringSolution) {
    return null;
  }
}
