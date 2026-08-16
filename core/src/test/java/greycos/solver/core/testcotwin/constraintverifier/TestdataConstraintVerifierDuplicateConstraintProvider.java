package greycos.solver.core.testcotwin.constraintverifier;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

public final class TestdataConstraintVerifierDuplicateConstraintProvider
    implements ConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      penalizeEveryEntity(constraintFactory), penalizeEveryEntity(constraintFactory)
    };
  }

  public Constraint penalizeEveryEntity(ConstraintFactory constraintFactory) {
    return constraintFactory
        .forEach(TestdataConstraintVerifierFirstEntity.class)
        .penalize(HardSoftScore.ONE_HARD)
        .asConstraint("Penalize every standard entity");
  }
}
