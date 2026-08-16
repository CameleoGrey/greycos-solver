package greycos.solver.core.impl.constructionheuristic;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.impl.phase.AbstractPhase;
import greycos.solver.core.impl.phase.Phase;
import greycos.solver.core.impl.phase.PossiblyInitializingPhase;

/**
 * A {@link ConstructionHeuristicPhase} is a {@link Phase} which uses a construction heuristic
 * algorithm, such as First Fit, First Fit Decreasing, Cheapest Insertion, ...
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @see Phase
 * @see AbstractPhase
 * @see DefaultConstructionHeuristicPhase
 */
public interface ConstructionHeuristicPhase<Solution_>
    extends PossiblyInitializingPhase<Solution_> {}
