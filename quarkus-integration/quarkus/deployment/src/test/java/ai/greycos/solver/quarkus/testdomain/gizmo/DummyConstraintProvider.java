package ai.greycos.solver.quarkus.testdomain.gizmo;

import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

public class DummyConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[0];
  }
}
