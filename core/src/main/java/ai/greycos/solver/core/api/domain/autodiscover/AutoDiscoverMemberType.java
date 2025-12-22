package ai.greycos.solver.core.api.domain.autodiscover;

import ai.greycos.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningEntityProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.ProblemFactProperty;

/**
 * Determines if and how to automatically presume {@link ConstraintWeightOverrides}, {@link
 * ProblemFactCollectionProperty}, {@link ProblemFactProperty}, {@link
 * PlanningEntityCollectionProperty}, {@link PlanningEntityProperty} and {@link PlanningScore}
 * annotations on {@link PlanningSolution} members based on the member type.
 */
public enum AutoDiscoverMemberType {
  /** Do not reflect. */
  NONE,
  /**
   * Reflect over the fields and automatically behave as the appropriate annotation is there based
   * on the field type.
   */
  FIELD,
  /**
   * Reflect over the getter methods and automatically behave as the appropriate annotation is there
   * based on the return type.
   */
  GETTER;
}
