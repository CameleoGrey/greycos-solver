package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;

/**
 * Triggers migration for island model agents during phase execution.
 *
 * <p>This listener is attached to phases running on island agents. After each step, it decrements
 * the step counter and triggers migration when the counter reaches zero.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
class MigrationTrigger<Solution_> extends PhaseLifecycleListenerAdapter<Solution_> {

  private final IslandAgent<Solution_> agent;

  /**
   * Creates a new migration trigger.
   *
   * @param agent the island agent that owns this trigger
   */
  MigrationTrigger(IslandAgent<Solution_> agent) {
    this.agent = agent;
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    // Check if migration should occur after this step
    agent.checkAndPerformMigration();
  }
}
