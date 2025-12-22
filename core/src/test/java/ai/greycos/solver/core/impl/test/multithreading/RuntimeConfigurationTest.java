package ai.greycos.solver.core.impl.test.multithreading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import ai.greycos.solver.core.impl.solver.thread.AdaptiveThreadPoolManager;
import ai.greycos.solver.core.impl.solver.thread.ErrorRecoveryManager;
import ai.greycos.solver.core.impl.solver.thread.MemoryMonitor;
import ai.greycos.solver.core.impl.solver.thread.MultithreadingMonitor;
import ai.greycos.solver.core.impl.solver.thread.PerformanceMetrics;
import ai.greycos.solver.core.impl.solver.thread.RuntimeConfigurationManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runtime configuration tests for multithreading functionality. These tests validate the ability to
 * dynamically adjust multithreading parameters during solving without requiring a restart.
 */
public class RuntimeConfigurationTest {

  private MemoryMonitor memoryMonitor;
  private PerformanceMetrics performanceMetrics;
  private AdaptiveThreadPoolManager threadPoolManager;
  private ErrorRecoveryManager errorRecoveryManager;
  private MultithreadingMonitor monitor;
  private RuntimeConfigurationManager configManager;

  @BeforeEach
  void setUp() {
    memoryMonitor = new MemoryMonitor();
    performanceMetrics = new PerformanceMetrics(4);
    errorRecoveryManager = new ErrorRecoveryManager();

    // Create a simple thread factory for testing
    java.util.concurrent.ThreadFactory threadFactory =
        r -> new Thread(r, "TestThread-" + System.currentTimeMillis());

    threadPoolManager = new AdaptiveThreadPoolManager(threadFactory, memoryMonitor, performanceMetrics, 2);
    monitor = new MultithreadingMonitor(memoryMonitor, performanceMetrics, threadPoolManager, errorRecoveryManager);
    configManager = new RuntimeConfigurationManager(memoryMonitor, performanceMetrics, threadPoolManager, errorRecoveryManager, monitor);
  }

  @Test
  void testUpdateMoveThreadCount() {
    // Test valid thread count updates
    assertThat(configManager.updateMoveThreadCount(1)).isTrue();
    assertThat(threadPoolManager.getCurrentThreadCount()).isEqualTo(1);

    assertThat(configManager.updateMoveThreadCount(4)).isTrue();
    assertThat(threadPoolManager.getCurrentThreadCount()).isEqualTo(4);

    assertThat(configManager.updateMoveThreadCount(2)).isTrue();
    assertThat(threadPoolManager.getCurrentThreadCount()).isEqualTo(2);
  }

  @Test
  void testUpdateMoveThreadCountInvalidValues() {
    // Test invalid thread count values
    assertThat(configManager.updateMoveThreadCount(0)).isFalse();
    assertThat(configManager.updateMoveThreadCount(-1)).isFalse();
    assertThat(configManager.updateMoveThreadCount(33)).isFalse();
    assertThat(configManager.updateMoveThreadCount(100)).isFalse();

    // Verify thread count remains unchanged
    assertThat(threadPoolManager.getCurrentThreadCount()).isEqualTo(2);
  }

  @Test
  void testUpdateMoveThreadBufferSize() {
    // Test valid buffer size updates
    assertThat(configManager.updateMoveThreadBufferSize(10)).isTrue();
    assertThat(configManager.updateMoveThreadBufferSize(100)).isTrue();
    assertThat(configManager.updateMoveThreadBufferSize(500)).isTrue();
  }

  @Test
  void testUpdateMoveThreadBufferSizeInvalidValues() {
    // Test invalid buffer size values
    assertThat(configManager.updateMoveThreadBufferSize(0)).isFalse();
    assertThat(configManager.updateMoveThreadBufferSize(-1)).isFalse();
    assertThat(configManager.updateMoveThreadBufferSize(1001)).isFalse();

    // Verify method returns false for invalid values
    assertThat(configManager.updateMoveThreadBufferSize(1000)).isTrue(); // Boundary value
  }

  @Test
  void testUpdateMemoryThresholds() {
    // Test valid memory threshold updates
    assertThat(configManager.updateMemoryThresholds(0.7, 0.85, 0.95)).isTrue();
    assertThat(configManager.updateMemoryThresholds(0.5, 0.7, 0.8)).isTrue();
    assertThat(configManager.updateMemoryThresholds(0.1, 0.5, 0.9)).isTrue();
  }

