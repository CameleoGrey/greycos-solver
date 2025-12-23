package ai.greycos.solver.core.impl.test.multithreading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ai.greycos.solver.core.impl.solver.thread.AdaptiveThreadPoolManager;
import ai.greycos.solver.core.impl.solver.thread.ErrorRecoveryManager;
import ai.greycos.solver.core.impl.solver.thread.MemoryMonitor;
import ai.greycos.solver.core.impl.solver.thread.MultithreadingMonitor;
import ai.greycos.solver.core.impl.solver.thread.PerformanceMetrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Enhanced features tests for multithreading functionality. These tests validate the new advanced
 * features including memory monitoring, performance metrics, adaptive thread management, and error
 * recovery.
 */
public class EnhancedFeaturesTest {

  private MemoryMonitor memoryMonitor;
  private PerformanceMetrics performanceMetrics;
  private AdaptiveThreadPoolManager threadPoolManager;
  private ErrorRecoveryManager errorRecoveryManager;
  private MultithreadingMonitor monitor;

  @BeforeEach
  void setUp() {
    memoryMonitor = new MemoryMonitor();
    performanceMetrics = new PerformanceMetrics(4);
    errorRecoveryManager = new ErrorRecoveryManager();

    // Create a simple thread factory for testing
    java.util.concurrent.ThreadFactory threadFactory =
        r -> new Thread(r, "TestThread-" + System.currentTimeMillis());

    threadPoolManager =
        new AdaptiveThreadPoolManager(threadFactory, memoryMonitor, performanceMetrics, 2);
    monitor =
        new MultithreadingMonitor(
            memoryMonitor, performanceMetrics, threadPoolManager, errorRecoveryManager);
  }

