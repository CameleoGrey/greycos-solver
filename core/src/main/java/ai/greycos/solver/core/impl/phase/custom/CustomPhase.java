package ai.greycos.solver.core.impl.phase.custom;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.solver.phase.PhaseCommand;
import ai.greycos.solver.core.impl.phase.AbstractPhase;
import ai.greycos.solver.core.impl.phase.Phase;
import ai.greycos.solver.core.impl.phase.PossiblyInitializingPhase;

import org.jspecify.annotations.NullMarked;

/**
 * A {@link CustomPhase} is a {@link Phase} which uses {@link PhaseCommand}s.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @see Phase
 * @see AbstractPhase
 * @see DefaultCustomPhase
 */
@NullMarked
public interface CustomPhase<Solution_> extends PossiblyInitializingPhase<Solution_> {}
