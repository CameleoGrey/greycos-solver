package greycos.solver.quarkus.testcotwin.dummy;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusSolution;

import org.jspecify.annotations.NonNull;

public class DummyTestdataQuarkusEasyScoreCalculator
    implements EasyScoreCalculator<TestdataQuarkusSolution, SimpleScore> {
  @Override
  public @NonNull SimpleScore calculateScore(
      @NonNull TestdataQuarkusSolution testdataQuarkusSolution) {
    return null;
  }
}
