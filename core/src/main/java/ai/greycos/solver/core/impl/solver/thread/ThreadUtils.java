package ai.greycos.solver.core.impl.solver.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(ThreadUtils.class);

  // Default shutdown timeout in seconds (can be configured)
  private static volatile int defaultShutdownTimeoutSeconds = 60;

  /**
   * Sets the default shutdown timeout for all thread pools.
   *
   * @param timeoutSeconds timeout in seconds (must be positive)
   */
  public static void setDefaultShutdownTimeout(int timeoutSeconds) {
    if (timeoutSeconds <= 0) {
      throw new IllegalArgumentException("Shutdown timeout must be positive");
    }
    defaultShutdownTimeoutSeconds = timeoutSeconds;
  }

  /**
   * Gets the default shutdown timeout in seconds.
   *
   * @return timeout in seconds
   */
  public static int getDefaultShutdownTimeout() {
    return defaultShutdownTimeoutSeconds;
  }

  /**
   * Shuts down an executor service gracefully, waiting for tasks to complete. If the timeout is
   * exceeded, forces shutdown.
   *
   * @param executor the executor service to shut down
   * @param logIndentation indentation for log messages
   * @param name name of the phase/component being shut down
   */
  public static void shutdownAwaitOrKill(
      ExecutorService executor, String logIndentation, String name) {
    shutdownAwaitOrKill(executor, logIndentation, name, defaultShutdownTimeoutSeconds);
  }

  /**
   * Shuts down an executor service gracefully, waiting for tasks to complete. If the timeout is
   * exceeded, forces shutdown.
   *
   * @param executor the executor service to shut down
   * @param logIndentation indentation for log messages
   * @param name name of the phase/component being shut down
   * @param timeoutSeconds timeout in seconds to wait for graceful shutdown
   */
  public static void shutdownAwaitOrKill(
      ExecutorService executor, String logIndentation, String name, int timeoutSeconds) {
    if (executor == null || executor.isShutdown()) {
      return;
    }

    // Intentionally clearing the interrupted flag so that awaitTermination() in step 3 works.
    if (Thread.interrupted()) {
      // 2a. If the current thread is interrupted, propagate interrupt signal to children by
      // initiating an abrupt shutdown.
      executor.shutdownNow();
    } else {
      // 2b. Otherwise, initiate an orderly shutdown of the executor. This allows partition solvers
      // to finish solving upon detecting the termination issued previously (step 1). Shutting down
      // the
      // executor service is important because the JVM cannot exit until all non-daemon threads have
      // terminated.
      executor.shutdown();
    }

    // 3. Finally, wait until the executor finishes shutting down.
    try {
      if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
        // Some solvers refused to complete. Busy threads will be interrupted in the finally block.
        // We're only logging the error instead throwing an exception to prevent eating the original
        // exception.
        LOGGER.warn(
            "{}            {} phase's thread pool did not terminate in the specified time ({} seconds)."
                + " Force cancelling remaining tasks.",
            logIndentation,
            name,
            timeoutSeconds);
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      // Interrupted while waiting for thread pool termination. Busy threads will be interrupted
      // in the finally block.
      Thread.currentThread().interrupt();
      LOGGER.warn(
          "{}            {} phase's thread pool was interrupted. Force cancelling remaining tasks.",
          logIndentation,
          name);
      executor.shutdownNow();
      // If there is an original exception it will be eaten by this.
      throw new IllegalStateException("Thread pool termination was interrupted.", e);
    }
  }

  // ************************************************************************
  // Private constructor
  // ************************************************************************

  private ThreadUtils() {}
}
