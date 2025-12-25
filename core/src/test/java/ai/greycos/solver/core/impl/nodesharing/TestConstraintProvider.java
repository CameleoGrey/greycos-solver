package ai.greycos.solver.core.impl.nodesharing;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

/** Simple ConstraintProvider with identical lambdas that should be shared. */
public class TestConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint[] defineConstraints(ConstraintFactory factory) {
    return new Constraint[] {
      // Two constraints using identical lambdas
      factory
          .forEach(String.class)
          .filter(s -> s.length() > 0)
          .penalize(SimpleScore.ONE)
          .asConstraint("Non-empty string 1"),
      factory
          .forEach(String.class)
          .filter(s -> s.length() > 0)
          .penalize(SimpleScore.ONE)
          .asConstraint("Non-empty string 2")
    };
  }
}
