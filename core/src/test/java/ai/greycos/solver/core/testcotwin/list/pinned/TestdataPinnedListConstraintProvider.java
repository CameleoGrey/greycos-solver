package ai.greycos.solver.core.testcotwin.list.pinned;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

public final class TestdataPinnedListConstraintProvider implements ConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {onlyConstraint(constraintFactory)};
  }

  private Constraint onlyConstraint(ConstraintFactory constraintFactory) {
    return constraintFactory
        .forEach(TestdataPinnedListEntity.class)
        .penalize(SimpleScore.ONE)
        .asConstraint("First weight");
  }
}
