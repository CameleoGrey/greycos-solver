package greycos.solver.core.testcotwin.common;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public class DummyHardSoftEasyScoreCalculator
    implements EasyScoreCalculator<Object, HardSoftScore> {

  @Override
  public @NonNull HardSoftScore calculateScore(@NonNull Object o) {
    return HardSoftScore.ZERO;
  }
}
