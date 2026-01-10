package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;

/**
 * Triggers migration for island model agents during phase execution. Attached to phases running on
 * island agents, decrements step counter and triggers migration when counter reaches zero.
 */
class MigrationTrigger<Solution_> extends PhaseLifecycleListenerAdapter<Solution_> {

  private final IslandAgent<Solution_> agent;

  MigrationTrigger(IslandAgent<Solution_> agent) {
    this.agent = agent;
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    agent.checkAndPerformMigration();
  }
}
