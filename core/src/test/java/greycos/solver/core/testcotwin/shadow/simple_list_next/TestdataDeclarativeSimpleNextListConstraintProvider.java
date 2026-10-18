package greycos.solver.core.testcotwin.shadow.simple_list_next;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

public class TestdataDeclarativeSimpleNextListConstraintProvider implements ConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      constraintFactory
          .forEach(TestdataDeclarativeSimpleNextListValue.class)
          .penalize(
              SimpleScore.ONE,
              value ->
                  value.entity == null
                      ? 0
                      : value.duration * (value.entity.startTime - value.endTime))
          .asConstraint("Finish long values late")
    };
  }
}
