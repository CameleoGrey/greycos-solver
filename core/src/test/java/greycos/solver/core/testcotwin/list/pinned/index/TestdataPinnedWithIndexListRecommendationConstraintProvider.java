package greycos.solver.core.testcotwin.list.pinned.index;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;

import org.jspecify.annotations.NonNull;

public final class TestdataPinnedWithIndexListRecommendationConstraintProvider
    implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {penalizeLongerLists(constraintFactory)};
  }

  private Constraint penalizeLongerLists(ConstraintFactory constraintFactory) {
    var assignedValueStream =
        constraintFactory
            .forEach(TestdataPinnedWithIndexListValue.class)
            .filter(value -> value.getEntity() != null);
    return assignedValueStream
        .join(
            assignedValueStream,
            Joiners.equal(
                TestdataPinnedWithIndexListValue::getEntity,
                TestdataPinnedWithIndexListValue::getEntity))
        .penalize(SimpleScore.ONE)
        .asConstraint("Penalize longer lists");
  }
}
