package ai.greycos.solver.core.testcotwin.shadow;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.api.score.stream.Joiners;

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
