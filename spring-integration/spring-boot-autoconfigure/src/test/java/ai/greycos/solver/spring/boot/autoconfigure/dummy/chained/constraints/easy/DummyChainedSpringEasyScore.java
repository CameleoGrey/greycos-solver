package ai.greycos.solver.spring.boot.autoconfigure.dummy.chained.constraints.easy;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import ai.greycos.solver.spring.boot.autoconfigure.chained.cotwin.TestdataChainedSpringSolution;

import org.jspecify.annotations.NonNull;

public class DummyChainedSpringEasyScore
    implements EasyScoreCalculator<TestdataChainedSpringSolution, SimpleScore> {
  @Override
  public @NonNull SimpleScore calculateScore(
      @NonNull TestdataChainedSpringSolution testdataSpringSolution) {
    return null;
  }
}
