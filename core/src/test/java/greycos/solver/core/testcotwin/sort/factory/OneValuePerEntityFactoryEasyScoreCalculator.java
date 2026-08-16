package greycos.solver.core.testcotwin.sort.factory;

import java.util.Objects;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public class OneValuePerEntityFactoryEasyScoreCalculator
    implements EasyScoreCalculator<TestdataFactorySortableSolution, HardSoftScore> {

  @Override
  public @NonNull HardSoftScore calculateScore(@NonNull TestdataFactorySortableSolution solution) {
    var distinct =
        (int)
            solution.getEntityList().stream()
                .map(TestdataFactorySortableEntity::getValue)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    var assigned =
        solution.getEntityList().stream()
            .map(TestdataFactorySortableEntity::getValue)
            .filter(Objects::nonNull)
            .count();
    var repeated = (int) (assigned - distinct);
    return HardSoftScore.of(-repeated, -distinct);
  }
}
