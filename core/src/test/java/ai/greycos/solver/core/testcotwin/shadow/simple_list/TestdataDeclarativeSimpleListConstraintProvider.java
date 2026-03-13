package ai.greycos.solver.core.testcotwin.shadow.simple_list;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

public class TestdataDeclarativeSimpleListConstraintProvider implements ConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      constraintFactory
          .forEach(TestdataDeclarativeSimpleListValue.class)
          .penalize(SimpleScore.ONE, value -> value.endTime)
          .asConstraint("Minimize end time")
    };
  }
}
