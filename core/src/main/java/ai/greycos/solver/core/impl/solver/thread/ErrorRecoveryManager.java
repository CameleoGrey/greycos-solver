package ai.greycos.solver.core.impl.solver.thread;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Advanced error recovery manager for multithreaded solving. This class handles various types of
 * errors that can occur in move threads and implements recovery strategies to maintain solver
 * stability and performance.
 *
 * @since 1.0.0
 */
public class ErrorRecoveryManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(ErrorRecoveryManager.class);

  private static final int MAX_ERROR_COUNT = 10;
  private static final long ERROR_RESET_INTERVAL = 60000;
  private static final long RECOVERY_DELAY = 1000;
  private static final int MAX_RECOVERY_ATTEMPTS = 3;

  private final AtomicInteger errorCount = new AtomicInteger(0);
  private final AtomicLong lastErrorTime = new AtomicLong(0);
  private final AtomicLong lastRecoveryTime = new AtomicLong(0);
  private final AtomicInteger recoveryAttempts = new AtomicInteger(0);
  private final AtomicReference<RecoveryState> recoveryState =
      new AtomicReference<>(RecoveryState.NORMAL);

  private volatile int maxErrorCount = MAX_ERROR_COUNT;
  private volatile long errorResetInterval = ERROR_RESET_INTERVAL;
  private volatile long recoveryDelay = RECOVERY_DELAY;
  private volatile int maxRecoveryAttempts = MAX_RECOVERY_ATTEMPTS;

  private volatile ErrorRecoveryListener recoveryListener;

  public ErrorRecoveryManager() {}

  public boolean recordError(Throwable error, int threadIndex) {
    long currentTime = System.currentTimeMillis();
    lastErrorTime.set(currentTime);

    int currentErrorCount = errorCount.incrementAndGet();

    LOGGER.warn(
        "Error in move thread {}: {} - Error count: {}/{}",
        threadIndex,
        error.getMessage(),
        currentErrorCount,
        maxErrorCount);

    // Check if we need to trigger recovery
    if (currentErrorCount >= maxErrorCount) {
      return triggerRecovery(error, threadIndex);
    }

    return false;
  }

  private boolean triggerRecovery(Throwable error, int threadIndex) {
    RecoveryState currentState = recoveryState.get();

    if (currentState == RecoveryState.RECOVERING || currentState == RecoveryState.FAILED) {
      LOGGER.debug("Recovery already in progress, ignoring additional error");
      return false;
    }

    int attempts = recoveryAttempts.incrementAndGet();

    if (attempts > maxRecoveryAttempts) {
      LOGGER.error(
          "Maximum recovery attempts ({}) exceeded, marking as failed", maxRecoveryAttempts);
      recoveryState.set(RecoveryState.FAILED);
      if (recoveryListener != null) {
        recoveryListener.onRecoveryFailed(error, threadIndex);
      }
      return true;
    }

    LOGGER.warn("Triggering recovery attempt {} for error in thread {}", attempts, threadIndex);

    recoveryState.set(RecoveryState.RECOVERING);

    // Schedule recovery after delay
    scheduleRecovery(error, threadIndex);

    return true;
  }

  private void scheduleRecovery(Throwable error, int threadIndex) {
    try {
      Thread.sleep(recoveryDelay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.debug("Recovery interrupted");
      return;
    }

    performRecovery(error, threadIndex);
  }

  private void performRecovery(Throwable error, int threadIndex) {
    try {
      lastRecoveryTime.set(System.currentTimeMillis());

      LOGGER.info("Starting recovery process for thread {}", threadIndex);

      // Notify listener about recovery start
      if (recoveryListener != null) {
        recoveryListener.onRecoveryStarted(error, threadIndex);
      }

      // Reset error count after successful recovery
      errorCount.set(0);
      recoveryState.set(RecoveryState.NORMAL);

      LOGGER.info("Recovery completed successfully for thread {}", threadIndex);

      // Notify listener about recovery completion
      if (recoveryListener != null) {
        recoveryListener.onRecoveryCompleted(threadIndex);
      }

    } catch (Exception recoveryError) {
      LOGGER.error("Recovery process failed for thread {}", threadIndex, recoveryError);
      recoveryState.set(RecoveryState.FAILED);

      if (recoveryListener != null) {
        recoveryListener.onRecoveryFailed(recoveryError, threadIndex);
      }
    }
  }

  public boolean isRecoverable() {
    RecoveryState state = recoveryState.get();
    if (state == RecoveryState.FAILED) {
      return false;
    }

    // Check if enough time has passed since last recovery
    long timeSinceLastRecovery = System.currentTimeMillis() - lastRecoveryTime.get();
    return timeSinceLastRecovery > errorResetInterval;
  }

  public void checkAndResetErrorCount() {
    long timeSinceLastError = System.currentTimeMillis() - lastErrorTime.get();
    if (timeSinceLastError > errorResetInterval) {
      int previousCount = errorCount.getAndSet(0);
      if (previousCount > 0) {
        LOGGER.debug("Resetting error count after {}ms of inactivity", timeSinceLastError);
      }
    }
  }

  public void resetRecoveryState() {
    errorCount.set(0);
    recoveryAttempts.set(0);
    recoveryState.set(RecoveryState.NORMAL);
    lastErrorTime.set(0);
    lastRecoveryTime.set(0);

    LOGGER.info("Recovery state reset");
  }

  public RecoveryStatistics getRecoveryStatistics() {
    return new RecoveryStatistics(
        errorCount.get(),
        recoveryAttempts.get(),
        recoveryState.get(),
        lastErrorTime.get(),
        lastRecoveryTime.get(),
        System.currentTimeMillis());
  }

  public void setMaxErrorCount(int maxErrorCount) {
    if (maxErrorCount < 1) {
      throw new IllegalArgumentException("Max error count must be positive");
    }
    this.maxErrorCount = maxErrorCount;
  }

  public void setErrorResetInterval(long errorResetInterval) {
    if (errorResetInterval <= 0) {
      throw new IllegalArgumentException("Error reset interval must be positive");
    }
    this.errorResetInterval = errorResetInterval;
  }

  public void setRecoveryDelay(long recoveryDelay) {
    if (recoveryDelay < 0) {
      throw new IllegalArgumentException("Recovery delay cannot be negative");
    }
    this.recoveryDelay = recoveryDelay;
  }

  public void setMaxRecoveryAttempts(int maxRecoveryAttempts) {
    if (maxRecoveryAttempts < 0) {
      throw new IllegalArgumentException("Max recovery attempts cannot be negative");
    }
    this.maxRecoveryAttempts = maxRecoveryAttempts;
  }

  public void setRecoveryListener(ErrorRecoveryListener listener) {
    this.recoveryListener = listener;
  }

  public int getMaxErrorCount() {
    return maxErrorCount;
  }

  public long getRecoveryDelay() {
    return recoveryDelay;
  }

  public enum RecoveryState {
    NORMAL,
    RECOVERING,
    FAILED
  }

  public static class RecoveryStatistics {
    private final int errorCount;
    private final int recoveryAttempts;
    private final RecoveryState recoveryState;
    private final long lastErrorTime;
    private final long lastRecoveryTime;
    private final long currentTime;

    public RecoveryStatistics(
        int errorCount,
        int recoveryAttempts,
        RecoveryState recoveryState,
        long lastErrorTime,
        long lastRecoveryTime,
        long currentTime) {
      this.errorCount = errorCount;
      this.recoveryAttempts = recoveryAttempts;
      this.recoveryState = recoveryState;
      this.lastErrorTime = lastErrorTime;
      this.lastRecoveryTime = lastRecoveryTime;
      this.currentTime = currentTime;
    }

    public int getErrorCount() {
      return errorCount;
    }

    public int getRecoveryAttempts() {
      return recoveryAttempts;
    }

    public RecoveryState getRecoveryState() {
      return recoveryState;
    }

    public long getLastErrorTime() {
      return lastErrorTime;
    }

    public long getLastRecoveryTime() {
      return lastRecoveryTime;
    }

    public long getCurrentTime() {
      return currentTime;
    }

    public long getTimeSinceLastError() {
      return currentTime - lastErrorTime;
    }

    public long getTimeSinceLastRecovery() {
      return currentTime - lastRecoveryTime;
    }

    @Override
    public String toString() {
      return String.format(
          "RecoveryStatistics{errorCount=%d, recoveryAttempts=%d, state=%s, "
              + "timeSinceLastError=%dms, timeSinceLastRecovery=%dms}",
          errorCount,
          recoveryAttempts,
          recoveryState,
          getTimeSinceLastError(),
          getTimeSinceLastRecovery());
    }
  }

  public interface ErrorRecoveryListener {
    void onRecoveryStarted(Throwable error, int threadIndex);

    void onRecoveryCompleted(int threadIndex);

    void onRecoveryFailed(Throwable error, int threadIndex);
  }
}
