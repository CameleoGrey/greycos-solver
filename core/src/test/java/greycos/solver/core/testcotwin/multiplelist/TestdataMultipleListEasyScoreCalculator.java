package greycos.solver.core.testcotwin.multiplelist;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;

/** Scores genuine state only, independently of list shadows. */
public class TestdataMultipleListEasyScoreCalculator
    implements EasyScoreCalculator<TestdataMultipleListSolution, SimpleScore> {

  @Override
  public SimpleScore calculateScore(TestdataMultipleListSolution solution) {
    long penalty = 0;
    for (int ownerIndex = 0; ownerIndex < solution.getEntityList().size(); ownerIndex++) {
      var owner = solution.getEntityList().get(ownerIndex);
      penalty += owner.getBasicValue() == null ? 0 : Math.abs(owner.getBasicValue() - ownerIndex);
      penalty += Math.abs(owner.getFirstValueList().size() - owner.getSecondValueList().size());
      for (int index = 0; index < owner.getFirstValueList().size(); index++) {
        int ordinal = solution.getFirstValueList().indexOf(owner.getFirstValueList().get(index));
        penalty += Math.abs(ordinal - index * solution.getEntityList().size() - ownerIndex);
      }
      for (int index = 0; index < owner.getSecondValueList().size(); index++) {
        var value = owner.getSecondValueList().get(index);
        int ordinal = solution.getSecondValueList().indexOf(value);
        penalty += Math.abs(ordinal - index * solution.getEntityList().size() - ownerIndex);
        if (value.getQuantity() != null) {
          penalty += Math.abs(value.getQuantity() - (ordinal % 4));
        }
      }
    }
    return SimpleScore.of(-penalty);
  }
}
