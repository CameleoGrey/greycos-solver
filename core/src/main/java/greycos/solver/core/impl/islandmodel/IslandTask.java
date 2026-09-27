package greycos.solver.core.impl.islandmodel;

import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/** Publishes completion only after the agent future is ready to be read without blocking. */
final class IslandTask extends FutureTask<Void> {

  private final Queue<Future<Void>> completedTasks;
  private final IslandCoordinatorSignal signal;

  IslandTask(Runnable agent, Queue<Future<Void>> completedTasks, IslandCoordinatorSignal signal) {
    super(agent, null);
    this.completedTasks = completedTasks;
    this.signal = signal;
  }

  @Override
  protected void done() {
    completedTasks.add(this);
    signal.signal();
  }
}
