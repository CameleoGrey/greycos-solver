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
    
    // Error thresholds and recovery parameters
    private static final int MAX_ERROR_COUNT = 10;
    private static final long ERROR_RESET_INTERVAL = 60000; // 60 seconds
    private static final long RECOVERY_DELAY = 1000; // 1 second
    private static final int MAX_RECOVERY_ATTEMPTS = 3;
    
    // State
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicLong lastErrorTime = new AtomicLong(0);
    private final AtomicLong lastRecoveryTime = new AtomicLong(0);
    private final AtomicInteger recoveryAttempts = new AtomicInteger(0);
    private final AtomicReference<RecoveryState> recoveryState = new AtomicReference<>(RecoveryState.NORMAL);
    
    // Configuration
    private volatile int maxErrorCount = MAX_ERROR_COUNT;
    private volatile long errorResetInterval = ERROR_RESET_INTERVAL;
    private volatile long recoveryDelay = RECOVERY_DELAY;
    private volatile int maxRecoveryAttempts = MAX_RECOVERY_ATTEMPTS;
    
    // Recovery listeners
    private volatile ErrorRecoveryListener recoveryListener;
    
    public ErrorRecoveryManager() {
        // Initialize with default values
    }
    
    /**
     * Records an error occurrence and determines if recovery is needed.
     * 
     * @param error the error that occurred
     * @param threadIndex index of the thread where the error occurred
     * @return true if recovery action is needed
     */
    public boolean recordError(Throwable error, int threadIndex) {
        long currentTime = System.currentTimeMillis();
        lastErrorTime.set(currentTime);
        
        int currentErrorCount = errorCount.incrementAndGet();
        
        LOGGER.warn("Error in move thread {}: {} - Error count: {}/{}",
                threadIndex, error.getMessage(), currentErrorCount, maxErrorCount);
        
        // Check if we need to trigger recovery
        if (currentErrorCount >= maxErrorCount) {
            return triggerRecovery(error, threadIndex);
        }
        
        return false;
    }
    
    /**
     * Triggers the recovery process.
     * 
     * @param error the triggering error
     * @param threadIndex index of the thread where the error occurred
     * @return true if recovery was triggered
     */
    private boolean triggerRecovery(Throwable error, int threadIndex) {
        RecoveryState currentState = recoveryState.get();
        
        if (currentState == RecoveryState.RECOVERING || currentState == RecoveryState.FAILED) {
            LOGGER.debug("Recovery already in progress, ignoring additional error");
            return false;
        }
        
        int attempts = recoveryAttempts.incrementAndGet();
        
        if (attempts > maxRecoveryAttempts) {
            LOGGER.error("Maximum recovery attempts ({}) exceeded, marking as failed",
                    maxRecoveryAttempts);
            recoveryState.set(RecoveryState.FAILED);
            notifyRecoveryFailed(error, threadIndex);
            return true;
        }
        
        LOGGER.warn("Triggering recovery attempt {} for error in thread {}",
                attempts, threadIndex);
        
        recoveryState.set(RecoveryState.RECOVERING);
        
        // Schedule recovery after delay
        scheduleRecovery(error, threadIndex);
        
        return true;
    }
    
    /**
     * Schedules the actual recovery process.
     */
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
    
    /**
     * Performs the actual recovery process.
     */
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
    
    /**
     * Checks if the system is in a recoverable state.
     * 
     * @return true if the system can attempt recovery
     */
    public boolean isRecoverable() {
        RecoveryState state = recoveryState.get();
        if (state == RecoveryState.FAILED) {
            return false;
        }
        
        // Check if enough time has passed since last recovery
        long timeSinceLastRecovery = System.currentTimeMillis() - lastRecoveryTime.get();
        return timeSinceLastRecovery > errorResetInterval;
    }
    
    /**
     * Resets the error count if enough time has passed since the last error.
     */
    public void checkAndResetErrorCount() {
        long timeSinceLastError = System.currentTimeMillis() - lastErrorTime.get();
        if (timeSinceLastError > errorResetInterval) {
            int previousCount = errorCount.getAndSet(0);
            if (previousCount > 0) {
                LOGGER.debug("Resetting error count after {}ms of inactivity", timeSinceLastError);
            }
        }
    }
    
    /**
     * Forces a recovery state reset.
     */
    public void resetRecoveryState() {
        errorCount.set(0);
        recoveryAttempts.set(0);
        recoveryState.set(RecoveryState.NORMAL);
        lastErrorTime.set(0);
        lastRecoveryTime.set(0);
        
        LOGGER.info("Recovery state reset");
    }
    
    /**
     * Gets current recovery statistics.
     * 
     * @return RecoveryStatistics with current state information
     */
    public RecoveryStatistics getRecoveryStatistics() {
        return new RecoveryStatistics(
                errorCount.get(),
                recoveryAttempts.get(),
                recoveryState.get(),
                lastErrorTime.get(),
                lastRecoveryTime.get(),
                System.currentTimeMillis()
        );
    }
    
    // Configuration setters
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
    
    /**
     * Sets the recovery listener for notifications.
     * 
     * @param listener the recovery listener
     */
    public void setRecoveryListener(ErrorRecoveryListener listener) {
        this.recoveryListener = listener;
    }
    
    /**
     * Recovery state enumeration.
     */
    public enum RecoveryState {
        NORMAL,
        RECOVERING,
        FAILED
    }
    
    /**
     * Recovery statistics container.
     */
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
        
        // Getters
        public int getErrorCount() { return errorCount; }
        public int getRecoveryAttempts() { return recoveryAttempts; }
        public RecoveryState getRecoveryState() { return recoveryState; }
        public long getLastErrorTime() { return lastErrorTime; }
        public long getLastRecoveryTime() { return lastRecoveryTime; }
        public long getCurrentTime() { return currentTime; }
        public long getTimeSinceLastError() { return currentTime - lastErrorTime; }
        public long getTimeSinceLastRecovery() { return currentTime - lastRecoveryTime; }
        
        @Override
        public String toString() {
            return String.format(
                    "RecoveryStatistics{errorCount=%d, recoveryAttempts=%d, state=%s, " +
                    "timeSinceLastError=%dms, timeSinceLastRecovery=%dms}",
                    errorCount, recoveryAttempts, recoveryState,
                    getTimeSinceLastError(), getTimeSinceLastRecovery()
            );
        }
    }
    
    /**
     * Recovery listener interface for notifications.
     */
    public interface ErrorRecoveryListener {
        /**
         * Called when recovery process starts.
         * 
         * @param error the error that triggered recovery
         * @param threadIndex index of the affected thread
         */
        void onRecoveryStarted(Throwable error, int threadIndex);
        
        /**
         * Called when recovery process completes successfully.
         * 
         * @param threadIndex index of the thread that was recovered
         */
        void onRecoveryCompleted(int threadIndex);
        
        /**
         * Called when recovery process fails.
         * 
         * @param error the error that caused recovery failure
         * @param threadIndex index of the affected thread
         */
        void onRecoveryFailed(Throwable error, int threadIndex);
    }
}