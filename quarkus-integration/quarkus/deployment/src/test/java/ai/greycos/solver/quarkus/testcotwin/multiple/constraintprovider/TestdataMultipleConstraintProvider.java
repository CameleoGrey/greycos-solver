package ai.greycos.solver.quarkus.testcotwin.multiple.constraintprovider;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.Joiners;

import org.jspecify.annotations.NonNull;

public class TestdataMultipleConstraintProvider extends TestdataAbstractMultipleConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(TestdataMultipleConstraintEntity.class)
          .join(
              TestdataMultipleConstraintEntity.class,
              Joiners.equal(TestdataMultipleConstraintEntity::getValue))
          .filter((a, b) -> a != b)
          .penalize(SimpleScore.ONE, (e1, e2) -> 1)
          .asConstraint("Don't assign 2 entities the same value.")
    };
  }
}
