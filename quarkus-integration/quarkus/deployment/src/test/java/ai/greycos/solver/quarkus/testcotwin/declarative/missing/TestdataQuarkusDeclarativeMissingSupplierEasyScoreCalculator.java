package ai.greycos.solver.quarkus.testcotwin.declarative.missing;

import ai.greycos.solver.core.api.score.HardSoftScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public class TestdataQuarkusDeclarativeMissingSupplierEasyScoreCalculator
    implements EasyScoreCalculator<
        TestdataQuarkusDeclarativeMissingSupplierSolution, HardSoftScore> {
  @Override
  public @NonNull HardSoftScore calculateScore(
      @NonNull TestdataQuarkusDeclarativeMissingSupplierSolution
          testdataQuarkusDeclarativeMissingSupplierSolution) {
    return HardSoftScore.ZERO;
  }
}
