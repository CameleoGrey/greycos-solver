package ai.greycos.solver.core.testcotwin.interfaces;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

public class TestdataInterfaceConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {alwaysPenalizingConstraint(constraintFactory)};
  }

  private Constraint alwaysPenalizingConstraint(ConstraintFactory constraintFactory) {
    return constraintFactory
        .forEach(TestdataInterfaceEntity.class)
        .penalize(SimpleScore.ONE)
        .asConstraint("Always penalize");
  }
}
