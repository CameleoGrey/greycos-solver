package ai.greycos.solver.core.testdomain.inheritance.entity.multiple.baseannotated.classes.childnot;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.testdomain.inheritance.entity.multiple.baseannotated.classes.childtoo.TestdataMultipleBothAnnotatedChildEntity;

import org.jspecify.annotations.NonNull;

public class TestMultipleChildNotAnnotatedConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(TestdataMultipleBothAnnotatedChildEntity.class)
          .filter(e -> e.getValue() != null)
          .reward(SimpleScore.ONE, value -> 1)
          .asConstraint("Constraint")
    };
  }
}
