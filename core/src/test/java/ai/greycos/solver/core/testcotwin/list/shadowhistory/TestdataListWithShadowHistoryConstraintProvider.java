package ai.greycos.solver.core.testcotwin.list.shadowhistory;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

public class TestdataListWithShadowHistoryConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      constraintFactory
          .forEach(TestdataListEntityWithShadowHistory.class)
          .penalize(
              SimpleScore.ONE,
              entity -> {
                var size = (long) entity.getValueList().size();
                return size * size;
              })
          .asConstraint("testConstraint")
    };
  }
}
