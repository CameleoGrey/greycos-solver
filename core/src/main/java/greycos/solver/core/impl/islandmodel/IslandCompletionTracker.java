package greycos.solver.core.impl.islandmodel;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/** Closes migration channels when every island has finished its search lifecycle. */
final class IslandCompletionTracker extends CountDownLatch {

  private final Runnable onAllCompleted;
  private final AtomicBoolean completionSignaled = new AtomicBoolean();

  IslandCompletionTracker(int islandCount, Runnable onAllCompleted) {
    super(islandCount);
    if (islandCount < 1) {
      throw new IllegalArgumentException("Island count (" + islandCount + ") must be at least 1.");
    }
    this.onAllCompleted = Objects.requireNonNull(onAllCompleted);
  }

  @Override
  public void countDown() {
    super.countDown();
    if (getCount() == 0L && completionSignaled.compareAndSet(false, true)) {
      onAllCompleted.run();
    }
  }
}
