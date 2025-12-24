package ai.greycos.solver.core.impl.partitionedsearch.event;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchPhaseScope;
import ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchStepScope;
import ai.greycos.solver.core.impl.solver.event.SolverLifecycleListener;

import org.jspecify.annotations.NullMarked;

/**
 * Listener for partitioned search phase lifecycle events.
 *
 * <p>Extends {@link SolverLifecycleListener} with phase-specific methods that provide typed access
 * to partitioned search scopes.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
@NullMarked
public interface PartitionedSearchPhaseLifecycleListener<Solution_>
    extends SolverLifecycleListener<Solution_> {

  /**
   * Called when a partitioned search phase starts.
   *
   * @param phaseScope the phase scope
   */
  void phaseStarted(PartitionedSearchPhaseScope<Solution_> phaseScope);

  /**
   * Called when a partitioned search step starts.
   *
   * @param stepScope the step scope
   */
  void stepStarted(PartitionedSearchStepScope<Solution_> stepScope);

  /**
   * Called when a partitioned search step ends.
   *
   * @param stepScope the step scope
   */
  void stepEnded(PartitionedSearchStepScope<Solution_> stepScope);

  /**
   * Called when a partitioned search phase ends.
   *
   * @param phaseScope the phase scope
   */
  void phaseEnded(PartitionedSearchPhaseScope<Solution_> phaseScope);
}
