package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.impl.phase.PossiblyInitializingPhase;

import org.jspecify.annotations.NullMarked;

/**
 * Island model phase that runs multiple independent island agents in parallel. Agents exchange best
 * solutions through migration in a ring topology for enhanced solution quality.
 */
@NullMarked
public interface IslandModelPhase<Solution_> extends PossiblyInitializingPhase<Solution_> {}
