package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.impl.phase.PossiblyInitializingPhase;

import org.jspecify.annotations.NullMarked;

/**
 * An {@link IslandModelPhase} runs multiple independent island agents in parallel, each running the
 * same phases independently. Agents periodically exchange their best solutions through migration in
 * a ring topology.
 *
 * <p>The island model provides enhanced solution quality through migration, near-linear horizontal
 * scaling, and fault tolerance.
 *
 * @param <Solution_> solution type, class with the {@link PlanningSolution} annotation
 * @see DefaultIslandModelPhase
 */
@NullMarked
public interface IslandModelPhase<Solution_> extends PossiblyInitializingPhase<Solution_> {}
