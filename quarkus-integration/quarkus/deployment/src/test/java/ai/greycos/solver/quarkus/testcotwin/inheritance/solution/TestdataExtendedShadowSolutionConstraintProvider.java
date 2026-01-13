package ai.greycos.solver.quarkus.testcotwin.inheritance.solution;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.shadow.TestdataExtendedShadowEntity;

import org.jspecify.annotations.NonNull;

public class TestdataExtendedShadowSolutionConstraintProvider implements ConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      constraintFactory
          .forEach(TestdataExtendedShadowEntity.class)
          .filter(e -> e.myPlanningVariable.id != e.desiredId)
          .penalize(SimpleScore.ONE)
          .asConstraint("Variable does not match desired id")
    };
  }
}
