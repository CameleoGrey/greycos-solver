package greycos.solver.core.testcotwin.multivar;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

public class TestdataMultiVarConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      constraintFactory
          .forEachIncludingUnassigned(TestdataMultiVarEntity.class)
          .penalize(
              SimpleScore.ONE,
              entity -> {
                int count = entity.getPrimaryValue() == entity.getSecondaryValue() ? 0 : 1;
                count += entity.getTertiaryValueAllowedUnassigned() == null ? 0 : 1;
                return count;
              })
          .asConstraint("testConstraint")
    };
  }
}
