package ai.greycos.solver.core.impl.nodesharing;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

/**
 * Simple ConstraintProvider with 2 identical lambdas for testing node sharing. Minimal case to
 * verify basic lambda deduplication works.
 */
public class SimpleConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint[] defineConstraints(ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(String.class)
          .filter(s -> s.length() > 0)
          .penalize(SimpleScore.ONE)
          .asConstraint("Non-empty 1"),
      factory
          .forEach(String.class)
          .filter(s -> s.length() > 0)
          .penalize(SimpleScore.ONE)
          .asConstraint("Non-empty 2")
    };
  }
}
