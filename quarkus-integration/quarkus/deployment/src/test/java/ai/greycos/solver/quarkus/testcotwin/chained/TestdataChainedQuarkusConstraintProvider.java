package ai.greycos.solver.quarkus.testcotwin.chained;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.api.score.stream.Joiners;

import org.jspecify.annotations.NonNull;

public class TestdataChainedQuarkusConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(TestdataChainedQuarkusAnchor.class)
          .ifNotExists(
              TestdataChainedQuarkusEntity.class,
              Joiners.equal(anchor -> anchor, TestdataChainedQuarkusEntity::getPrevious))
          .penalize(SimpleScore.ONE)
          .asConstraint("Assign at least one entity to each anchor.")
    };
  }
}
