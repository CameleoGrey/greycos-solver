package ai.greycos.solver.core.impl.nodesharing;

import java.util.Set;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

/**
 * ConstraintProvider with an existing class initializer.
 *
 * <p>The transformer must preserve the original static initialization and still initialize shared
 * lambda fields.
 */
public class StaticInitializerConstraintProvider implements ConstraintProvider {

  public static final Set<String> ALLOWED_VALUES;
  public static final String INIT_MARKER;

  static {
    ALLOWED_VALUES = Set.of("A1");
    INIT_MARKER = String.join("-", "static", "init");
  }

  @Override
  public Constraint[] defineConstraints(ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(String.class)
          .filter(s -> ALLOWED_VALUES.contains(s))
          .penalize(SimpleScore.ONE)
          .asConstraint("Long enough 1"),
      factory
          .forEach(String.class)
          .filter(s -> ALLOWED_VALUES.contains(s))
          .penalize(SimpleScore.ONE)
          .asConstraint("Long enough 2")
    };
  }
}
