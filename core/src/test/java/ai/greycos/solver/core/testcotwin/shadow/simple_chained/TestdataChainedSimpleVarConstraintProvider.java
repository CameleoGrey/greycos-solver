package ai.greycos.solver.core.testcotwin.shadow.simple_chained;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

public class TestdataChainedSimpleVarConstraintProvider implements ConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      constraintFactory
          .forEach(TestdataChainedSimpleVarValue.class)
          .filter(entity -> !(entity instanceof TestdataChainedSimpleVarEntity))
          .penalize(
              SimpleScore.ONE,
              value -> value.cumulativeDurationInDays * value.cumulativeDurationInDays)
          .asConstraint("Minimize cumulative duration in days product")
    };
  }
}
