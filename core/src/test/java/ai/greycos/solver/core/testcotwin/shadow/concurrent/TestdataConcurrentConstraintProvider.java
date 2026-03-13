package ai.greycos.solver.core.testcotwin.shadow.concurrent;

import static ai.greycos.solver.core.testcotwin.shadow.concurrent.TestdataConcurrentValue.BASE_START_TIME;

import java.time.Duration;

import ai.greycos.solver.core.api.score.HardSoftScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

public class TestdataConcurrentConstraintProvider implements ConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      constraintFactory
          .forEachUnfiltered(TestdataConcurrentValue.class)
          .filter(TestdataConcurrentValue::isInconsistent)
          .penalize(HardSoftScore.ONE_HARD)
          .asConstraint("Invalid visit"),
      constraintFactory
          .forEach(TestdataConcurrentValue.class)
          .filter(TestdataConcurrentValue::isAssigned)
          .penalize(
              HardSoftScore.ONE_SOFT,
              visit ->
                  (int) Duration.between(BASE_START_TIME, visit.getServiceFinishTime()).toMinutes())
          .asConstraint("Minimize finish time")
    };
  }
}
