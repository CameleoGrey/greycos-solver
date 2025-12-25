package ai.greycos.solver.core.impl.nodesharing;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

/** Test with a more complex ConstraintProvider. */
public class ComplexConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint[] defineConstraints(ConstraintFactory factory) {
    return new Constraint[] {
      // Multiple identical filters
      factory
          .forEach(String.class)
          .filter(s -> s.length() > 0)
          .penalize(SimpleScore.ONE)
          .asConstraint("Non-empty 1"),
      factory
          .forEach(String.class)
          .filter(s -> s.length() > 0)
          .penalize(SimpleScore.ONE)
          .asConstraint("Non-empty 2"),
      factory
          .forEach(String.class)
          .filter(s -> s.length() > 0)
          .penalize(SimpleScore.ONE)
          .asConstraint("Non-empty 3"),
      // Different filter (should not be shared)
      factory
          .forEach(String.class)
          .filter(s -> s.length() > 5)
          .penalize(SimpleScore.ONE)
          .asConstraint("Long string")
    };
  }
}
