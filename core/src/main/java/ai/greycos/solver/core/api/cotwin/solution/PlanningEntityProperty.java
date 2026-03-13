package ai.greycos.solver.core.api.cotwin.solution;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;

/**
 * Specifies that a property (or a field) on a {@link PlanningSolution} class is a planning entity.
 *
 * <p>The planning entity should have the {@link PlanningEntity} annotation. The planning entity
 * will be registered with the solver.
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface PlanningEntityProperty {}
