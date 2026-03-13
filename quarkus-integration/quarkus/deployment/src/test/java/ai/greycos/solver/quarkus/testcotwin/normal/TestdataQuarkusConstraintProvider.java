package ai.greycos.solver.quarkus.testcotwin.normal;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.api.score.stream.Joiners;

import org.jspecify.annotations.NonNull;

public class TestdataQuarkusConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(TestdataQuarkusEntity.class)
          .join(TestdataQuarkusEntity.class, Joiners.equal(TestdataQuarkusEntity::getValue))
          .filter((a, b) -> a != b)
          .penalize(SimpleScore.ONE)
          .asConstraint("Don't assign 2 entities the same value.")
    };
  }
}
