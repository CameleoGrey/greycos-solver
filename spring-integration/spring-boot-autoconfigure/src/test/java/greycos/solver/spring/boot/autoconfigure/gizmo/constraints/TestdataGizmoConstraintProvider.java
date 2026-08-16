package greycos.solver.spring.boot.autoconfigure.gizmo.constraints;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.spring.boot.autoconfigure.gizmo.cotwin.TestdataGizmoSpringEntity;

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
