package ai.greycos.solver.quarkus.testcotwin.cascade;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

public class TestdataQuarkusDuplicateCascadingConstraintProvider implements ConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      constraintFactory
          .forEach(TestdataQuarkusDuplicateCascadingValue.class)
          .filter(value -> value.getChainLength() > 2)
          .penalize(SimpleScore.ONE)
          .asConstraint("length too long"),
      constraintFactory
          .forEach(TestdataQuarkusDuplicateCascadingValue.class)
          .filter(value -> value.getChainProduct() < 4)
          .penalize(SimpleScore.ONE)
          .asConstraint("product too small"),
    };
  }
}
