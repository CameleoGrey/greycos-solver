package greycos.solver.quarkus.testcotwin.gizmo;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;

import org.jspecify.annotations.NonNull;

public class PrivateNoArgsConstructorConstraintProvider implements ConstraintProvider {

  private PrivateNoArgsConstructorConstraintProvider() {}

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      constraintFactory
          .forEachUniquePair(PrivateNoArgsConstructorEntity.class, Joiners.equal(p -> p.value))
          .penalize(SimpleScore.ONE)
          .asConstraint("Same value")
    };
  }
}
