package ai.greycos.solver.spring.boot.autoconfigure.multiple.constraintprovider.constraints;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.api.score.stream.Joiners;
import ai.greycos.solver.spring.boot.autoconfigure.multiple.constraintprovider.cotwin.TestdataMultipleConstraintEntity;

import org.jspecify.annotations.NonNull;

public abstract class TestdataAbstractMultipleConstraintProvider implements ConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(TestdataMultipleConstraintEntity.class)
          .join(
              TestdataMultipleConstraintEntity.class,
              Joiners.equal(TestdataMultipleConstraintEntity::getValue))
          .filter((a, b) -> a != b)
          .penalize(SimpleScore.ONE)
          .asConstraint("Don't assign 2 entities the same value.")
    };
  }
}
