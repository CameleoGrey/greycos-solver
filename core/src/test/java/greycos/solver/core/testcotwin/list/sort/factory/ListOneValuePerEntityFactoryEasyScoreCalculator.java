package greycos.solver.core.testcotwin.list.sort.factory;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public class ListOneValuePerEntityFactoryEasyScoreCalculator
    implements EasyScoreCalculator<TestdataListFactorySortableSolution, HardSoftScore> {

  @Override
  public @NonNull HardSoftScore calculateScore(
      @NonNull TestdataListFactorySortableSolution solution) {
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
