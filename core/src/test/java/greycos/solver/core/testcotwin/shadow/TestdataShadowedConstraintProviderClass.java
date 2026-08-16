package greycos.solver.core.testcotwin.shadow;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;

import org.jspecify.annotations.NonNull;

public final class TestdataShadowedConstraintProviderClass implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      constraintFactory
          .forEach(TestdataShadowedEntity.class)
          .filter(entity -> entity.getValue() != null)
          .join(TestdataShadowedEntity.class, Joiners.equal(TestdataShadowedEntity::getValue))
          .penalize(SimpleScore.ONE)
          .asConstraint("testConstraint")
    };
  }
}
