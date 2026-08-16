package greycos.solver.quarkus.it.solver;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.quarkus.it.cotwin.TestdataStringLengthShadowEntity;

import org.jspecify.annotations.NonNull;

public class TestdataStringLengthConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(TestdataStringLengthShadowEntity.class)
          .join(
              TestdataStringLengthShadowEntity.class,
              Joiners.equal(TestdataStringLengthShadowEntity::getValue))
          .filter((a, b) -> a != b)
          .penalize(HardSoftScore.ONE_HARD)
          .asConstraint("Don't assign 2 entities the same value."),
      factory
          .forEach(TestdataStringLengthShadowEntity.class)
          .reward(HardSoftScore.ONE_SOFT, TestdataStringLengthShadowEntity::getLength)
          .asConstraint("Maximize value length")
    };
  }
}
