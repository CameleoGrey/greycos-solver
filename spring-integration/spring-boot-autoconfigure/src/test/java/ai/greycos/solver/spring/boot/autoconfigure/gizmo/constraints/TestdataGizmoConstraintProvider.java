package ai.greycos.solver.spring.boot.autoconfigure.gizmo.constraints;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.api.score.stream.Joiners;
import ai.greycos.solver.spring.boot.autoconfigure.gizmo.domain.TestdataGizmoSpringEntity;

import org.jspecify.annotations.NonNull;

public class TestdataGizmoConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(TestdataGizmoSpringEntity.class)
          .join(TestdataGizmoSpringEntity.class, Joiners.equal(TestdataGizmoSpringEntity::getValue))
          .filter((a, b) -> a != b)
          .penalize(SimpleScore.ONE)
          .asConstraint("Don't assign 2 entities the same value.")
    };
  }
}
