package ai.greycos.solver.core.impl.solver.thread;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Memory monitoring utility for multithreaded solving. This class provides memory usage monitoring,
 * pressure detection, and memory-efficient thread management capabilities.
 *
 * @since 1.0.0
 */
public class MemoryMonitor {

  private static final Logger LOGGER = LoggerFactory.getLogger(MemoryMonitor.class);

  private static final double MEMORY_WARNING_THRESHOLD = 0.8;
  private static final double MEMORY_CRITICAL_THRESHOLD = 0.9;
  private static final double MEMORY_EMERGENCY_THRESHOLD = 0.95;
  private static final long MEMORY_PRESSURE_CHECK_INTERVAL = 5000;

  private final MemoryMXBean memoryBean;
  private final AtomicLong lastPressureCheckTime = new AtomicLong(0);
  private final AtomicLong memoryPressureWarnings = new AtomicLong(0);
  private final AtomicLong memoryPressureCriticals = new AtomicLong(0);
  private final AtomicLong memoryPressureEmergencies = new AtomicLong(0);

  private volatile double warningThreshold = MEMORY_WARNING_THRESHOLD;
  private volatile double criticalThreshold = MEMORY_CRITICAL_THRESHOLD;
  private volatile double emergencyThreshold = MEMORY_EMERGENCY_THRESHOLD;
  private volatile long pressureCheckInterval = MEMORY_PRESSURE_CHECK_INTERVAL;

  public MemoryMonitor() {
    this.memoryBean = ManagementFactory.getMemoryMXBean();
  }

  /**
   * Checks current memory usage and logs warnings if thresholds are exceeded.
   *
   * @return MemoryPressureLevel indicating the current memory pressure level
   */
  public MemoryPressureLevel checkMemoryUsage() {
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastPressureCheckTime.get() < pressureCheckInterval) {
      return MemoryPressureLevel.NORMAL;
    }

    lastPressureCheckTime.set(currentTime);

    MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
    long usedMemory = heapUsage.getUsed();
    long maxMemory = heapUsage.getMax();

    if (maxMemory <= 0) {
      return MemoryPressureLevel.NORMAL;
    }

    double memoryUsagePercentage = (double) usedMemory / maxMemory;
    double memoryUsagePercent = memoryUsagePercentage * 100.0;

    if (memoryUsagePercentage >= emergencyThreshold) {
      memoryPressureEmergencies.incrementAndGet();
      LOGGER.warn(
          "Memory usage is at EMERGENCY level: {}% ({} MB / {} MB)",
          String.format("%.1f", memoryUsagePercent),
          usedMemory / (1024 * 1024),
          maxMemory / (1024 * 1024));
      return MemoryPressureLevel.EMERGENCY;
    } else if (memoryUsagePercentage >= criticalThreshold) {
      memoryPressureCriticals.incrementAndGet();
      LOGGER.warn(
          "Memory usage is at CRITICAL level: {}% ({} MB / {} MB)",
          String.format("%.1f", memoryUsagePercent),
          usedMemory / (1024 * 1024),
          maxMemory / (1024 * 1024));
      return MemoryPressureLevel.CRITICAL;
    } else if (memoryUsagePercentage >= warningThreshold) {
      memoryPressureWarnings.incrementAndGet();
      LOGGER.warn(
          "Memory usage is at WARNING level: {}% ({} MB / {} MB)",
          String.format("%.1f", memoryUsagePercent),
          usedMemory / (1024 * 1024),
          maxMemory / (1024 * 1024));
      return MemoryPressureLevel.WARNING;
    }

