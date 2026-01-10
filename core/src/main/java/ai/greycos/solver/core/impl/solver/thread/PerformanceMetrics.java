package ai.greycos.solver.core.impl.solver.thread;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performance metrics collection system for multithreaded solving. This class collects and reports
 * various performance metrics including calculation counts, throughput, latency, and efficiency.
 *
 * @since 1.0.0
 */
public class PerformanceMetrics {

  private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceMetrics.class);

  private final AtomicLong totalCalculations = new AtomicLong(0);
  private final AtomicLong totalMovesEvaluated = new AtomicLong(0);
  private final AtomicLong totalMovesAccepted = new AtomicLong(0);
  private final AtomicLong totalStepsTaken = new AtomicLong(0);
  private final AtomicLong startTime = new AtomicLong(0);
  private final AtomicLong endTime = new AtomicLong(0);

  private final DoubleAdder totalEvaluationTime = new DoubleAdder();
  private final DoubleAdder totalStepTime = new DoubleAdder();
  private final DoubleAdder totalBarrierWaitTime = new DoubleAdder();

  private final AtomicLong bestScoreCalculationCount = new AtomicLong(0);
  private final AtomicLong lastBestScoreCalculationCount = new AtomicLong(0);

  private final ThreadMetrics[] threadMetrics;

  private volatile boolean metricsEnabled = true;
  private volatile long reportingInterval = 10000;
  private volatile long lastReportTime = 0;

  public PerformanceMetrics(int threadCount) {
    this.threadMetrics = new ThreadMetrics[threadCount];
    for (int i = 0; i < threadCount; i++) {
      threadMetrics[i] = new ThreadMetrics(i);
    }
    startTime.set(System.currentTimeMillis());
  }

  /**
   * Records a move evaluation.
   *
   * @param threadIndex index of the thread that performed the evaluation
   * @param evaluationTime time taken for evaluation in nanoseconds
   * @param accepted whether the move was accepted
   */
  public void recordMoveEvaluation(int threadIndex, long evaluationTime, boolean accepted) {
    if (!metricsEnabled) return;

    totalMovesEvaluated.incrementAndGet();
    totalEvaluationTime.add(evaluationTime / 1_000_000.0); // Convert to milliseconds

    if (accepted) {
      totalMovesAccepted.incrementAndGet();
    }

    if (threadIndex >= 0 && threadIndex < threadMetrics.length) {
      threadMetrics[threadIndex].recordMoveEvaluation(evaluationTime, accepted);
    }

    checkAndReportMetrics();
  }

  public void recordStepCompletion(long stepTime, long barrierWaitTime) {
    if (!metricsEnabled) return;

    totalStepsTaken.incrementAndGet();
    totalStepTime.add(stepTime / 1_000_000.0); // Convert to milliseconds
    totalBarrierWaitTime.add(barrierWaitTime / 1_000_000.0); // Convert to milliseconds

    checkAndReportMetrics();
  }

  /**
   * Records calculation count from a thread.
   *
   * @param threadIndex index of the thread
   * @param calculationCount number of calculations performed by the thread
   */
  public void recordCalculationCount(int threadIndex, long calculationCount) {
    if (!metricsEnabled) return;

    totalCalculations.addAndGet(calculationCount);

    if (threadIndex >= 0 && threadIndex < threadMetrics.length) {
      threadMetrics[threadIndex].updateCalculationCount(calculationCount);
    }
  }

  public void recordBestScoreCalculationCount(long calculationCount) {
    if (!metricsEnabled) return;

    lastBestScoreCalculationCount.set(bestScoreCalculationCount.get());
    bestScoreCalculationCount.set(calculationCount);
  }

  public PerformanceStatistics getStatistics() {
    if (!metricsEnabled) {
      return new PerformanceStatistics(
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, new ThreadStatistics[0], 0.0);
    }

    long currentTime = System.currentTimeMillis();
    long duration = currentTime - startTime.get();

    double calculationsPerSecond = duration > 0 ? (totalCalculations.get() * 1000.0 / duration) : 0;
    double movesPerSecond = duration > 0 ? (totalMovesEvaluated.get() * 1000.0 / duration) : 0;
    double acceptanceRate =
        totalMovesEvaluated.get() > 0
            ? (totalMovesAccepted.get() * 100.0 / totalMovesEvaluated.get())
            : 0;
    double averageEvaluationTime =
        totalMovesEvaluated.get() > 0 ? (totalEvaluationTime.sum() / totalMovesEvaluated.get()) : 0;
    double averageStepTime =
        totalStepsTaken.get() > 0 ? (totalStepTime.sum() / totalStepsTaken.get()) : 0;
    double averageBarrierWaitTime =
        totalStepsTaken.get() > 0 ? (totalBarrierWaitTime.sum() / totalStepsTaken.get()) : 0;
    double efficiency =
        totalStepsTaken.get() > 0 ? (totalMovesAccepted.get() * 100.0 / totalStepsTaken.get()) : 0;

    return new PerformanceStatistics(
        totalCalculations.get(),
        totalMovesEvaluated.get(),
        totalMovesAccepted.get(),
        totalStepsTaken.get(),
        calculationsPerSecond,
        movesPerSecond,
        acceptanceRate,
        averageEvaluationTime,
        averageStepTime,
        averageBarrierWaitTime,
        efficiency,
        bestScoreCalculationCount.get(),
        lastBestScoreCalculationCount.get(),
        duration,
        getThreadStatistics(),
        getOverallEfficiency());
  }

  public void reset() {
    totalCalculations.set(0);
    totalMovesEvaluated.set(0);
    totalMovesAccepted.set(0);
    totalStepsTaken.set(0);
    totalEvaluationTime.reset();
    totalStepTime.reset();
    totalBarrierWaitTime.reset();
    bestScoreCalculationCount.set(0);
    lastBestScoreCalculationCount.set(0);
    startTime.set(System.currentTimeMillis());
    endTime.set(0);

    for (ThreadMetrics metrics : threadMetrics) {
      metrics.reset();
    }
  }

  /** Marks the end of solving and calculates final metrics. */
  public void finish() {
    endTime.set(System.currentTimeMillis());
    if (metricsEnabled) {
      PerformanceStatistics stats = getStatistics();
      LOGGER.info("Final performance metrics: {}", stats);
    }
  }

  public void setMetricsEnabled(boolean enabled) {
    this.metricsEnabled = enabled;
  }

  public void setReportingInterval(long interval) {
    if (interval <= 0) {
      throw new IllegalArgumentException("Reporting interval must be positive");
    }
    this.reportingInterval = interval;
  }

  private void checkAndReportMetrics() {
    if (!metricsEnabled) return;

    long currentTime = System.currentTimeMillis();
    if (currentTime - lastReportTime >= reportingInterval) {
      lastReportTime = currentTime;
      PerformanceStatistics stats = getStatistics();
      LOGGER.debug("Performance metrics: {}", stats);
    }
  }

  private double getOverallEfficiency() {
    if (totalStepsTaken.get() == 0) return 0.0;

    double totalThreadEfficiency = 0.0;
    for (ThreadMetrics metrics : threadMetrics) {
      totalThreadEfficiency += metrics.getEfficiency();
    }

    return totalThreadEfficiency / threadMetrics.length;
  }

  private ThreadStatistics[] getThreadStatistics() {
    ThreadStatistics[] stats = new ThreadStatistics[threadMetrics.length];
    for (int i = 0; i < threadMetrics.length; i++) {
      stats[i] = threadMetrics[i].getStatistics();
    }
    return stats;
  }

  private static class ThreadMetrics {
    private final int threadIndex;
    private final AtomicLong movesEvaluated = new AtomicLong(0);
    private final AtomicLong movesAccepted = new AtomicLong(0);
    private final AtomicLong calculationCount = new AtomicLong(0);
    private final DoubleAdder evaluationTime = new DoubleAdder();

    public ThreadMetrics(int threadIndex) {
      this.threadIndex = threadIndex;
    }

    public void recordMoveEvaluation(long evaluationTime, boolean accepted) {
      movesEvaluated.incrementAndGet();
      this.evaluationTime.add(evaluationTime / 1_000_000.0);
      if (accepted) {
        movesAccepted.incrementAndGet();
      }
    }

    public void updateCalculationCount(long count) {
      calculationCount.set(count);
    }

    public void reset() {
      movesEvaluated.set(0);
      movesAccepted.set(0);
      calculationCount.set(0);
      evaluationTime.reset();
    }

    public ThreadStatistics getStatistics() {
      double efficiency =
          movesEvaluated.get() > 0 ? (movesAccepted.get() * 100.0 / movesEvaluated.get()) : 0;
      double avgEvalTime =
          movesEvaluated.get() > 0 ? (evaluationTime.sum() / movesEvaluated.get()) : 0;

      return new ThreadStatistics(
          threadIndex,
          movesEvaluated.get(),
          movesAccepted.get(),
          calculationCount.get(),
          efficiency,
          avgEvalTime);
    }

    public double getEfficiency() {
      return movesEvaluated.get() > 0 ? (movesAccepted.get() * 100.0 / movesEvaluated.get()) : 0;
    }
  }

  public static class PerformanceStatistics {
    private final long totalCalculations;
    private final long totalMovesEvaluated;
    private final long totalMovesAccepted;
    private final long totalStepsTaken;
    private final double calculationsPerSecond;
    private final double movesPerSecond;
    private final double acceptanceRate;
    private final double averageEvaluationTime;
    private final double averageStepTime;
    private final double averageBarrierWaitTime;
    private final double efficiency;
    private final long bestScoreCalculationCount;
    private final long lastBestScoreCalculationCount;
    private final long duration;
    private final ThreadStatistics[] threadStatistics;
    private final double overallEfficiency;

    public PerformanceStatistics(
        long totalCalculations,
        long totalMovesEvaluated,
        long totalMovesAccepted,
        long totalStepsTaken,
        double calculationsPerSecond,
        double movesPerSecond,
        double acceptanceRate,
        double averageEvaluationTime,
        double averageStepTime,
        double averageBarrierWaitTime,
        double efficiency,
        long bestScoreCalculationCount,
        long lastBestScoreCalculationCount,
        long duration,
        ThreadStatistics[] threadStatistics,
        double overallEfficiency) {
      this.totalCalculations = totalCalculations;
      this.totalMovesEvaluated = totalMovesEvaluated;
      this.totalMovesAccepted = totalMovesAccepted;
      this.totalStepsTaken = totalStepsTaken;
      this.calculationsPerSecond = calculationsPerSecond;
      this.movesPerSecond = movesPerSecond;
      this.acceptanceRate = acceptanceRate;
      this.averageEvaluationTime = averageEvaluationTime;
      this.averageStepTime = averageStepTime;
      this.averageBarrierWaitTime = averageBarrierWaitTime;
      this.efficiency = efficiency;
      this.bestScoreCalculationCount = bestScoreCalculationCount;
      this.lastBestScoreCalculationCount = lastBestScoreCalculationCount;
      this.duration = duration;
      this.threadStatistics = threadStatistics;
      this.overallEfficiency = overallEfficiency;
    }

    public long getTotalCalculations() {
      return totalCalculations;
    }

    public long getTotalMovesEvaluated() {
      return totalMovesEvaluated;
    }

    public long getTotalMovesAccepted() {
      return totalMovesAccepted;
    }

    public long getTotalStepsTaken() {
      return totalStepsTaken;
    }

    public double getCalculationsPerSecond() {
      return calculationsPerSecond;
    }

    public double getMovesPerSecond() {
      return movesPerSecond;
    }

    public double getAcceptanceRate() {
      return acceptanceRate;
    }

    public double getAverageEvaluationTime() {
      return averageEvaluationTime;
    }

    public double getAverageStepTime() {
      return averageStepTime;
    }

    public double getAverageBarrierWaitTime() {
      return averageBarrierWaitTime;
    }

    public double getEfficiency() {
      return efficiency;
    }

    public long getBestScoreCalculationCount() {
      return bestScoreCalculationCount;
    }

    public long getLastBestScoreCalculationCount() {
      return lastBestScoreCalculationCount;
    }

    public long getDuration() {
      return duration;
    }

    public ThreadStatistics[] getThreadStatistics() {
      return threadStatistics;
    }

    public double getOverallEfficiency() {
      return overallEfficiency;
    }

    @Override
    public String toString() {
      return String.format(
          "PerformanceStatistics{calculations=%d, movesEvaluated=%d, movesAccepted=%d, "
              + "stepsTaken=%d, calcPerSec=%.2f, movesPerSec=%.2f, acceptanceRate=%.2f%%, "
              + "avgEvalTime=%.2fms, avgStepTime=%.2fms, avgBarrierWait=%.2fms, efficiency=%.2f%%, "
              + "bestScoreCalcCount=%d, duration=%dms, overallEfficiency=%.2f%%}",
          totalCalculations,
          totalMovesEvaluated,
          totalMovesAccepted,
          totalStepsTaken,
          calculationsPerSecond,
          movesPerSecond,
          acceptanceRate,
          averageEvaluationTime,
          averageStepTime,
          averageBarrierWaitTime,
          efficiency,
          bestScoreCalculationCount,
          duration,
          overallEfficiency);
    }
  }

  public static class ThreadStatistics {
    private final int threadIndex;
    private final long movesEvaluated;
    private final long movesAccepted;
    private final long calculationCount;
    private final double efficiency;
    private final double averageEvaluationTime;

    public ThreadStatistics(
        int threadIndex,
        long movesEvaluated,
        long movesAccepted,
        long calculationCount,
        double efficiency,
        double averageEvaluationTime) {
      this.threadIndex = threadIndex;
      this.movesEvaluated = movesEvaluated;
      this.movesAccepted = movesAccepted;
      this.calculationCount = calculationCount;
      this.efficiency = efficiency;
      this.averageEvaluationTime = averageEvaluationTime;
    }

    // Getters
    public int getThreadIndex() {
      return threadIndex;
    }

    public long getMovesEvaluated() {
      return movesEvaluated;
    }

    public long getMovesAccepted() {
      return movesAccepted;
    }

    public long getCalculationCount() {
      return calculationCount;
    }

    public double getEfficiency() {
      return efficiency;
    }

    public double getAverageEvaluationTime() {
      return averageEvaluationTime;
    }

    @Override
    public String toString() {
      return String.format(
          "ThreadStatistics{thread=%d, movesEvaluated=%d, movesAccepted=%d, "
              + "calculationCount=%d, efficiency=%.2f%%, avgEvalTime=%.2fms}",
          threadIndex,
          movesEvaluated,
          movesAccepted,
          calculationCount,
          efficiency,
          averageEvaluationTime);
    }
  }
}
