package ai.greycos.solver.core.impl.cotwin.solution;

import java.util.Set;

import ai.greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.constraint.ConstraintRef;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.impl.cotwin.common.CotwinAccessType;
import ai.greycos.solver.core.impl.cotwin.common.accessor.MemberAccessorFactory;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

public sealed interface ConstraintWeightSupplier<Solution_, Score_ extends Score<Score_>>
    permits OverridesBasedConstraintWeightSupplier {

  void initialize(
      SolutionDescriptor<Solution_> solutionDescriptor,
      MemberAccessorFactory memberAccessorFactory,
      CotwinAccessType cotwinAccessType);

  /**
   * Will be called after {@link #initialize(SolutionDescriptor, MemberAccessorFactory,
   * CotwinAccessType)}. Has the option of failing fast in case of discrepancies between the
   * constraints defined in {@link ConstraintProvider} and the constraints defined in the
   * configuration.
   *
   * @param userDefinedConstraints never null
   */
  void validate(Solution_ workingSolution, Set<ConstraintRef> userDefinedConstraints);

  /**
   * The class that carries the constraint weights. It will be {@link ConstraintWeightOverrides}.
   *
   * @return never null
   */
  Class<?> getProblemFactClass();

  String getDefaultConstraintPackage();

  /**
   * Get the weight for the constraint if known to the supplier. Supplies may choose not to provide
   * a value for unknown constraints, which is the case for {@link
   * OverridesBasedConstraintWeightSupplier}.
   *
   * @param constraintRef never null
   * @param workingSolution never null
   * @return may be null, if the provider does not know the constraint
   */
  Score_ getConstraintWeight(ConstraintRef constraintRef, Solution_ workingSolution);
}
