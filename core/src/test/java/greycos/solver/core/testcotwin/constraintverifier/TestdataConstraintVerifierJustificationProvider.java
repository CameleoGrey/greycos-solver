package greycos.solver.core.testcotwin.constraintverifier;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.testcotwin.constraintverifier.justification.TestFirstJustification;

import org.jspecify.annotations.NonNull;

public final class TestdataConstraintVerifierJustificationProvider implements ConstraintProvider {
  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
    return new Constraint[] {
      justifyWithFirstJustification(constraintFactory),
      justifyWithNoJustifications(constraintFactory)
    };
  }

  public Constraint justifyWithFirstJustification(ConstraintFactory constraintFactory) {
    return constraintFactory
        .forEach(TestdataConstraintVerifierFirstEntity.class)
        .penalize(HardSoftScore.ONE_HARD)
        .justifyWith((entity, score) -> new TestFirstJustification(entity.getCode()))
        .asConstraint("Justify with first justification");
  }

  public Constraint justifyWithNoJustifications(ConstraintFactory constraintFactory) {
    return constraintFactory
        .forEach(TestdataConstraintVerifierFirstEntity.class)
        .filter(entity -> entity.getCode().equals("Should not filter"))
        .penalize(HardSoftScore.ONE_HARD)
        .asConstraint("Justify without justifications");
  }
}
