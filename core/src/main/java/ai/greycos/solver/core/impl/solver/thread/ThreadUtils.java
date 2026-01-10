package ai.greycos.solver.core.impl.solver.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility class for managing thread pool shutdown in multithreaded solving. */
public class ThreadUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(ThreadUtils.class);

  private static volatile int defaultShutdownTimeoutSeconds = 60;

  public static void setDefaultShutdownTimeout(int timeoutSeconds) {
    if (timeoutSeconds <= 0) {
      throw new IllegalArgumentException("Shutdown timeout must be positive");
    }
    defaultShutdownTimeoutSeconds = timeoutSeconds;
  }

  public static int getDefaultShutdownTimeout() {
    return defaultShutdownTimeoutSeconds;
  }

  public static void shutdownAwaitOrKill(
      ExecutorService executor, String logIndentation, String name) {
    shutdownAwaitOrKill(executor, logIndentation, name, defaultShutdownTimeoutSeconds);
  }

  public static void shutdownAwaitOrKill(
      ExecutorService executor, String logIndentation, String name, int timeoutSeconds) {
    if (executor == null || executor.isShutdown()) {
      return;
    }

    if (Thread.interrupted()) {
      executor.shutdownNow();
    } else {
      executor.shutdown();
    }

    try {
      if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
        LOGGER.warn(
            "{}            {} phase's thread pool did not terminate in the specified time ({} seconds)."
                + " Force cancelling remaining tasks.",
            logIndentation,
            name,
            timeoutSeconds);
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.warn(
          "{}            {} phase's thread pool was interrupted. Force cancelling remaining tasks.",
          logIndentation,
          name);
      executor.shutdownNow();
      throw new IllegalStateException("Thread pool termination was interrupted.", e);
    }
  }

  private ThreadUtils() {}
}
