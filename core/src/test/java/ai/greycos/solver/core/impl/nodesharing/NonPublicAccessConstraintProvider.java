package ai.greycos.solver.core.impl.nodesharing;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

/** ConstraintProvider that references a package-private helper type. */
public class NonPublicAccessConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint[] defineConstraints(ConstraintFactory factory) {
    return new Constraint[] {
      factory
          .forEach(String.class)
          .filter(PackagePrivateAccessHelper::accept)
          .penalize(SimpleScore.ONE)
          .asConstraint("nonPublicAccess")
    };
  }
}
