package ai.greycos.solver.core.impl.solver.thread;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comprehensive monitoring dashboard for multithreaded solving. This class provides a centralized
 * view of all monitoring components including memory usage, performance metrics, thread pool health,
 * and error recovery status.
 * 
 * @since 1.0.0
 */
public class MultithreadingMonitor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MultithreadingMonitor.class);
    
    // Monitoring components
    private final MemoryMonitor memoryMonitor;
    private final PerformanceMetrics performanceMetrics;
    private final AdaptiveThreadPoolManager threadPoolManager;
    private final ErrorRecoveryManager errorRecoveryManager;
    
    // Monitoring state
    private final AtomicBoolean monitoringEnabled = new AtomicBoolean(true);
    private final AtomicLong lastHealthCheck = new AtomicLong(0);
    private final AtomicLong lastDetailedReport = new AtomicLong(0);
    
    // Configuration
    private volatile long healthCheckInterval = 5000; // 5 seconds
    private volatile long detailedReportInterval = 30000; // 30 seconds
    private volatile boolean logHealthStatus = true;
    private volatile boolean logDetailedReports = true;
    
    public MultithreadingMonitor(
            MemoryMonitor memoryMonitor,
            PerformanceMetrics performanceMetrics,
            AdaptiveThreadPoolManager threadPoolManager,
            ErrorRecoveryManager errorRecoveryManager) {
        this.memoryMonitor = memoryMonitor;
        this.performanceMetrics = performanceMetrics;
        this.threadPoolManager = threadPoolManager;
        this.errorRecoveryManager = errorRecoveryManager;
    }
    
    /**
     * Performs a health check of all monitoring components.
     */
    public void performHealthCheck() {
        if (!monitoringEnabled.get()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastHealthCheck.get() < healthCheckInterval) {
            return;
        }
        
        if (lastHealthCheck.compareAndSet(currentTime - healthCheckInterval, currentTime)) {
            HealthStatus healthStatus = getHealthStatus();
            
            if (logHealthStatus) {
                logHealthStatus(healthStatus);
            }
            
            // Trigger recovery if needed
            if (healthStatus.isErrorRecoveryNeeded()) {
                errorRecoveryManager.resetRecoveryState();
            }
            
            // Trigger thread adjustment if needed
            if (healthStatus.isThreadAdjustmentNeeded()) {
                threadPoolManager.forceAdjustment();
            }
        }
    }
    
    /**
     * Generates a detailed monitoring report.
     */
    public void generateDetailedReport() {
        if (!monitoringEnabled.get()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDetailedReport.get() < detailedReportInterval) {
            return;
        }
        
        if (lastDetailedReport.compareAndSet(currentTime - detailedReportInterval, currentTime)) {
            MonitoringReport report = getDetailedReport();
            
            if (logDetailedReports) {
                logDetailedReport(report);
            }
        }
    }
    
    /**
     * Gets the current health status.
     * 
     * @return HealthStatus with current system health
     */
    public HealthStatus getHealthStatus() {
        MemoryMonitor.MemoryPressureLevel memoryPressure = memoryMonitor.checkMemoryUsage();
        PerformanceMetrics.PerformanceStatistics perfStats = performanceMetrics.getStatistics();
        ErrorRecoveryManager.RecoveryStatistics recoveryStats = errorRecoveryManager.getRecoveryStatistics();
        AdaptiveThreadPoolManager.AdjustmentStatistics adjustmentStats = threadPoolManager.getAdjustmentStatistics();
        
        boolean memoryHealthy = memoryPressure == MemoryMonitor.MemoryPressureLevel.NORMAL;
        boolean performanceHealthy = perfStats.getOverallEfficiency() > 50.0; // 50% efficiency threshold
        boolean recoveryHealthy = recoveryStats.getRecoveryState() == ErrorRecoveryManager.RecoveryState.NORMAL;
        boolean threadsHealthy = adjustmentStats.getTargetThreads() == adjustmentStats.getCurrentThreads();
        
        HealthLevel overallHealth = determineOverallHealth(
                memoryHealthy, performanceHealthy, recoveryHealthy, threadsHealthy);
        
        return new HealthStatus(
                overallHealth,
                memoryPressure,
                memoryHealthy,
                performanceHealthy,
                recoveryHealthy,
                threadsHealthy,
                recoveryStats.getErrorCount(),
                adjustmentStats.getAdjustmentNeeded()
        );
    }
    
    /**
     * Gets a detailed monitoring report.
     * 
     * @return MonitoringReport with comprehensive system status
     */
    public MonitoringReport getDetailedReport() {
        MemoryMonitor.MemoryStatistics memoryStats = memoryMonitor.getCurrentMemoryStatistics();
        MemoryMonitor.MemoryPressureStatistics pressureStats = memoryMonitor.getMemoryPressureStatistics();
        PerformanceMetrics.PerformanceStatistics perfStats = performanceMetrics.getStatistics();
        ErrorRecoveryManager.RecoveryStatistics recoveryStats = errorRecoveryManager.getRecoveryStatistics();
        AdaptiveThreadPoolManager.AdjustmentStatistics adjustmentStats = threadPoolManager.getAdjustmentStatistics();
        
        return new MonitoringReport(
                memoryStats,
                pressureStats,
                perfStats,
                recoveryStats,
                adjustmentStats,
                System.currentTimeMillis()
        );
    }
    
    /**
     * Provides performance optimization suggestions based on current metrics.
     * 
     * @return PerformanceSuggestions with optimization recommendations
     */
    public PerformanceSuggestions getPerformanceSuggestions() {
        PerformanceMetrics.PerformanceStatistics perfStats = performanceMetrics.getStatistics();
        MemoryMonitor.MemoryPressureLevel memoryPressure = memoryMonitor.checkMemoryUsage();
        int currentThreads = threadPoolManager.getCurrentThreadCount();
        
        PerformanceSuggestions.Builder builder = new PerformanceSuggestions.Builder();
        
        // Analyze acceptance rate
        if (perfStats.getAcceptanceRate() < 10.0) {
            builder.addSuggestion("Low acceptance rate (" + perfStats.getAcceptanceRate() + 
                    "%). Consider adjusting acceptor configuration or move selectors.");
        }
        
        // Analyze efficiency
        if (perfStats.getOverallEfficiency() < 70.0) {
            builder.addSuggestion("Low overall efficiency (" + perfStats.getOverallEfficiency() + 
                    "%). Consider reducing thread count or improving move evaluation performance.");
        }
        
        // Analyze memory pressure
        if (memoryPressure != MemoryMonitor.MemoryPressureLevel.NORMAL) {
            builder.addSuggestion("High memory pressure detected. Consider reducing move thread count or buffer sizes.");
        }
        
        // Analyze thread count
        if (currentThreads > 4 && perfStats.getOverallEfficiency() < 80.0) {
            builder.addSuggestion("High thread count with low efficiency. Consider reducing thread count.");
        }
        
        // Analyze calculation throughput
        if (perfStats.getCalculationsPerSecond() < 1000) {
            builder.addSuggestion("Low calculation throughput. Consider optimizing score calculation or move evaluation.");
        }
        
        return builder.build();
    }
    
    /**
     * Resets all monitoring statistics.
     */
    public void resetStatistics() {
        memoryMonitor.resetStatistics();
        performanceMetrics.reset();
        errorRecoveryManager.resetRecoveryState();
    }
    
    /**
     * Disables monitoring.
     */
    public void disableMonitoring() {
        monitoringEnabled.set(false);
    }
    
    /**
     * Enables monitoring.
     */
    public void enableMonitoring() {
        monitoringEnabled.set(true);
    }
    
    // Configuration setters
    public void setHealthCheckInterval(long healthCheckInterval) {
        if (healthCheckInterval <= 0) {
            throw new IllegalArgumentException("Health check interval must be positive");
        }
        this.healthCheckInterval = healthCheckInterval;
    }
    
    public void setDetailedReportInterval(long detailedReportInterval) {
        if (detailedReportInterval <= 0) {
            throw new IllegalArgumentException("Detailed report interval must be positive");
        }
        this.detailedReportInterval = detailedReportInterval;
    }
    
    public void setLogHealthStatus(boolean logHealthStatus) {
        this.logHealthStatus = logHealthStatus;
    }
    
    public void setLogDetailedReports(boolean logDetailedReports) {
        this.logDetailedReports = logDetailedReports;
    }
    
    private HealthLevel determineOverallHealth(
            boolean memoryHealthy, boolean performanceHealthy, 
            boolean recoveryHealthy, boolean threadsHealthy) {
        
        int healthyComponents = 0;
        if (memoryHealthy) healthyComponents++;
        if (performanceHealthy) healthyComponents++;
        if (recoveryHealthy) healthyComponents++;
        if (threadsHealthy) healthyComponents++;
        
        if (healthyComponents == 4) return HealthLevel.EXCELLENT;
        if (healthyComponents == 3) return HealthLevel.GOOD;
        if (healthyComponents == 2) return HealthLevel.WARNING;
        if (healthyComponents == 1) return HealthLevel.CRITICAL;
        return HealthLevel.EMERGENCY;
    }
    
    private void logHealthStatus(HealthStatus healthStatus) {
        LOGGER.info("Multithreading health check: {} - Memory: {}, Performance: {}, Recovery: {}, Threads: {}",
                healthStatus.getOverallHealth(),
                healthStatus.isMemoryHealthy() ? "HEALTHY" : "UNHEALTHY",
                healthStatus.isPerformanceHealthy() ? "HEALTHY" : "UNHEALTHY",
                healthStatus.isRecoveryHealthy() ? "HEALTHY" : "UNHEALTHY",
                healthStatus.isThreadsHealthy() ? "HEALTHY" : "UNHEALTHY");
    }
    
    private void logDetailedReport(MonitoringReport report) {
        LOGGER.info("=== Multithreading Detailed Report ===");
        LOGGER.info("Memory: {:.1%} used ({} MB / {} MB)",
                report.getMemoryStatistics().getMemoryUsagePercentage(),
                report.getMemoryStatistics().getUsedMemoryAfterGC() / (1024 * 1024),
                report.getMemoryStatistics().getMaxMemory() / (1024 * 1024));
        LOGGER.info("Performance: {:.2f} calc/sec, {:.2f} moves/sec, {:.2f}% efficiency",
                report.getPerformanceStatistics().getCalculationsPerSecond(),
                report.getPerformanceStatistics().getMovesPerSecond(),
                report.getPerformanceStatistics().getOverallEfficiency());
        LOGGER.info("Threads: {} current, {} target",
                report.getAdjustmentStatistics().getCurrentThreads(),
                report.getAdjustmentStatistics().getTargetThreads());
        LOGGER.info("Errors: {} total, {} recovery attempts",
                report.getRecoveryStatistics().getErrorCount(),
                report.getRecoveryStatistics().getRecoveryAttempts());
        LOGGER.info("=====================================");
    }
    
    /**
     * Health level enumeration.
     */
    public enum HealthLevel {
        EXCELLENT,
        GOOD,
        WARNING,
        CRITICAL,
        EMERGENCY
    }
    
    /**
     * Health status container.
     */
    public static class HealthStatus {
        private final HealthLevel overallHealth;
        private final MemoryMonitor.MemoryPressureLevel memoryPressure;
        private final boolean memoryHealthy;
        private final boolean performanceHealthy;
        private final boolean recoveryHealthy;
        private final boolean threadsHealthy;
        private final int errorCount;
        private final boolean threadAdjustmentNeeded;
        
        public HealthStatus(
                HealthLevel overallHealth,
                MemoryMonitor.MemoryPressureLevel memoryPressure,
                boolean memoryHealthy,
                boolean performanceHealthy,
                boolean recoveryHealthy,
                boolean threadsHealthy,
                int errorCount,
                boolean threadAdjustmentNeeded) {
            this.overallHealth = overallHealth;
            this.memoryPressure = memoryPressure;
            this.memoryHealthy = memoryHealthy;
            this.performanceHealthy = performanceHealthy;
            this.recoveryHealthy = recoveryHealthy;
            this.threadsHealthy = threadsHealthy;
            this.errorCount = errorCount;
            this.threadAdjustmentNeeded = threadAdjustmentNeeded;
        }
        
        // Getters
        public HealthLevel getOverallHealth() { return overallHealth; }
        public MemoryMonitor.MemoryPressureLevel getMemoryPressure() { return memoryPressure; }
        public boolean isMemoryHealthy() { return memoryHealthy; }
        public boolean isPerformanceHealthy() { return performanceHealthy; }
        public boolean isRecoveryHealthy() { return recoveryHealthy; }
        public boolean isThreadsHealthy() { return threadsHealthy; }
        public int getErrorCount() { return errorCount; }
        public boolean isErrorRecoveryNeeded() { return errorCount > 0; }
        public boolean isThreadAdjustmentNeeded() { return threadAdjustmentNeeded; }
    }
    
    /**
     * Monitoring report container.
     */
    public static class MonitoringReport {
        private final MemoryMonitor.MemoryStatistics memoryStatistics;
        private final MemoryMonitor.MemoryPressureStatistics pressureStatistics;
        private final PerformanceMetrics.PerformanceStatistics performanceStatistics;
        private final ErrorRecoveryManager.RecoveryStatistics recoveryStatistics;
        private final AdaptiveThreadPoolManager.AdjustmentStatistics adjustmentStatistics;
        private final long timestamp;
        
        public MonitoringReport(
                MemoryMonitor.MemoryStatistics memoryStatistics,
                MemoryMonitor.MemoryPressureStatistics pressureStatistics,
                PerformanceMetrics.PerformanceStatistics performanceStatistics,
                ErrorRecoveryManager.RecoveryStatistics recoveryStatistics,
                AdaptiveThreadPoolManager.AdjustmentStatistics adjustmentStatistics,
                long timestamp) {
            this.memoryStatistics = memoryStatistics;
            this.pressureStatistics = pressureStatistics;
            this.performanceStatistics = performanceStatistics;
            this.recoveryStatistics = recoveryStatistics;
            this.adjustmentStatistics = adjustmentStatistics;
            this.timestamp = timestamp;
        }
        
        // Getters
        public MemoryMonitor.MemoryStatistics getMemoryStatistics() { return memoryStatistics; }
        public MemoryMonitor.MemoryPressureStatistics getPressureStatistics() { return pressureStatistics; }
        public PerformanceMetrics.PerformanceStatistics getPerformanceStatistics() { return performanceStatistics; }
        public ErrorRecoveryManager.RecoveryStatistics getRecoveryStatistics() { return recoveryStatistics; }
        public AdaptiveThreadPoolManager.AdjustmentStatistics getAdjustmentStatistics() { return adjustmentStatistics; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Performance suggestions container.
     */
    public static class PerformanceSuggestions {
        private final String[] suggestions;
        
        private PerformanceSuggestions(String[] suggestions) {
            this.suggestions = suggestions;
        }
        
        public String[] getSuggestions() {
            return suggestions;
        }
        
        public boolean hasSuggestions() {
            return suggestions.length > 0;
        }
        
        @Override
        public String toString() {
            if (suggestions.length == 0) {
                return "No performance suggestions available.";
            }
            
            StringBuilder sb = new StringBuilder("Performance Suggestions:\n");
            for (String suggestion : suggestions) {
                sb.append("- ").append(suggestion).append("\n");
            }
            return sb.toString();
        }
        
        public static class Builder {
            private final java.util.List<String> suggestions = new java.util.ArrayList<>();
            
            public Builder addSuggestion(String suggestion) {
                suggestions.add(suggestion);
                return this;
            }
            
            public PerformanceSuggestions build() {
                return new PerformanceSuggestions(suggestions.toArray(new String[0]));
            }
        }
    }
}