  @Test
  void testMemoryMonitorBasicFunctionality() {
    // Test basic memory monitoring
    MemoryMonitor.MemoryPressureLevel pressure = memoryMonitor.checkMemoryUsage();
    assertThat(pressure).isNotNull();

    // Test memory statistics
    MemoryMonitor.MemoryStatistics stats = memoryMonitor.getCurrentMemoryStatistics();
    assertThat(stats).isNotNull();
    assertThat(stats.getUsedMemoryAfterGC()).isGreaterThanOrEqualTo(0);
    assertThat(stats.getMaxMemory()).isGreaterThan(0);

    // Test memory pressure statistics
    MemoryMonitor.MemoryPressureStatistics pressureStats =
        memoryMonitor.getMemoryPressureStatistics();
    assertThat(pressureStats).isNotNull();
    assertThat(pressureStats.getTotalPressureEvents()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void testMemoryMonitorConfiguration() {
    // Test threshold configuration
    memoryMonitor.setWarningThreshold(0.7);
    memoryMonitor.setCriticalThreshold(0.85);
    memoryMonitor.setEmergencyThreshold(0.95);

    assertThat(memoryMonitor.getAvailableMemory()).isGreaterThanOrEqualTo(0);

    // Test invalid configurations
    assertThatThrownBy(() -> memoryMonitor.setWarningThreshold(1.5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> memoryMonitor.setWarningThreshold(-0.1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testPerformanceMetricsCollection() {
    // Test metrics recording
    performanceMetrics.recordMoveEvaluation(0, 1000000, true); // 1ms in nanoseconds
    performanceMetrics.recordMoveEvaluation(1, 2000000, false); // 2ms in nanoseconds
    performanceMetrics.recordStepCompletion(5000000, 1000000); // 5ms step, 1ms barrier

    PerformanceMetrics.PerformanceStatistics stats = performanceMetrics.getStatistics();
    assertThat(stats.getTotalMovesEvaluated()).isEqualTo(2);
    assertThat(stats.getTotalMovesAccepted()).isEqualTo(1);
    assertThat(stats.getTotalStepsTaken()).isEqualTo(1);
    assertThat(stats.getAcceptanceRate()).isEqualTo(50.0);
  }

  @Test
  void testPerformanceMetricsThreadSpecific() {
    // Test thread-specific metrics
    performanceMetrics.recordMoveEvaluation(0, 1000000, true);
    performanceMetrics.recordMoveEvaluation(0, 2000000, false);
    performanceMetrics.recordMoveEvaluation(1, 1500000, true);

    PerformanceMetrics.PerformanceStatistics stats = performanceMetrics.getStatistics();
    assertThat(stats.getThreadStatistics()).hasSize(4);

    PerformanceMetrics.ThreadStatistics thread0Stats = stats.getThreadStatistics()[0];
    assertThat(thread0Stats.getMovesEvaluated()).isEqualTo(2);
    assertThat(thread0Stats.getMovesAccepted()).isEqualTo(1);
    assertThat(thread0Stats.getEfficiency()).isEqualTo(50.0);
  }

  @Test
  void testAdaptiveThreadPoolManager() {
    // Test thread adjustment
    int initialThreads = threadPoolManager.getCurrentThreadCount();
    assertThat(initialThreads).isEqualTo(2);

    // Test adjustment statistics
    AdaptiveThreadPoolManager.AdjustmentStatistics adjustmentStats =
        threadPoolManager.getAdjustmentStatistics();
    assertThat(adjustmentStats).isNotNull();
    assertThat(adjustmentStats.getCurrentThreads()).isEqualTo(2);

    // Test configuration
    threadPoolManager.setMinThreadCount(1);
    threadPoolManager.setMaxThreadCount(8);
    threadPoolManager.setAdjustmentInterval(10000);

    assertThat(threadPoolManager.getTargetThreadCount()).isEqualTo(2);
  }

  @Test
  void testAdaptiveThreadPoolManagerConfiguration() {
    // Test invalid configurations
    assertThatThrownBy(() -> threadPoolManager.setMinThreadCount(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> threadPoolManager.setMaxThreadCount(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> threadPoolManager.setAdjustmentInterval(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testErrorRecoveryManager() {
    // Test error recording
    RuntimeException testError = new RuntimeException("Test error");
    boolean recoveryTriggered = errorRecoveryManager.recordError(testError, 0);

    // Should not trigger recovery immediately (below threshold)
    assertThat(recoveryTriggered).isFalse();

    // Record multiple errors to trigger recovery
    for (int i = 0; i < 10; i++) {
      errorRecoveryManager.recordError(new RuntimeException("Error " + i), 0);
    }

    ErrorRecoveryManager.RecoveryStatistics stats = errorRecoveryManager.getRecoveryStatistics();
    assertThat(stats.getErrorCount()).isGreaterThanOrEqualTo(10);
    assertThat(stats.getRecoveryState()).isNotNull();
  }

  @Test
  void testErrorRecoveryManagerConfiguration() {
    // Test configuration
    errorRecoveryManager.setMaxErrorCount(5);
    errorRecoveryManager.setRecoveryDelay(500);
    errorRecoveryManager.setMaxRecoveryAttempts(2);

    // Test invalid configurations
    assertThatThrownBy(() -> errorRecoveryManager.setMaxErrorCount(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> errorRecoveryManager.setRecoveryDelay(-100))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testMultithreadingMonitor() {
    // Test health check
    MultithreadingMonitor.HealthStatus healthStatus = monitor.getHealthStatus();
    assertThat(healthStatus).isNotNull();
    assertThat(healthStatus.getOverallHealth()).isNotNull();

    // Test detailed report
    MultithreadingMonitor.MonitoringReport report = monitor.getDetailedReport();
    assertThat(report).isNotNull();
    assertThat(report.getTimestamp()).isGreaterThan(0);

    // Test performance suggestions
    MultithreadingMonitor.PerformanceSuggestions suggestions = monitor.getPerformanceSuggestions();
    assertThat(suggestions).isNotNull();
  }

  @Test
  void testMultithreadingMonitorConfiguration() {
    // Test configuration
    monitor.setHealthCheckInterval(10000);
    monitor.setDetailedReportInterval(60000);
    monitor.setLogHealthStatus(false);
    monitor.setLogDetailedReports(false);

    // Test invalid configurations
    assertThatThrownBy(() -> monitor.setHealthCheckInterval(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> monitor.setDetailedReportInterval(-1000))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testIntegratedMonitoringSystem() throws InterruptedException {
    // Test the integrated monitoring system
    ExecutorService executor = Executors.newFixedThreadPool(2);

    // Simulate some work
    CountDownLatch latch = new CountDownLatch(10);
    AtomicInteger completedTasks = new AtomicInteger(0);

    for (int i = 0; i < 10; i++) {
      executor.submit(
          () -> {
            try {
              // Simulate work
              Thread.sleep(100);
              performanceMetrics.recordMoveEvaluation(0, 1000000, true);
              performanceMetrics.recordCalculationCount(0, 100);
              completedTasks.incrementAndGet();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              latch.countDown();
            }
          });
    }

    // Wait for completion
    boolean completed = latch.await(5, TimeUnit.SECONDS);
    assertThat(completed).isTrue();
    assertThat(completedTasks.get()).isEqualTo(10);

    // Check final statistics
    PerformanceMetrics.PerformanceStatistics stats = performanceMetrics.getStatistics();
    assertThat(stats.getTotalMovesEvaluated()).isEqualTo(10);
    assertThat(stats.getTotalCalculations()).isEqualTo(1000);

    executor.shutdown();
  }

  @Test
  void testMemoryPressureHandling() {
    // Test memory pressure detection
    MemoryMonitor.MemoryPressureLevel initialPressure = memoryMonitor.checkMemoryUsage();

    // Force garbage collection to simulate memory pressure relief
    MemoryMonitor.MemoryStatistics gcStats = memoryMonitor.forceGarbageCollection();
    assertThat(gcStats).isNotNull();
    assertThat(gcStats.getMemoryFreedByGC()).isGreaterThanOrEqualTo(0);

    // Check that memory usage is reasonable
    MemoryMonitor.MemoryStatistics currentStats = memoryMonitor.getCurrentMemoryStatistics();
    assertThat(currentStats.getMemoryUsagePercentage()).isBetween(0.0, 1.0);
  }

  @Test
  void testPerformanceMetricsReset() {
    // Record some metrics
    performanceMetrics.recordMoveEvaluation(0, 1000000, true);
    performanceMetrics.recordStepCompletion(5000000, 1000000);

    PerformanceMetrics.PerformanceStatistics beforeReset = performanceMetrics.getStatistics();
    assertThat(beforeReset.getTotalMovesEvaluated()).isEqualTo(1);

    // Reset metrics
    performanceMetrics.reset();

    PerformanceMetrics.PerformanceStatistics afterReset = performanceMetrics.getStatistics();
    assertThat(afterReset.getTotalMovesEvaluated()).isEqualTo(0);
    assertThat(afterReset.getTotalStepsTaken()).isEqualTo(0);
  }

  @Test
  void testErrorRecoveryReset() {
    // Record some errors
    for (int i = 0; i < 5; i++) {
      errorRecoveryManager.recordError(new RuntimeException("Error " + i), 0);
    }

    ErrorRecoveryManager.RecoveryStatistics beforeReset =
        errorRecoveryManager.getRecoveryStatistics();
    assertThat(beforeReset.getErrorCount()).isEqualTo(5);

    // Reset recovery state
    errorRecoveryManager.resetRecoveryState();

    ErrorRecoveryManager.RecoveryStatistics afterReset =
        errorRecoveryManager.getRecoveryStatistics();
    assertThat(afterReset.getErrorCount()).isEqualTo(0);
    assertThat(afterReset.getRecoveryAttempts()).isEqualTo(0);
  }

  @Test
  void testMonitoringIntegrationWithMultithreading() throws InterruptedException {
    // Test that monitoring works correctly with actual multithreading
    int threadCount = 3;
    PerformanceMetrics testMetrics = new PerformanceMetrics(threadCount);

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    // Simulate multithreaded work
    CountDownLatch latch = new CountDownLatch(threadCount * 5);
    AtomicInteger totalEvaluations = new AtomicInteger(0);

    for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
      final int finalThreadIndex = threadIndex; // Make it effectively final for lambda
      for (int i = 0; i < 5; i++) {
        executor.submit(
            () -> {
              try {
                // Simulate move evaluation
                Thread.sleep(50);
                testMetrics.recordMoveEvaluation(finalThreadIndex, 500000, true);
                totalEvaluations.incrementAndGet();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                latch.countDown();
              }
            });
      }
    }

    // Wait for completion
    boolean completed = latch.await(10, TimeUnit.SECONDS);
    assertThat(completed).isTrue();
    assertThat(totalEvaluations.get()).isEqualTo(threadCount * 5);

    // Verify thread-specific metrics
    PerformanceMetrics.PerformanceStatistics stats = testMetrics.getStatistics();
    assertThat(stats.getTotalMovesEvaluated()).isEqualTo(threadCount * 5);

    PerformanceMetrics.ThreadStatistics[] threadStats = stats.getThreadStatistics();
    assertThat(threadStats).hasSize(threadCount);

    for (int i = 0; i < threadCount; i++) {
      assertThat(threadStats[i].getMovesEvaluated()).isEqualTo(5);
      assertThat(threadStats[i].getMovesAccepted()).isEqualTo(5);
      assertThat(threadStats[i].getEfficiency()).isEqualTo(100.0);
    }

    executor.shutdown();
  }
}
