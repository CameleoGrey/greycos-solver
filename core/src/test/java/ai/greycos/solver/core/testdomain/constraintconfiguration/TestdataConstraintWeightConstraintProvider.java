package ai.greycos.solver.core.testdomain.constraintconfiguration;

import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.testdomain.TestdataEntity;

import org.jspecify.annotations.NonNull;

@Deprecated(forRemoval = true, since = "1.13.0")
public final class TestdataConstraintWeightConstraintProvider implements ConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {onlyConstraint(constraintFactory)};
  }

  private Constraint onlyConstraint(ConstraintFactory constraintFactory) {
    return constraintFactory
        .forEach(TestdataEntity.class)
        .rewardConfigurable()
        .asConstraint("First weight");
  }
}
