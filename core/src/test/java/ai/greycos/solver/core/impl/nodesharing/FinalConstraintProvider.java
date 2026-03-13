package ai.greycos.solver.core.impl.nodesharing;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

/** ConstraintProvider that should fail validation (final class). */
public final class FinalConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint[] defineConstraints(ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(String.class)
          .filter(s -> s.length() > 0)
          .penalize(SimpleScore.ONE)
          .asConstraint("Non-empty string")
    };
  }
}
