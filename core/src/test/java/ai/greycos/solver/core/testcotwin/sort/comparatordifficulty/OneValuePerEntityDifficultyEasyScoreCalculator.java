package ai.greycos.solver.core.testcotwin.sort.comparatordifficulty;

import java.util.Objects;

import ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public class OneValuePerEntityDifficultyEasyScoreCalculator
    implements EasyScoreCalculator<TestdataDifficultySortableSolution, HardSoftScore> {

  @Override
  public @NonNull HardSoftScore calculateScore(
      @NonNull TestdataDifficultySortableSolution solution) {
    var distinct =
        (int)
            solution.getEntityList().stream()
                .map(TestdataDifficultySortableEntity::getValue)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    var assigned =
        solution.getEntityList().stream()
            .map(TestdataDifficultySortableEntity::getValue)
            .filter(Objects::nonNull)
            .count();
    var repeated = (int) (assigned - distinct);
    return HardSoftScore.of(-repeated, -distinct);
  }
}
