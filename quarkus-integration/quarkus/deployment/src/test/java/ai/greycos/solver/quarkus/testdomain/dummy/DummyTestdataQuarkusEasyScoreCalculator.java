package ai.greycos.solver.quarkus.testdomain.dummy;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusSolution;

import org.jspecify.annotations.NonNull;

public class DummyTestdataQuarkusEasyScoreCalculator
    implements EasyScoreCalculator<TestdataQuarkusSolution, SimpleScore> {
  @Override
  public @NonNull SimpleScore calculateScore(
      @NonNull TestdataQuarkusSolution testdataQuarkusSolution) {
    return null;
  }
}
