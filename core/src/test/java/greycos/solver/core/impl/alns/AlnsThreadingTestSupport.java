package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

final class AlnsThreadingTestSupport {

  private AlnsThreadingTestSupport() {}

  static void assertThreadStopped(Thread thread) {
    // Executor termination can precede the final return from its worker's Thread.run().
    // Cancellation tests may already have an interrupt that this wait must preserve.
    boolean interruptedBefore = Thread.interrupted();
    try {
      assertThat(thread.join(Duration.ofSeconds(5)))
          .as("Thread %s terminates", thread.getName())
          .isTrue();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(
          "Interrupted while waiting for thread " + thread.getName() + " to terminate.",
          interrupted);
    } finally {
      if (interruptedBefore) Thread.currentThread().interrupt();
    }
  }
}
