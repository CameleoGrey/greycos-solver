package ai.greycos.solver.core.impl.nodesharing;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

/**
 * ConstraintProvider with lambdas capturing instance state for testing node sharing.
 *
 * <p>Captured lambdas must not be shared as class-level fields because that would leak one
 * instance's state into another.
 */
public class CapturedArgumentProvider implements ConstraintProvider {

  private final int minLength;

  public CapturedArgumentProvider() {
    this(5); // Default value for testing
  }

  public CapturedArgumentProvider(int minLength) {
    this.minLength = minLength;
  }

  @Override
  public Constraint[] defineConstraints(ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(String.class)
          .filter(s -> s.length() > minLength)
          .penalize(SimpleScore.ONE)
          .asConstraint("Min length 1"),
      factory
          .forEach(String.class)
          .filter(s -> s.length() > minLength)
          .penalize(SimpleScore.ONE)
          .asConstraint("Min length 2"),
      // Different captured value - should not be shared
      factory
          .forEach(String.class)
          .filter(s -> s.length() > minLength + 1)
          .penalize(SimpleScore.ONE)
          .asConstraint("Min length plus 1")
    };
  }
}
