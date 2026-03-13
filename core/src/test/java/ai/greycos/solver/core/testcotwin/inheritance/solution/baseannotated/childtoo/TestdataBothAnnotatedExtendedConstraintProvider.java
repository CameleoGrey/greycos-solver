package ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childtoo;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;

import org.jspecify.annotations.NonNull;

public class TestdataBothAnnotatedExtendedConstraintProvider
    extends TestdataBothAnnotatedConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
    return new Constraint[] {
      super.defineConstraints(factory)[0],
      factory
          .forEach(TestdataBothAnnotatedChildEntity.class)
          .filter(e -> e.getValue() != null)
          .reward(SimpleScore.ONE, value -> 1)
          .asConstraint("Constraint2")
    };
  }
}
