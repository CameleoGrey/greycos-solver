package ai.greycos.solver.core.impl.solver.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.greycos.solver.core.impl.solver.thread.ErrorRecoveryManager.RecoveryState;
import ai.greycos.solver.core.impl.solver.thread.MemoryMonitor.MemoryPressureLevel;

import org.junit.jupiter.api.Test;

class ThreadMonitoringTimingTest {

  @Test
  void adaptiveThreadPoolAdjustmentRunsWhenIntervalElapsed() {
    var memoryMonitor = mock(MemoryMonitor.class);
    var performanceMetrics = mock(PerformanceMetrics.class);
    var performanceStatistics = mock(PerformanceMetrics.PerformanceStatistics.class);

    when(memoryMonitor.checkMemoryUsage()).thenReturn(MemoryPressureLevel.NORMAL);
    when(performanceMetrics.getStatistics()).thenReturn(performanceStatistics);
    when(performanceStatistics.getOverallEfficiency()).thenReturn(0.0);

    var manager =
        new AdaptiveThreadPoolManager(
            runnable -> new Thread(runnable, "test-adaptive-thread"),
            memoryMonitor,
            performanceMetrics,
            2);
    try {
      manager.setAdjustmentInterval(1L);
      manager.checkAndAdjustThreadPool();

      assertThat(manager.getCurrentThreadCount()).isEqualTo(1);
    } finally {
      manager.shutdown();
    }
  }

  @Test
  void multithreadingHealthCheckTriggersThreadAdjustment() {
    var memoryMonitor = mock(MemoryMonitor.class);
    var performanceMetrics = mock(PerformanceMetrics.class);
    var threadPoolManager = mock(AdaptiveThreadPoolManager.class);
    var errorRecoveryManager = mock(ErrorRecoveryManager.class);

    var performanceStatistics = mock(PerformanceMetrics.PerformanceStatistics.class);
    when(memoryMonitor.checkMemoryUsage()).thenReturn(MemoryPressureLevel.NORMAL);
    when(performanceMetrics.getStatistics()).thenReturn(performanceStatistics);
    when(performanceStatistics.getOverallEfficiency()).thenReturn(90.0);

    when(errorRecoveryManager.getRecoveryStatistics())
        .thenReturn(
            new ErrorRecoveryManager.RecoveryStatistics(
                0, 0, RecoveryState.NORMAL, 0L, 0L, System.currentTimeMillis()));
    when(threadPoolManager.getAdjustmentStatistics())
        .thenReturn(
            new AdaptiveThreadPoolManager.AdjustmentStatistics(
                1, 2, MemoryPressureLevel.NORMAL, 90.0, true));

    var monitor =
        new MultithreadingMonitor(
            memoryMonitor, performanceMetrics, threadPoolManager, errorRecoveryManager);
    monitor.setHealthCheckInterval(1L);
    monitor.setLogHealthStatus(false);

    monitor.performHealthCheck();

    verify(threadPoolManager).forceAdjustment();
  }
}
