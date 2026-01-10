package ai.greycos.solver.core.impl.partitionedsearch.event;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchPhaseScope;
import ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchStepScope;
import ai.greycos.solver.core.impl.solver.event.SolverLifecycleListener;

import org.jspecify.annotations.NullMarked;

/**
 * Listener for partitioned search phase lifecycle events.
 *
 * <p>Extends SolverLifecycleListener with typed access to partitioned search scopes.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
@NullMarked
public interface PartitionedSearchPhaseLifecycleListener<Solution_>
    extends SolverLifecycleListener<Solution_> {

  void phaseStarted(PartitionedSearchPhaseScope<Solution_> phaseScope);

  void stepStarted(PartitionedSearchStepScope<Solution_> stepScope);

  void stepEnded(PartitionedSearchStepScope<Solution_> stepScope);

  void phaseEnded(PartitionedSearchPhaseScope<Solution_> phaseScope);
}
