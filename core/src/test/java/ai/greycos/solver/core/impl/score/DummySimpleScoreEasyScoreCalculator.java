package ai.greycos.solver.core.impl.score;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public class DummySimpleScoreEasyScoreCalculator<Solution_>
    implements EasyScoreCalculator<Solution_, SimpleScore> {

  @Override
  public @NonNull SimpleScore calculateScore(@NonNull Solution_ solution_) {
    return SimpleScore.of(0);
  }
}
