package greycos.solver.core.config.solver.testutil.corruptedundoshadow;

import java.util.Objects;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public class CorruptedUndoShadowEasyScoreCalculator
    implements EasyScoreCalculator<CorruptedUndoShadowSolution, SimpleScore> {
  @Override
  public @NonNull SimpleScore calculateScore(
      @NonNull CorruptedUndoShadowSolution corruptedUndoShadowSolution) {
    int score = 0;
    for (CorruptedUndoShadowEntity entity : corruptedUndoShadowSolution.entityList) {
      if (Objects.equals(entity.value, entity.valueClone)) {
        score++;
      }
    }
    return SimpleScore.of(score);
  }
}
