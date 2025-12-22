package ai.greycos.solver.quarkus.jackson.it.solver;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.api.score.stream.Joiners;
import ai.greycos.solver.quarkus.jackson.it.domain.ITestdataPlanningEntity;

import org.jspecify.annotations.NonNull;

public class ITestdataPlanningConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(ITestdataPlanningEntity.class)
          .join(ITestdataPlanningEntity.class, Joiners.equal(ITestdataPlanningEntity::getValue))
          .filter((a, b) -> a != b)
          .penalize(SimpleScore.ONE)
          .asConstraint("Don't assign 2 entities the same value.")
    };
  }
}
