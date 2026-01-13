package ai.greycos.solver.core.testcotwin.shadow.dynamic_follower;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintCollectors;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

public class TestdataDynamicFollowerConstraintProvider implements ConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      constraintFactory
          .forEach(TestdataDynamicHasValue.class)
          .groupBy(TestdataDynamicHasValue::getValue, ConstraintCollectors.count())
          .penalize(SimpleScore.ONE, (value, count) -> count * count)
          .asConstraint("Minimize value count")
    };
  }
}
