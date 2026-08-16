package greycos.solver.spring.boot.autoconfigure.multiple.constraintprovider.constraints;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.spring.boot.autoconfigure.multiple.constraintprovider.cotwin.TestdataMultipleConstraintEntity;

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
