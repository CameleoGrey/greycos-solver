package ai.greycos.solver.spring.boot.it.solver;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.spring.boot.it.domain.IntegrationTestEntity;

import org.jspecify.annotations.NonNull;

public class IntegrationTestConstraintProvider implements ConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      constraintFactory
          .forEach(IntegrationTestEntity.class)
          .filter(entity -> !entity.getId().equals(entity.getValue().id()))
          .penalize(SimpleScore.ONE)
          .asConstraint("Entity id do not match value id")
    };
  }
}
