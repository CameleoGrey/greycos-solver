package greycos.solver.core.testcotwin.valuerange.entityproviding;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

public final class TestdataEntityProvidingConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {alwaysPenalizingConstraint(constraintFactory)};
  }

  private Constraint alwaysPenalizingConstraint(ConstraintFactory constraintFactory) {
    return constraintFactory
        .forEach(TestdataEntityProvidingEntity.class)
        .penalize(SimpleScore.ONE)
        .asConstraint("Always penalize");
  }
}
