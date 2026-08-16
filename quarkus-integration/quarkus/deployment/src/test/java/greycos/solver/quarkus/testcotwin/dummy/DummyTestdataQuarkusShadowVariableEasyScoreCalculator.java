package greycos.solver.quarkus.testcotwin.dummy;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.quarkus.testcotwin.shadowvariable.TestdataQuarkusShadowVariableSolution;

import org.jspecify.annotations.NonNull;

public class DummyTestdataQuarkusShadowVariableEasyScoreCalculator
    implements EasyScoreCalculator<TestdataQuarkusShadowVariableSolution, SimpleScore> {
  @Override
  public @NonNull SimpleScore calculateScore(
      @NonNull TestdataQuarkusShadowVariableSolution testdataQuarkusSolution) {
    return null;
  }
}
