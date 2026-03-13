package ai.greycos.solver.core.testcotwin.valuerange.sort.comparator;

import java.util.Objects;

import ai.greycos.solver.core.api.score.HardSoftScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public class OneValuePerEntityComparatorRangeEasyScoreCalculator
    implements EasyScoreCalculator<
        TestdataComparatorSortableEntityProvidingSolution, HardSoftScore> {

  @Override
  public @NonNull HardSoftScore calculateScore(
      @NonNull TestdataComparatorSortableEntityProvidingSolution solution) {
    var distinct =
        (int)
            solution.getEntityList().stream()
                .map(TestdataComparatorSortableEntityProvidingEntity::getValue)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    var assigned =
        solution.getEntityList().stream()
            .map(TestdataComparatorSortableEntityProvidingEntity::getValue)
            .filter(Objects::nonNull)
            .count();
    var repeated = (int) (assigned - distinct);
    return HardSoftScore.of(-repeated, -distinct);
  }
}
