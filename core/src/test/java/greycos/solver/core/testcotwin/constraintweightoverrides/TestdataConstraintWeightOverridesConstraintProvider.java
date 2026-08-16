package greycos.solver.core.testcotwin.constraintweightoverrides;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEntity;

import org.jspecify.annotations.NonNull;

public final class TestdataConstraintWeightOverridesConstraintProvider
    implements ConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      firstConstraint(constraintFactory), secondConstraint(constraintFactory)
    };
  }

  public Constraint firstConstraint(ConstraintFactory constraintFactory) {
    return constraintFactory
        .forEach(TestdataEntity.class)
        .reward(SimpleScore.ONE)
        .asConstraint("First weight");
  }

  public Constraint secondConstraint(ConstraintFactory constraintFactory) {
    return constraintFactory
        .forEach(TestdataEntity.class)
        .penalize(SimpleScore.ZERO)
        .asConstraint("Second weight");
  }
}
