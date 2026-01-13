package ai.greycos.solver.quarkus.testcotwin.dummy;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import ai.greycos.solver.quarkus.testcotwin.shadowvariable.TestdataQuarkusShadowVariableSolution;

import org.jspecify.annotations.NonNull;

public class DummyTestdataQuarkusShadowVariableEasyScoreCalculator
    implements EasyScoreCalculator<TestdataQuarkusShadowVariableSolution, SimpleScore> {
  @Override
  public @NonNull SimpleScore calculateScore(
      @NonNull TestdataQuarkusShadowVariableSolution testdataQuarkusSolution) {
    return null;
  }
}