    return MemoryPressureLevel.NORMAL;
  }

  public MemoryStatistics forceGarbageCollection() {
    MemoryUsage beforeGC = memoryBean.getHeapMemoryUsage();

    System.gc();
    System.runFinalization();

    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    MemoryUsage afterGC = memoryBean.getHeapMemoryUsage();

    return new MemoryStatistics(
        beforeGC.getUsed(), afterGC.getUsed(), beforeGC.getMax(), System.currentTimeMillis());
  }

  public long estimateMemoryUsage(int threadCount, int bufferSize) {
    long baseMemoryPerThread = 1024 * 1024;
    long bufferMemoryPerThread = bufferSize * 1024;

    return threadCount * (baseMemoryPerThread + bufferMemoryPerThread);
  }

  /**
   * Checks if the system can handle additional threads without memory pressure.
   *
   * @param additionalThreads number of additional threads to check
   * @param bufferSize buffer size per thread
   * @return true if additional threads can be safely added
   */
  public boolean canHandleAdditionalThreads(int additionalThreads, int bufferSize) {
    MemoryPressureLevel currentPressure = checkMemoryUsage();
    if (currentPressure == MemoryPressureLevel.EMERGENCY) {
      return false;
    }

    long estimatedAdditionalMemory = estimateMemoryUsage(additionalThreads, bufferSize);
    long availableMemory = getAvailableMemory();

    return estimatedAdditionalMemory < (availableMemory * 0.8);
  }

  /**
   * Gets the available memory (max - used).
   *
   * @return available memory in bytes
   */
  public long getAvailableMemory() {
    MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
    return heapUsage.getMax() - heapUsage.getUsed();
  }

  public MemoryStatistics getCurrentMemoryStatistics() {
    MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
    return new MemoryStatistics(
        heapUsage.getUsed(),
        heapUsage.getUsed(), // Used before and after are the same for current stats
        heapUsage.getMax(),
        System.currentTimeMillis());
  }

  public MemoryPressureStatistics getMemoryPressureStatistics() {
    return new MemoryPressureStatistics(
        memoryPressureWarnings.get(),
        memoryPressureCriticals.get(),
        memoryPressureEmergencies.get());
  }

  public void resetStatistics() {
    memoryPressureWarnings.set(0);
    memoryPressureCriticals.set(0);
    memoryPressureEmergencies.set(0);
  }

  public void setWarningThreshold(double warningThreshold) {
    if (warningThreshold < 0.0 || warningThreshold > 1.0) {
      throw new IllegalArgumentException("Warning threshold must be between 0.0 and 1.0");
    }
    this.warningThreshold = warningThreshold;
  }

  public void setCriticalThreshold(double criticalThreshold) {
    if (criticalThreshold < 0.0 || criticalThreshold > 1.0) {
      throw new IllegalArgumentException("Critical threshold must be between 0.0 and 1.0");
    }
    this.criticalThreshold = criticalThreshold;
  }

  public void setEmergencyThreshold(double emergencyThreshold) {
    if (emergencyThreshold < 0.0 || emergencyThreshold > 1.0) {
      throw new IllegalArgumentException("Emergency threshold must be between 0.0 and 1.0");
    }
    this.emergencyThreshold = emergencyThreshold;
  }

  public void setPressureCheckInterval(long pressureCheckInterval) {
    if (pressureCheckInterval <= 0) {
      throw new IllegalArgumentException("Pressure check interval must be positive");
    }
    this.pressureCheckInterval = pressureCheckInterval;
  }

  public enum MemoryPressureLevel {
    NORMAL,
    WARNING,
    CRITICAL,
    EMERGENCY
  }

  public static class MemoryStatistics {
    private final long usedMemoryBeforeGC;
    private final long usedMemoryAfterGC;
    private final long maxMemory;
    private final long timestamp;

    public MemoryStatistics(
        long usedMemoryBeforeGC, long usedMemoryAfterGC, long maxMemory, long timestamp) {
      this.usedMemoryBeforeGC = usedMemoryBeforeGC;
      this.usedMemoryAfterGC = usedMemoryAfterGC;
      this.maxMemory = maxMemory;
      this.timestamp = timestamp;
    }

    public long getUsedMemoryBeforeGC() {
      return usedMemoryBeforeGC;
    }

    public long getUsedMemoryAfterGC() {
      return usedMemoryAfterGC;
    }

    public long getMaxMemory() {
      return maxMemory;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public long getMemoryFreedByGC() {
      return usedMemoryBeforeGC - usedMemoryAfterGC;
    }

    public double getMemoryUsagePercentage() {
      return (double) usedMemoryAfterGC / maxMemory;
    }
  }

  public static class MemoryPressureStatistics {
    private final long warningCount;
    private final long criticalCount;
    private final long emergencyCount;

    public MemoryPressureStatistics(long warningCount, long criticalCount, long emergencyCount) {
      this.warningCount = warningCount;
      this.criticalCount = criticalCount;
      this.emergencyCount = emergencyCount;
    }

    public long getWarningCount() {
      return warningCount;
    }

    public long getCriticalCount() {
      return criticalCount;
    }

    public long getEmergencyCount() {
      return emergencyCount;
    }

    public long getTotalPressureEvents() {
      return warningCount + criticalCount + emergencyCount;
    }
  }
}
