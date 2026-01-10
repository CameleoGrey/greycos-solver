package ai.greycos.solver.core.impl.solver.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adaptive thread pool manager for multithreaded solving. This class dynamically adjusts the number
 * of threads based on performance metrics, memory pressure, and system load.
 *
 * @since 1.0.0
 */
public class AdaptiveThreadPoolManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(AdaptiveThreadPoolManager.class);

  private static final int MIN_THREAD_COUNT = 1;
  private static final int MAX_THREAD_COUNT = 16;
  private static final long ADJUSTMENT_INTERVAL = 30000;
  private static final double PERFORMANCE_THRESHOLD = 0.8;
  private static final double MEMORY_PRESSURE_THRESHOLD = 0.8;

  private final ThreadFactory threadFactory;
  private final MemoryMonitor memoryMonitor;
  private final PerformanceMetrics performanceMetrics;
  private final AtomicInteger currentThreadCount;
  private final AtomicLong lastAdjustmentTime = new AtomicLong(0);

  private volatile int minThreadCount = MIN_THREAD_COUNT;
  private volatile int maxThreadCount = MAX_THREAD_COUNT;
  private volatile long adjustmentInterval = ADJUSTMENT_INTERVAL;
  private volatile double performanceThreshold = PERFORMANCE_THRESHOLD;
  private volatile double memoryPressureThreshold = MEMORY_PRESSURE_THRESHOLD;

  private volatile ExecutorService executorService;
  private volatile boolean isShutdown = false;

  public AdaptiveThreadPoolManager(
      ThreadFactory threadFactory,
      MemoryMonitor memoryMonitor,
      PerformanceMetrics performanceMetrics,
      int initialThreadCount) {
    this.threadFactory = threadFactory;
    this.memoryMonitor = memoryMonitor;
    this.performanceMetrics = performanceMetrics;
    this.currentThreadCount = new AtomicInteger(initialThreadCount);

    executorService = Executors.newFixedThreadPool(initialThreadCount, threadFactory);
  }

  public void checkAndAdjustThreadPool() {
    if (isShutdown) {
      return;
    }

    long currentTime = System.currentTimeMillis();
    if (currentTime - lastAdjustmentTime.get() < adjustmentInterval) {
      return;
    }

    if (lastAdjustmentTime.compareAndSet(currentTime - adjustmentInterval, currentTime)) {
      performThreadAdjustment();
    }
  }

  private void performThreadAdjustment() {
    try {
      int currentThreads = currentThreadCount.get();
      int targetThreads = determineOptimalThreadCount();

      if (targetThreads != currentThreads) {
        LOGGER.info("Adjusting thread pool from {} to {} threads", currentThreads, targetThreads);

        // Create new thread pool with target size
        ExecutorService newExecutor = Executors.newFixedThreadPool(targetThreads, threadFactory);

        // Shutdown old executor gracefully
        if (executorService != null) {
          executorService.shutdown();
          try {
            if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
              executorService.shutdownNow();
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
          }
        }

        // Replace with new executor
        executorService = newExecutor;
        currentThreadCount.set(targetThreads);

        LOGGER.info("Thread pool adjustment completed: {} threads", targetThreads);
      }
    } catch (Exception e) {
      LOGGER.error("Failed to adjust thread pool", e);
    }
  }

  private int determineOptimalThreadCount() {
    int currentThreads = currentThreadCount.get();
    int targetThreads = currentThreads;

    // Check memory pressure
    MemoryMonitor.MemoryPressureLevel memoryPressure = memoryMonitor.checkMemoryUsage();
    if (memoryPressure == MemoryMonitor.MemoryPressureLevel.EMERGENCY) {
      targetThreads = Math.max(minThreadCount, currentThreads - 2);
      LOGGER.warn("High memory pressure detected, reducing threads to {}", targetThreads);
    } else if (memoryPressure == MemoryMonitor.MemoryPressureLevel.CRITICAL) {
      targetThreads = Math.max(minThreadCount, currentThreads - 1);
      LOGGER.warn("Memory pressure detected, reducing threads to {}", targetThreads);
    }

    // Check performance metrics
    PerformanceMetrics.PerformanceStatistics stats = performanceMetrics.getStatistics();
    double efficiency = stats.getOverallEfficiency();

    if (efficiency < performanceThreshold * 100) {
      // Poor efficiency, reduce threads
      targetThreads = Math.max(minThreadCount, currentThreads - 1);
      LOGGER.debug("Low efficiency ({:.2f}%), reducing threads to {}", efficiency, targetThreads);
    } else if (efficiency > 95 && currentThreads < maxThreadCount) {
      // High efficiency, consider increasing threads
      targetThreads = Math.min(maxThreadCount, currentThreads + 1);
      LOGGER.debug(
          "High efficiency ({:.2f}%), increasing threads to {}", efficiency, targetThreads);
    }

    // Check if system can handle more threads
    if (targetThreads > currentThreads) {
      boolean canHandle =
          memoryMonitor.canHandleAdditionalThreads(
              targetThreads - currentThreads, 50); // Assume 50 buffer size
      if (!canHandle) {
        targetThreads = currentThreads;
        LOGGER.debug("System cannot handle additional threads, keeping at {}", currentThreads);
      }
    }

    return Math.max(minThreadCount, Math.min(maxThreadCount, targetThreads));
  }

  public void submit(Runnable task) {
    if (isShutdown) {
      throw new IllegalStateException("Thread pool manager is shutdown");
    }
    executorService.submit(task);
  }

  public void shutdown() {
    isShutdown = true;
    if (executorService != null) {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        executorService.shutdownNow();
      }
    }
  }

  public int getCurrentThreadCount() {
    return currentThreadCount.get();
  }

  public int getTargetThreadCount() {
    return determineOptimalThreadCount();
  }

  public void forceAdjustment() {
    lastAdjustmentTime.set(System.currentTimeMillis() - adjustmentInterval - 1);
    checkAndAdjustThreadPool();
  }

  public void setMinThreadCount(int minThreadCount) {
    if (minThreadCount < 1) {
      throw new IllegalArgumentException("Minimum thread count must be at least 1");
    }
    this.minThreadCount = minThreadCount;
    if (currentThreadCount.get() < minThreadCount) {
      forceAdjustment();
    }
  }

  public void setMaxThreadCount(int maxThreadCount) {
    if (maxThreadCount < minThreadCount) {
      throw new IllegalArgumentException("Maximum thread count must be greater than minimum");
    }
    this.maxThreadCount = maxThreadCount;
    if (currentThreadCount.get() > maxThreadCount) {
      forceAdjustment();
    }
  }

  public void setAdjustmentInterval(long adjustmentInterval) {
    if (adjustmentInterval <= 0) {
      throw new IllegalArgumentException("Adjustment interval must be positive");
    }
    this.adjustmentInterval = adjustmentInterval;
  }

  public void setPerformanceThreshold(double performanceThreshold) {
    if (performanceThreshold < 0.0 || performanceThreshold > 1.0) {
      throw new IllegalArgumentException("Performance threshold must be between 0.0 and 1.0");
    }
    this.performanceThreshold = performanceThreshold;
  }

  public void setMemoryPressureThreshold(double memoryPressureThreshold) {
    if (memoryPressureThreshold < 0.0 || memoryPressureThreshold > 1.0) {
      throw new IllegalArgumentException("Memory pressure threshold must be between 0.0 and 1.0");
    }
    this.memoryPressureThreshold = memoryPressureThreshold;
  }

  public long getAdjustmentInterval() {
    return adjustmentInterval;
  }

  public static class AdjustmentStatistics {
    private final int currentThreads;
    private final int targetThreads;
    private final MemoryMonitor.MemoryPressureLevel memoryPressure;
    private final double efficiency;
    private final boolean adjustmentNeeded;

    public AdjustmentStatistics(
        int currentThreads,
        int targetThreads,
        MemoryMonitor.MemoryPressureLevel memoryPressure,
        double efficiency,
        boolean adjustmentNeeded) {
      this.currentThreads = currentThreads;
      this.targetThreads = targetThreads;
      this.memoryPressure = memoryPressure;
      this.efficiency = efficiency;
      this.adjustmentNeeded = adjustmentNeeded;
    }

    public int getCurrentThreads() {
      return currentThreads;
    }

    public int getTargetThreads() {
      return targetThreads;
    }

    public MemoryMonitor.MemoryPressureLevel getMemoryPressure() {
      return memoryPressure;
    }

    public double getEfficiency() {
      return efficiency;
    }

    public boolean isAdjustmentNeeded() {
      return adjustmentNeeded;
    }

    @Override
    public String toString() {
      return String.format(
          "AdjustmentStatistics{currentThreads=%d, targetThreads=%d, "
              + "memoryPressure=%s, efficiency=%.2f%%, adjustmentNeeded=%b}",
          currentThreads, targetThreads, memoryPressure, efficiency, adjustmentNeeded);
    }
  }

  public AdjustmentStatistics getAdjustmentStatistics() {
    int currentThreads = currentThreadCount.get();
    int targetThreads = determineOptimalThreadCount();
    MemoryMonitor.MemoryPressureLevel memoryPressure = memoryMonitor.checkMemoryUsage();
    PerformanceMetrics.PerformanceStatistics stats = performanceMetrics.getStatistics();
    boolean adjustmentNeeded = targetThreads != currentThreads;

    return new AdjustmentStatistics(
        currentThreads,
        targetThreads,
        memoryPressure,
        stats.getOverallEfficiency(),
        adjustmentNeeded);
  }
}
