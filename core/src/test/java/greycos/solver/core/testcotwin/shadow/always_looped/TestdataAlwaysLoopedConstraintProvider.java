package greycos.solver.core.testcotwin.shadow.always_looped;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

public class TestdataAlwaysLoopedConstraintProvider implements ConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      constraintFactory
          .forEach(TestdataAlwaysLoopedEntity.class)
          .filter(TestdataAlwaysLoopedEntity::getEven)
          .groupBy(ConstraintCollectors.count())
          .filter(count -> count % 2 != 0)
          .penalize(SimpleScore.ONE)
          .asConstraint("Odd even count"),
      constraintFactory
          .forEach(TestdataAlwaysLoopedEntity.class)
          .filter(TestdataAlwaysLoopedEntity::getOdd)
          .groupBy(ConstraintCollectors.count())
          .filter(count -> count % 2 != 1)
          .penalize(SimpleScore.ONE)
          .asConstraint("Even odd count")
    };
  }
}
