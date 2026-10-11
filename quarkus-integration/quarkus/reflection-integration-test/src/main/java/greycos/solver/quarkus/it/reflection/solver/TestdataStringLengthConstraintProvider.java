package greycos.solver.quarkus.it.reflection.solver;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.quarkus.it.reflection.cotwin.TestdataMultiListFixture;
import greycos.solver.quarkus.it.reflection.cotwin.TestdataReflectionEntity;

import org.jspecify.annotations.NonNull;

public class TestdataStringLengthConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(TestdataMultiListFixture.FirstElement.class)
          .filter(TestdataMultiListFixture.FirstElement::hasInvalidShadows)
          .penalize(HardSoftScore.ONE_HARD)
          .asConstraint("First-list shadows must be consistent"),
      factory
          .forEach(TestdataMultiListFixture.SecondElement.class)
          .filter(TestdataMultiListFixture.SecondElement::hasInvalidShadows)
          .penalize(HardSoftScore.ONE_HARD)
          .asConstraint("Second-list shadows must be consistent"),
      factory
          .forEach(TestdataReflectionEntity.class)
          .join(
              TestdataReflectionEntity.class,
              Joiners.equal(TestdataReflectionEntity::getMethodValue))
          .filter((a, b) -> !a.fieldValue.equals(b.fieldValue))
          .penalize(HardSoftScore.ONE_HARD)
          .asConstraint("Entities with equal method values should have equal field values"),
      factory
          .forEach(TestdataReflectionEntity.class)
          .reward(HardSoftScore.ONE_SOFT, entity -> entity.getMethodValue().length())
          .asConstraint("Maximize method value length")
    };
  }
}
