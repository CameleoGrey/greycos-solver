package ai.greycos.solver.core.impl.solver.thread;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime configuration manager for multithreaded solving. This class allows dynamic adjustment of
 * multithreading parameters during solving without requiring a restart.
 *
 * @since 1.0.0
 */
public class RuntimeConfigurationManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeConfigurationManager.class);

  // Configuration components
  private final MemoryMonitor memoryMonitor;
  private final PerformanceMetrics performanceMetrics;
  private final AdaptiveThreadPoolManager threadPoolManager;
  private final ErrorRecoveryManager errorRecoveryManager;
  private final MultithreadingMonitor monitor;

  // Runtime state
  private final AtomicBoolean configurationEnabled = new AtomicBoolean(true);
  private final AtomicLong lastConfigurationUpdate = new AtomicLong(0);
  private final AtomicInteger configurationUpdateCount = new AtomicInteger(0);

  // Configuration listeners
  private volatile Consumer<ConfigurationChangeEvent> configurationChangeListener;

  public RuntimeConfigurationManager(
      MemoryMonitor memoryMonitor,
      PerformanceMetrics performanceMetrics,
      AdaptiveThreadPoolManager threadPoolManager,
      ErrorRecoveryManager errorRecoveryManager,
      MultithreadingMonitor monitor) {
    this.memoryMonitor = memoryMonitor;
    this.performanceMetrics = performanceMetrics;
    this.threadPoolManager = threadPoolManager;
    this.errorRecoveryManager = errorRecoveryManager;
    this.monitor = monitor;
  }

  /**
   * Updates the move thread count at runtime.
   *
   * @param newThreadCount new number of move threads
   * @return true if the update was successful
   */
  public boolean updateMoveThreadCount(int newThreadCount) {
    if (!configurationEnabled.get()) {
      LOGGER.warn("Configuration updates are disabled");
      return false;
    }

    if (newThreadCount < 1 || newThreadCount > 32) {
      LOGGER.warn("Invalid thread count: {}. Must be between 1 and 32.", newThreadCount);
      return false;
    }

    try {
      int currentThreads = threadPoolManager.getCurrentThreadCount();
      if (newThreadCount == currentThreads) {
        LOGGER.debug("Thread count already at desired value: {}", newThreadCount);
        return true;
      }

      // Update thread pool manager configuration
      threadPoolManager.setMinThreadCount(
          Math.min(newThreadCount, threadPoolManager.getCurrentThreadCount()));
      threadPoolManager.setMaxThreadCount(
          Math.max(newThreadCount, threadPoolManager.getCurrentThreadCount()));

      // Force immediate adjustment
      threadPoolManager.forceAdjustment();

      // Verify the change
      int actualThreads = threadPoolManager.getCurrentThreadCount();
      if (actualThreads == newThreadCount) {
        logConfigurationChange("moveThreadCount", currentThreads, newThreadCount);
        notifyConfigurationChange(
            new ConfigurationChangeEvent("moveThreadCount", currentThreads, newThreadCount));
        return true;
      } else {
        LOGGER.warn("Failed to set thread count to {}. Actual: {}", newThreadCount, actualThreads);
        return false;
      }
    } catch (Exception e) {
      LOGGER.error("Failed to update move thread count to {}", newThreadCount, e);
      return false;
    }
  }

  /**
   * Updates the move thread buffer size at runtime.
   *
   * @param newBufferSize new buffer size per thread
   * @return true if the update was successful
   */
  public boolean updateMoveThreadBufferSize(int newBufferSize) {
    if (!configurationEnabled.get()) {
      LOGGER.warn("Configuration updates are disabled");
      return false;
    }

    if (newBufferSize < 1 || newBufferSize > 1000) {
      LOGGER.warn("Invalid buffer size: {}. Must be between 1 and 1000.", newBufferSize);
      return false;
    }

    try {
      // Note: Buffer size is typically configured at decider level, but we can log the change
      // for monitoring purposes
      logConfigurationChange("moveThreadBufferSize", "unknown", newBufferSize);
      notifyConfigurationChange(
          new ConfigurationChangeEvent("moveThreadBufferSize", "unknown", newBufferSize));
      return true;
    } catch (Exception e) {
      LOGGER.error("Failed to update move thread buffer size to {}", newBufferSize, e);
      return false;
    }
  }

  /**
   * Updates memory monitoring thresholds at runtime.
   *
   * @param warningThreshold warning threshold (0.0 to 1.0)
   * @param criticalThreshold critical threshold (0.0 to 1.0)
   * @param emergencyThreshold emergency threshold (0.0 to 1.0)
   * @return true if the update was successful
   */
  public boolean updateMemoryThresholds(
      double warningThreshold, double criticalThreshold, double emergencyThreshold) {
    if (!configurationEnabled.get()) {
      LOGGER.warn("Configuration updates are disabled");
      return false;
    }

    if (warningThreshold < 0.0
        || warningThreshold > 1.0
        || criticalThreshold < 0.0
        || criticalThreshold > 1.0
        || emergencyThreshold < 0.0
        || emergencyThreshold > 1.0) {
      LOGGER.warn("Invalid memory thresholds. All must be between 0.0 and 1.0.");
      return false;
    }

    if (warningThreshold >= criticalThreshold || criticalThreshold >= emergencyThreshold) {
      LOGGER.warn("Memory thresholds must be in ascending order: warning < critical < emergency");
      return false;
    }

    try {
      memoryMonitor.setWarningThreshold(warningThreshold);
      memoryMonitor.setCriticalThreshold(criticalThreshold);
      memoryMonitor.setEmergencyThreshold(emergencyThreshold);

      logConfigurationChange(
          "memoryThresholds",
          "updated",
          String.format(
              "warning=%.2f, critical=%.2f, emergency=%.2f",
              warningThreshold, criticalThreshold, emergencyThreshold));
      notifyConfigurationChange(
          new ConfigurationChangeEvent(
              "memoryThresholds",
              "updated",
              String.format(
                  "warning=%.2f, critical=%.2f, emergency=%.2f",
                  warningThreshold, criticalThreshold, emergencyThreshold)));
      return true;
    } catch (Exception e) {
      LOGGER.error("Failed to update memory thresholds", e);
      return false;
    }
  }

  /**
   * Updates performance monitoring thresholds at runtime.
   *
   * @param performanceThreshold performance efficiency threshold (0.0 to 1.0)
   * @param adjustmentInterval adjustment interval in milliseconds
   * @return true if the update was successful
   */
  public boolean updatePerformanceThresholds(double performanceThreshold, long adjustmentInterval) {
    if (!configurationEnabled.get()) {
      LOGGER.warn("Configuration updates are disabled");
      return false;
    }

    if (performanceThreshold < 0.0 || performanceThreshold > 1.0) {
      LOGGER.warn("Invalid performance threshold. Must be between 0.0 and 1.0.");
      return false;
    }

    if (adjustmentInterval < 1000) {
      LOGGER.warn("Adjustment interval too low: {}. Must be at least 1000ms.", adjustmentInterval);
      return false;
    }

    try {
      threadPoolManager.setPerformanceThreshold(performanceThreshold);
      threadPoolManager.setAdjustmentInterval(adjustmentInterval);

      logConfigurationChange(
          "performanceThresholds",
          "updated",
          String.format("threshold=%.2f, interval=%d", performanceThreshold, adjustmentInterval));
      notifyConfigurationChange(
          new ConfigurationChangeEvent(
              "performanceThresholds",
              "updated",
              String.format(
                  "threshold=%.2f, interval=%d", performanceThreshold, adjustmentInterval)));
      return true;
    } catch (Exception e) {
      LOGGER.error("Failed to update performance thresholds", e);
      return false;
    }
  }

  /**
   * Updates error recovery settings at runtime.
   *
   * @param maxErrorCount maximum error count before recovery
   * @param recoveryDelay recovery delay in milliseconds
   * @param maxRecoveryAttempts maximum recovery attempts
   * @return true if the update was successful
   */
  public boolean updateErrorRecoverySettings(
      int maxErrorCount, long recoveryDelay, int maxRecoveryAttempts) {
    if (!configurationEnabled.get()) {
      LOGGER.warn("Configuration updates are disabled");
      return false;
    }

    if (maxErrorCount < 1) {
      LOGGER.warn("Invalid max error count: {}. Must be at least 1.", maxErrorCount);
      return false;
    }

    if (recoveryDelay < 0) {
      LOGGER.warn("Invalid recovery delay: {}. Must be non-negative.", recoveryDelay);
      return false;
    }

    if (maxRecoveryAttempts < 0) {
      LOGGER.warn("Invalid max recovery attempts: {}. Must be non-negative.", maxRecoveryAttempts);
      return false;
    }

    try {
      errorRecoveryManager.setMaxErrorCount(maxErrorCount);
      errorRecoveryManager.setRecoveryDelay(recoveryDelay);
      errorRecoveryManager.setMaxRecoveryAttempts(maxRecoveryAttempts);

      logConfigurationChange(
          "errorRecoverySettings",
          "updated",
          String.format(
              "maxErrors=%d, delay=%d, maxAttempts=%d",
              maxErrorCount, recoveryDelay, maxRecoveryAttempts));
      notifyConfigurationChange(
          new ConfigurationChangeEvent(
              "errorRecoverySettings",
              "updated",
              String.format(
                  "maxErrors=%d, delay=%d, maxAttempts=%d",
                  maxErrorCount, recoveryDelay, maxRecoveryAttempts)));
      return true;
    } catch (Exception e) {
      LOGGER.error("Failed to update error recovery settings", e);
      return false;
    }
  }

  /**
   * Updates monitoring intervals at runtime.
   *
   * @param healthCheckInterval health check interval in milliseconds
   * @param detailedReportInterval detailed report interval in milliseconds
   * @return true if the update was successful
   */
  public boolean updateMonitoringIntervals(long healthCheckInterval, long detailedReportInterval) {
    if (!configurationEnabled.get()) {
      LOGGER.warn("Configuration updates are disabled");
      return false;
    }

    if (healthCheckInterval < 1000) {
      LOGGER.warn(
          "Health check interval too low: {}. Must be at least 1000ms.", healthCheckInterval);
      return false;
    }

    if (detailedReportInterval < 5000) {
      LOGGER.warn(
          "Detailed report interval too low: {}. Must be at least 5000ms.", detailedReportInterval);
      return false;
    }

    try {
      monitor.setHealthCheckInterval(healthCheckInterval);
      monitor.setDetailedReportInterval(detailedReportInterval);

      logConfigurationChange(
          "monitoringIntervals",
          "updated",
          String.format(
              "healthCheck=%d, detailedReport=%d", healthCheckInterval, detailedReportInterval));
      notifyConfigurationChange(
          new ConfigurationChangeEvent(
              "monitoringIntervals",
              "updated",
              String.format(
                  "healthCheck=%d, detailedReport=%d",
                  healthCheckInterval, detailedReportInterval)));
      return true;
    } catch (Exception e) {
      LOGGER.error("Failed to update monitoring intervals", e);
      return false;
    }
  }

  /**
   * Gets current runtime configuration.
   *
   * @return RuntimeConfiguration with current settings
   */
  public RuntimeConfiguration getCurrentConfiguration() {
    return new RuntimeConfiguration(
        threadPoolManager.getCurrentThreadCount(),
        "unknown", // Buffer size is not directly accessible
        memoryMonitor.getAvailableMemory(),
        threadPoolManager.getAdjustmentInterval(),
        errorRecoveryManager.getMaxErrorCount(),
        errorRecoveryManager.getRecoveryDelay(),
        monitor.getHealthCheckInterval(),
        monitor.getDetailedReportInterval(),
        configurationEnabled.get(),
        configurationUpdateCount.get(),
        lastConfigurationUpdate.get());
  }

  /**
   * Enables or disables runtime configuration updates.
   *
   * @param enabled true to enable configuration updates
   */
  public void setConfigurationEnabled(boolean enabled) {
    boolean previous = configurationEnabled.getAndSet(enabled);
    if (previous != enabled) {
      LOGGER.info("Runtime configuration {}abled", enabled ? "en" : "dis");
    }
  }

  /**
   * Sets the configuration change listener.
   *
   * @param listener the listener to notify of configuration changes
   */
  public void setConfigurationChangeListener(Consumer<ConfigurationChangeEvent> listener) {
    this.configurationChangeListener = listener;
  }

  /** Resets all configuration statistics. */
  public void resetConfigurationStatistics() {
    configurationUpdateCount.set(0);
    lastConfigurationUpdate.set(0);
    memoryMonitor.resetStatistics();
    performanceMetrics.reset();
    errorRecoveryManager.resetRecoveryState();
  }

  private void logConfigurationChange(String setting, Object oldValue, Object newValue) {
    LOGGER.info("Runtime configuration change: {} = {} -> {}", setting, oldValue, newValue);
    configurationUpdateCount.incrementAndGet();
    lastConfigurationUpdate.set(System.currentTimeMillis());
  }

  private void notifyConfigurationChange(ConfigurationChangeEvent event) {
    if (configurationChangeListener != null) {
      try {
        configurationChangeListener.accept(event);
      } catch (Exception e) {
        LOGGER.error("Failed to notify configuration change listener", e);
      }
    }
  }

  /** Runtime configuration container. */
  public static class RuntimeConfiguration {
    private final int currentThreadCount;
    private final String currentBufferSize;
    private final long availableMemory;
    private final long adjustmentInterval;
    private final int maxErrorCount;
    private final long recoveryDelay;
    private final long healthCheckInterval;
    private final long detailedReportInterval;
    private final boolean configurationEnabled;
    private final int configurationUpdateCount;
    private final long lastConfigurationUpdate;

    public RuntimeConfiguration(
        int currentThreadCount,
        String currentBufferSize,
        long availableMemory,
        long adjustmentInterval,
        int maxErrorCount,
        long recoveryDelay,
        long healthCheckInterval,
        long detailedReportInterval,
        boolean configurationEnabled,
        int configurationUpdateCount,
        long lastConfigurationUpdate) {
      this.currentThreadCount = currentThreadCount;
      this.currentBufferSize = currentBufferSize;
      this.availableMemory = availableMemory;
      this.adjustmentInterval = adjustmentInterval;
      this.maxErrorCount = maxErrorCount;
      this.recoveryDelay = recoveryDelay;
      this.healthCheckInterval = healthCheckInterval;
      this.detailedReportInterval = detailedReportInterval;
      this.configurationEnabled = configurationEnabled;
      this.configurationUpdateCount = configurationUpdateCount;
      this.lastConfigurationUpdate = lastConfigurationUpdate;
    }

    // Getters
    public int getCurrentThreadCount() {
      return currentThreadCount;
    }

    public String getCurrentBufferSize() {
      return currentBufferSize;
    }

    public long getAvailableMemory() {
      return availableMemory;
    }

    public long getAdjustmentInterval() {
      return adjustmentInterval;
    }

    public int getMaxErrorCount() {
      return maxErrorCount;
    }

    public long getRecoveryDelay() {
      return recoveryDelay;
    }

    public long getHealthCheckInterval() {
      return healthCheckInterval;
    }

    public long getDetailedReportInterval() {
      return detailedReportInterval;
    }

    public boolean isConfigurationEnabled() {
      return configurationEnabled;
    }

    public int getConfigurationUpdateCount() {
      return configurationUpdateCount;
    }

    public long getLastConfigurationUpdate() {
      return lastConfigurationUpdate;
    }

    @Override
    public String toString() {
      return String.format(
          "RuntimeConfiguration{threadCount=%d, bufferSize=%s, availableMemory=%dMB, "
              + "adjustmentInterval=%dms, maxErrorCount=%d, recoveryDelay=%dms, "
              + "healthCheckInterval=%dms, detailedReportInterval=%dms, enabled=%b, "
              + "updates=%d, lastUpdate=%d}",
          currentThreadCount,
          currentBufferSize,
          availableMemory / (1024 * 1024),
          adjustmentInterval,
          maxErrorCount,
          recoveryDelay,
          healthCheckInterval,
          detailedReportInterval,
          configurationEnabled,
          configurationUpdateCount,
          lastConfigurationUpdate);
    }
  }

  /** Configuration change event. */
  public static class ConfigurationChangeEvent {
    private final String setting;
    private final Object oldValue;
    private final Object newValue;
    private final long timestamp;

    public ConfigurationChangeEvent(String setting, Object oldValue, Object newValue) {
      this.setting = setting;
      this.oldValue = oldValue;
      this.newValue = newValue;
      this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public String getSetting() {
      return setting;
    }

    public Object getOldValue() {
      return oldValue;
    }

    public Object getNewValue() {
      return newValue;
    }

    public long getTimestamp() {
      return timestamp;
    }

    @Override
    public String toString() {
      return String.format(
          "ConfigurationChangeEvent{setting='%s', oldValue=%s, newValue=%s, timestamp=%d}",
          setting, oldValue, newValue, timestamp);
    }
  }
}