  @Test
  void testUpdateMemoryThresholdsInvalidValues() {
    // Test invalid threshold values
    assertThat(configManager.updateMemoryThresholds(-0.1, 0.8, 0.9)).isFalse();
    assertThat(configManager.updateMemoryThresholds(1.1, 0.8, 0.9)).isFalse();
    assertThat(configManager.updateMemoryThresholds(0.7, -0.1, 0.9)).isFalse();
    assertThat(configManager.updateMemoryThresholds(0.7, 1.1, 0.9)).isFalse();
    assertThat(configManager.updateMemoryThresholds(0.7, 0.8, -0.1)).isFalse();
    assertThat(configManager.updateMemoryThresholds(0.7, 0.8, 1.1)).isFalse();

    // Test invalid ordering
    assertThat(configManager.updateMemoryThresholds(0.9, 0.8, 0.7)).isFalse();
    assertThat(configManager.updateMemoryThresholds(0.8, 0.8, 0.9)).isFalse();
    assertThat(configManager.updateMemoryThresholds(0.7, 0.9, 0.8)).isFalse();
  }

  @Test
  void testUpdatePerformanceThresholds() {
    // Test valid performance threshold updates
    assertThat(configManager.updatePerformanceThresholds(0.8, 30000)).isTrue();
    assertThat(configManager.updatePerformanceThresholds(0.5, 60000)).isTrue();
    assertThat(configManager.updatePerformanceThresholds(0.9, 15000)).isTrue();
  }

  @Test
  void testUpdatePerformanceThresholdsInvalidValues() {
    // Test invalid threshold values
    assertThat(configManager.updatePerformanceThresholds(-0.1, 30000)).isFalse();
    assertThat(configManager.updatePerformanceThresholds(1.1, 30000)).isFalse();
    assertThat(configManager.updatePerformanceThresholds(0.8, 500)).isFalse(); // Too low interval

    // Verify method returns true for boundary values
    assertThat(configManager.updatePerformanceThresholds(0.0, 1000)).isTrue();
    assertThat(configManager.updatePerformanceThresholds(1.0, 1000)).isTrue();
  }

  @Test
  void testUpdateErrorRecoverySettings() {
    // Test valid error recovery settings
    assertThat(configManager.updateErrorRecoverySettings(5, 1000, 3)).isTrue();
    assertThat(configManager.updateErrorRecoverySettings(10, 2000, 5)).isTrue();
    assertThat(configManager.updateErrorRecoverySettings(1, 0, 0)).isTrue();
  }

  @Test
  void testUpdateErrorRecoverySettingsInvalidValues() {
    // Test invalid error recovery settings
    assertThat(configManager.updateErrorRecoverySettings(0, 1000, 3)).isFalse();
    assertThat(configManager.updateErrorRecoverySettings(-1, 1000, 3)).isFalse();
    assertThat(configManager.updateErrorRecoverySettings(5, -1, 3)).isFalse();
    assertThat(configManager.updateErrorRecoverySettings(5, 1000, -1)).isFalse();
  }

  @Test
  void testUpdateMonitoringIntervals() {
    // Test valid monitoring interval updates
    assertThat(configManager.updateMonitoringIntervals(5000, 30000)).isTrue();
    assertThat(configManager.updateMonitoringIntervals(10000, 60000)).isTrue();
    assertThat(configManager.updateMonitoringIntervals(2000, 10000)).isTrue();
  }

  @Test
  void testUpdateMonitoringIntervalsInvalidValues() {
    // Test invalid monitoring interval values
    assertThat(configManager.updateMonitoringIntervals(500, 30000)).isFalse(); // Too low health check
    assertThat(configManager.updateMonitoringIntervals(1000, 3000)).isFalse(); // Too low detailed report
    assertThat(configManager.updateMonitoringIntervals(-1, 30000)).isFalse();
    assertThat(configManager.updateMonitoringIntervals(5000, -1)).isFalse();

    // Verify boundary values work
    assertThat(configManager.updateMonitoringIntervals(1000, 5000)).isTrue();
  }

  @Test
  void testGetCurrentConfiguration() {
    // Test getting current configuration
    RuntimeConfigurationManager.RuntimeConfiguration config = configManager.getCurrentConfiguration();
    assertThat(config).isNotNull();
    assertThat(config.getCurrentThreadCount()).isEqualTo(2);
    assertThat(config.isConfigurationEnabled()).isTrue();
    assertThat(config.getConfigurationUpdateCount()).isEqualTo(0);
    assertThat(config.getLastConfigurationUpdate()).isEqualTo(0);
  }

