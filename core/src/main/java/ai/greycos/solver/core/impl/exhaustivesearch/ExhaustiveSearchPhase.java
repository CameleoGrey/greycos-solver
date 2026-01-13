package ai.greycos.solver.core.impl.exhaustivesearch;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.impl.phase.AbstractPhase;
import ai.greycos.solver.core.impl.phase.Phase;

/**
 * A {@link ExhaustiveSearchPhase} is a {@link Phase} which uses an exhaustive algorithm, such as
 * Brute Force.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @see Phase
 * @see AbstractPhase
 * @see DefaultExhaustiveSearchPhase
 */
public interface ExhaustiveSearchPhase<Solution_> extends Phase<Solution_> {}
