package ai.greycos.solver.core.impl.nodesharing;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

/** ConstraintProvider with no lambdas (edge case). */
public class NoLambdaConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint[] defineConstraints(ConstraintFactory factory) {
    return new Constraint[] {
      factory.forEach(String.class).penalize(SimpleScore.ONE).asConstraint("All strings")
    };
  }
}
