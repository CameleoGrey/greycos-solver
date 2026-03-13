package ai.greycos.solver.core.testcotwin.constraintweightoverrides;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.testcotwin.TestdataEntity;

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
        .penalize(SimpleScore.ONE)
        .asConstraint("First weight");
  }

  public Constraint secondConstraint(ConstraintFactory constraintFactory) {
    return constraintFactory
        .forEach(TestdataEntity.class)
        .reward(SimpleScore.of(2))
        .asConstraint("Second weight");
  }
}
