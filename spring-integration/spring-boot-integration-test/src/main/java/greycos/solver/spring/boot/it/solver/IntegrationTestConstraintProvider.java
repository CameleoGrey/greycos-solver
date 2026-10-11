package greycos.solver.spring.boot.it.solver;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.spring.boot.it.cotwin.IntegrationTestEntity;
import greycos.solver.spring.boot.it.cotwin.TestdataMultiListFixture;

import org.jspecify.annotations.NonNull;

public class IntegrationTestConstraintProvider implements ConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      constraintFactory
          .forEach(TestdataMultiListFixture.FirstElement.class)
          .filter(TestdataMultiListFixture.FirstElement::hasInvalidShadows)
          .penalize(SimpleScore.ONE)
          .asConstraint("First-list shadows must be consistent"),
      constraintFactory
          .forEach(TestdataMultiListFixture.SecondElement.class)
          .filter(TestdataMultiListFixture.SecondElement::hasInvalidShadows)
          .penalize(SimpleScore.ONE)
          .asConstraint("Second-list shadows must be consistent"),
      constraintFactory
          .forEach(IntegrationTestEntity.class)
          .filter(entity -> !entity.getId().equals(entity.getValue().id()))
          .penalize(SimpleScore.ONE)
          .asConstraint("Entity id do not match value id")
    };
  }
}
