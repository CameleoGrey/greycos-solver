package ai.greycos.solver.core.testdomain.shadow.concurrent;

import static ai.greycos.solver.core.testdomain.shadow.concurrent.TestdataConcurrentValue.BASE_START_TIME;

import java.time.Duration;

import ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;

import org.jspecify.annotations.NonNull;

public class TestdataConcurrentAssertionEasyScoreCalculator
    implements EasyScoreCalculator<TestdataConcurrentSolution, HardSoftScore> {
  @Override
  public @NonNull HardSoftScore calculateScore(@NonNull TestdataConcurrentSolution routePlan) {
    var hardScore = 0;
    var softScore = 0;

    for (var visit : routePlan.values) {
      if (visit.getExpectedInconsistent()) {
        hardScore--;
      }
      if (visit.isAssigned()) {
        if (!visit.getExpectedInconsistent()) {
          softScore -=
              (int)
                  Duration.between(BASE_START_TIME, visit.getExpectedServiceFinishTime())
                      .toMinutes();
        }
      }
    }
    return HardSoftScore.of(hardScore, softScore);
  }
}
