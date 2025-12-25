package ai.greycos.solver.core.impl.nodesharing;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

/** ConstraintProvider with a final method. */
public class FinalMethodConstraintProvider implements ConstraintProvider {

  @Override
  public final Constraint[] defineConstraints(ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(String.class)
          .filter(s -> s.length() > 0)
          .penalize(SimpleScore.ONE)
          .asConstraint("Non-empty string")
    };
  }
}
