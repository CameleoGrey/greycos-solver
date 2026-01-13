package ai.greycos.solver.quarkus.testcotwin.gizmo;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.api.score.stream.Joiners;

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
