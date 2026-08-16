package greycos.solver.spring.boot.autoconfigure.missingsuppliervariable.constraints;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.spring.boot.autoconfigure.missingsuppliervariable.cotwin.TestdataSpringMissingSupplierVariableEntity;

import org.jspecify.annotations.NonNull;

public class TestdataSpringMissingSupplierVariableConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(TestdataSpringMissingSupplierVariableEntity.class)
          .join(
              TestdataSpringMissingSupplierVariableEntity.class,
              Joiners.equal(
                  TestdataSpringMissingSupplierVariableEntity::getValue1,
                  TestdataSpringMissingSupplierVariableEntity::getValue2))
          .filter(
              (a, b) -> {
                if (a.getValue1AndValue2() == null || b.getValue1AndValue2() == null) {
                  throw new IllegalStateException();
                }
                return a.getValue1AndValue2().equals(b.getValue1AndValue2());
              })
          .penalize(SimpleScore.ONE)
          .asConstraint("Don't assign 2 entities the same value.")
    };
  }
}
