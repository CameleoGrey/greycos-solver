package ai.greycos.solver.core.testdomain.inheritance.entity.single.baseannotated.interfaces.childnot;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

public class TestChildNotAnnotatedInterfaceConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(TestdataChildNotAnnotatedInterfaceChildEntity.class)
          .filter(e -> e.getValue() != null)
          .reward(SimpleScore.ONE, value -> 1)
          .asConstraint("Constraint")
    };
  }
}
