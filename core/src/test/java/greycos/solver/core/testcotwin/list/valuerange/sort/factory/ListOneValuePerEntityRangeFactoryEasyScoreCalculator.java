package greycos.solver.core.testcotwin.list.valuerange.sort.factory;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public class ListOneValuePerEntityRangeFactoryEasyScoreCalculator
    implements EasyScoreCalculator<
        TestdataListFactorySortableEntityProvidingSolution, HardSoftScore> {

  @Override
  public @NonNull HardSoftScore calculateScore(
      @NonNull TestdataListFactorySortableEntityProvidingSolution solution) {
    var softScore = 0;
    var hardScore = 0;
    for (var entity : solution.getEntityList()) {
      if (entity.getValueList().size() == 1) {
        softScore -= 10;
      } else {
        hardScore -= 10;
      }
      hardScore--;
    }
    return HardSoftScore.of(hardScore, softScore);
  }
}
