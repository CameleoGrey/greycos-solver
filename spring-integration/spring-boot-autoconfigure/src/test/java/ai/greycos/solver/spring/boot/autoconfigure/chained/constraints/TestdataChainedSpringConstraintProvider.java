package ai.greycos.solver.spring.boot.autoconfigure.chained.constraints;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.api.score.stream.Joiners;
import ai.greycos.solver.spring.boot.autoconfigure.chained.cotwin.TestdataChainedSpringAnchor;
import ai.greycos.solver.spring.boot.autoconfigure.chained.cotwin.TestdataChainedSpringEntity;

import org.jspecify.annotations.NonNull;

public class TestdataChainedSpringConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(TestdataChainedSpringAnchor.class)
          .ifNotExists(
              TestdataChainedSpringEntity.class,
              Joiners.equal((anchor) -> anchor, TestdataChainedSpringEntity::getPrevious))
          .penalize(SimpleScore.ONE)
          .asConstraint("Assign at least one entity to each anchor.")
    };
  }
}