  @Test
  void testConfigurationChangeEvents() {
    // Test configuration change event notifications
    AtomicReference<RuntimeConfigurationManager.ConfigurationChangeEvent> lastEvent = new AtomicReference<>();
    CountDownLatch eventLatch = new CountDownLatch(1);

    configManager.setConfigurationChangeListener(event -> {
      lastEvent.set(event);
      eventLatch.countDown();
    });

    // Trigger a configuration change
    assertThat(configManager.updateMoveThreadCount(3)).isTrue();

    // Wait for event notification
    try {
      boolean eventReceived = eventLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
      assertThat(eventReceived).isTrue();
      
      RuntimeConfigurationManager.ConfigurationChangeEvent event = lastEvent.get();
      assertThat(event).isNotNull();
      assertThat(event.getSetting()).isEqualTo("moveThreadCount");
      assertThat(event.getOldValue()).isEqualTo(2);
      assertThat(event.getNewValue()).isEqualTo(3);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  @Test
  void testConfigurationDisabled() {
    // Test that configuration updates are disabled
    configManager.setConfigurationEnabled(false);

    assertThat(configManager.updateMoveThreadCount(4)).isFalse();
    assertThat(configManager.updateMemoryThresholds(0.7, 0.8, 0.9)).isFalse();
    assertThat(configManager.updatePerformanceThresholds(0.8, 30000)).isFalse();

    // Verify thread count remains unchanged
    assertThat(threadPoolManager.getCurrentThreadCount()).isEqualTo(2);
  }

  @Test
  void testConfigurationEnabled() {
    // Test enabling configuration updates
    configManager.setConfigurationEnabled(false);
    assertThat(configManager.updateMoveThreadCount(4)).isFalse();

    configManager.setConfigurationEnabled(true);
    assertThat(configManager.updateMoveThreadCount(4)).isTrue();
    assertThat(threadPoolManager.getCurrentThreadCount()).isEqualTo(4);
  }

  @Test
  void testResetConfigurationStatistics() {
    // Record some configuration changes
    configManager.updateMoveThreadCount(3);
    configManager.updateMemoryThresholds(0.7, 0.8, 0.9);
    configManager.updatePerformanceThresholds(0.8, 30000);

    RuntimeConfigurationManager.RuntimeConfiguration config = configManager.getCurrentConfiguration();
    assertThat(config.getConfigurationUpdateCount()).isEqualTo(3);
    assertThat(config.getLastConfigurationUpdate()).isGreaterThan(0);

    // Reset statistics
    configManager.resetConfigurationStatistics();

    config = configManager.getCurrentConfiguration();
    assertThat(config.getConfigurationUpdateCount()).isEqualTo(0);
    assertThat(config.getLastConfigurationUpdate()).isEqualTo(0);
  }

  @Test
  void testConfigurationChangeStatistics() {
    // Test that configuration change statistics are tracked
    RuntimeConfigurationManager.RuntimeConfiguration initialConfig = configManager.getCurrentConfiguration();
    assertThat(initialConfig.getConfigurationUpdateCount()).isEqualTo(0);
    assertThat(initialConfig.getLastConfigurationUpdate()).isEqualTo(0);

    // Make several configuration changes
    configManager.updateMoveThreadCount(3);
    configManager.updateMoveThreadCount(4);
    configManager.updateMemoryThresholds(0.7, 0.8, 0.9);

    RuntimeConfigurationManager.RuntimeConfiguration updatedConfig = configManager.getCurrentConfiguration();
    assertThat(updatedConfig.getConfigurationUpdateCount()).isEqualTo(3);
    assertThat(updatedConfig.getLastConfigurationUpdate()).isGreaterThan(0);
  }

  @Test
  void testConfigurationChangeListenerExceptionHandling() {
    // Test that exceptions in configuration change listeners don't break the system
    configManager.setConfigurationChangeListener(event -> {
      throw new RuntimeException("Test exception in listener");
    });

    // The configuration update should still succeed despite the listener exception
    assertThat(configManager.updateMoveThreadCount(3)).isTrue();
    assertThat(threadPoolManager.getCurrentThreadCount()).isEqualTo(3);
  }

  @Test
  void testMultipleConfigurationUpdates() {
    // Test multiple configuration updates in sequence
    assertThat(configManager.updateMoveThreadCount(1)).isTrue();
    assertThat(configManager.updateMoveThreadBufferSize(50)).isTrue();
    assertThat(configManager.updateMemoryThresholds(0.6, 0.8, 0.9)).isTrue();
    assertThat(configManager.updatePerformanceThresholds(0.7, 20000)).isTrue();
    assertThat(configManager.updateErrorRecoverySettings(8, 1500, 4)).isTrue();
    assertThat(configManager.updateMonitoringIntervals(8000, 40000)).isTrue();

    // Verify all changes were applied
    assertThat(threadPoolManager.getCurrentThreadCount()).isEqualTo(1);
    
    RuntimeConfigurationManager.RuntimeConfiguration config = configManager.getCurrentConfiguration();
    assertThat(config.getConfigurationUpdateCount()).isEqualTo(6);
  }
}