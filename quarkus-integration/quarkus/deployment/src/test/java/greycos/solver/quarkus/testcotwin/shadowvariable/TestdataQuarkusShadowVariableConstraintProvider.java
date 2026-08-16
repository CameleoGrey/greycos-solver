package greycos.solver.quarkus.testcotwin.shadowvariable;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;

import org.jspecify.annotations.NonNull;

public class TestdataQuarkusShadowVariableConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(TestdataQuarkusShadowVariableEntity.class)
          .join(
              TestdataQuarkusShadowVariableEntity.class,
              Joiners.equal(
                  TestdataQuarkusShadowVariableEntity::getValue1,
                  TestdataQuarkusShadowVariableEntity::getValue2))
          .filter((a, b) -> a.getValue1AndValue2().equals(b.getValue1AndValue2()))
          .penalize(SimpleScore.ONE)
          .asConstraint("Don't assign 2 entities the same value.")
    };
  }
}
