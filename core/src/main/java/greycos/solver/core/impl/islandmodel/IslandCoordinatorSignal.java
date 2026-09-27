package greycos.solver.core.impl.islandmodel;

/** Wakes one island coordinator without accumulating permits or losing changes before a wait. */
final class IslandCoordinatorSignal {

  private long generation;

  synchronized long generation() {
    return generation;
  }

  synchronized void signal() {
    generation++;
    notifyAll();
  }

  synchronized void awaitChange(long observedGeneration) throws InterruptedException {
    if (Thread.interrupted()) {
      throw new InterruptedException("Interrupted while waiting for island activity.");
    }
    while (generation == observedGeneration) {
      wait();
    }
  }
}
