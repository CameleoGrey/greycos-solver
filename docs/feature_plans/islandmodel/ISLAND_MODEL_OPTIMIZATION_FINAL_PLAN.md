# Island Model Optimization - Final Implementation Plan

## Executive Summary

This document provides the final implementation plan for optimizing the island model in Greycos. The plan focuses on three high-impact, low-risk optimizations:

1. **Reduce Lock Contention**: Double-Checked Locking with Volatile
2. **Reduce Solution Cloning**: Solution Reference Sharing with Copy-on-Write
3. **Optimize Migration**: Non-blocking Migration Channels + Remove Dead Agent Forwarding

**Expected Overall Impact**: 40-60% performance improvement with minimal risk.

**Note**: The plan assumes that all observers clone solutions when they need to modify them. This assumption must be verified during implementation.

---

## 1. Selected Optimizations Overview

### 1.1 Optimization 1: Double-Checked Locking with Volatile

**Goal**: Reduce lock contention on [`SharedGlobalState.tryUpdate()`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/SharedGlobalState.java:33-54)

**Current Problem**:
- All agents compete for the same lock on every update attempt
- Deep cloning happens inside synchronized block
- Most update attempts fail (solution not better)
- Lock contention scales poorly with island count

**Solution**: Use double-checked locking pattern with volatile reads
- Fast path: check without lock for most failed updates
- Slow path: acquire lock only for potential improvements
- Eliminates cloning from critical section
- **Critical**: Use `volatile` keyword for `bestScore` field to ensure visibility across threads

**Expected Improvement**: 60-80% reduction in failed update contention

**Complexity**: LOW
**Risk**: LOW
**Timeline**: 3-5 days

---

### 1.2 Optimization 2: Reduce Solution Cloning - Reference Sharing

**Goal**: Eliminate unnecessary solution cloning in global best updates

**Current Problem**:
- Solutions are cloned inside synchronized lock in [`SharedGlobalState.tryUpdate()`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/SharedGlobalState.java:39,47)
- Cloning is expensive for large solutions (O(n) where n = solution size)
- Lock hold time extended by cloning operation
- Memory bandwidth wasted on unnecessary copies

**Solution**: Solution Reference Sharing with Copy-on-Write
- Store solution reference instead of clone in SharedGlobalState
- Observers clone solutions when they need to modify them
- Eliminates cloning from critical section
- Reduces memory bandwidth pressure

**Expected Improvement**: 30-40% reduction in cloning overhead, 20-30% reduction in lock contention

**Complexity**: LOW
**Risk**: LOW (observers already clone when needed)
**Timeline**: 2-3 days

---

### 1.3 Optimization 3: Non-blocking Migration + Remove Dead Agent Forwarding

**Goal**: Eliminate unnecessary blocking and wasted CPU cycles

**Current Problems**:
- Agents block indefinitely during migration
- Dead agents continue forwarding messages
- Reduced parallelism and wasted resources

**Solutions**:
- Non-blocking migration channels with timeout
- Dead agents skip migration entirely
- Better parallelism and faster termination

**Status**: Already implemented - verify current implementation matches requirements

**Expected Improvement**: 10-20% reduction in migration blocking time, 5-10% reduction in dead agent overhead

**Complexity**: LOW
**Risk**: LOW
**Timeline**: 1-2 days (verification and testing)

---

## 2. Detailed Implementation Plan

### 2.1 Phase 1: Reduce Lock Contention (3-5 days)

#### Day 1: Design and Code Review
- Review current [`SharedGlobalState`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/SharedGlobalState.java) implementation
- Identify all lock acquisition points
- Design double-checked locking implementation

#### Day 2: Implement Double-Checked Locking
**File**: `core/src/main/java/ai/greycos/solver/core/impl/islandmodel/SharedGlobalState.java`

**Changes**:
```java
// Field declaration must be volatile for correct double-checked locking
private volatile Score<?> bestScore;
private Solution_ bestSolution;

public boolean tryUpdate(Solution_ candidate, Score<?> candidateScore) {
    Objects.requireNonNull(candidate, "Candidate solution cannot be null");
    Objects.requireNonNull(candidateScore, "Candidate score cannot be null");

    // Fast path: check if update is needed without lock
    // Volatile read ensures visibility of latest bestScore
    Score<?> currentBest = bestScore;
    if (currentBest != null) {
        @SuppressWarnings("unchecked")
        int comparison = ((Score) candidateScore).compareTo((Score) currentBest);
        if (comparison <= 0) {
            return false;  // Not better, skip entirely
        }
    }

    // Slow path: acquire lock and double-check
    synchronized (lock) {
        // Double-check in case another thread updated while waiting
        currentBest = bestScore;
        if (currentBest != null) {
            @SuppressWarnings("unchecked")
            int comparison = ((Score) candidateScore).compareTo((Score) currentBest);
            if (comparison <= 0) {
                return false;  // Lost race, not better anymore
            }
        }

        // Update - no cloning, pass reference directly
        bestScore = candidateScore;
        bestSolution = candidate;  // Store reference, don't clone
        notifyObservers(candidate);
        return true;
    }
}
```

**Key Points**:
- Remove `deepClone()` calls from synchronized block
- Store solution reference instead of clone
- Observers already clone when needed (e.g., [`GlobalBestPropagator`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/GlobalBestPropagator.java:91))

#### Day 3: Update Observers
**File**: `core/src/main/java/ai/greycos/solver/core/impl/islandmodel/GlobalBestPropagator.java`

**Changes**:
```java
private void updateMainSolverScope(Solution_ newBestSolution, Score<?> newBestScore) {
    // Clone here, outside the lock (already done in GlobalBestPropagator)
    var clonedSolution = mainSolverScope.getScoreDirector().cloneSolution(newBestSolution);
    mainSolverScope.setBestSolution(clonedSolution);
    mainSolverScope.setBestScore(newBestScore);
    // ... rest of update logic
}
```

**Note**: Verify that all observers clone solutions before modification.

---

### 2.2 Phase 2: Reduce Solution Cloning - Reference Sharing (2-3 days)

#### Day 1: Design and Code Review
- Review current [`SharedGlobalState`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/SharedGlobalState.java) implementation
- Identify all cloning points
- Review observer implementations ([`GlobalBestPropagator`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/GlobalBestPropagator.java))
- Design reference sharing approach
- Verify observers already clone when needed

#### Day 2: Implement Reference Sharing in SharedGlobalState
**File**: `core/src/main/java/ai/greycos/solver/core/impl/islandmodel/SharedGlobalState.java`

**Changes**:
```java
// Field declaration must be volatile for correct double-checked locking
private volatile Score<?> bestScore;
private Solution_ bestSolution;

public boolean tryUpdate(Solution_ candidate, Score<?> candidateScore) {
    Objects.requireNonNull(candidate, "Candidate solution cannot be null");
    Objects.requireNonNull(candidateScore, "Candidate score cannot be null");

    // Fast path: check if update is needed without lock
    // Volatile read ensures visibility of latest bestScore
    Score<?> currentBest = bestScore;
    if (currentBest != null) {
        @SuppressWarnings("unchecked")
        int comparison = ((Score) candidateScore).compareTo((Score) currentBest);
        if (comparison <= 0) {
            return false;  // Not better, skip entirely
        }
    }

    // Slow path: acquire lock and double-check
    synchronized (lock) {
        // Double-check in case another thread updated while waiting
        currentBest = bestScore;
        if (currentBest != null) {
            @SuppressWarnings("unchecked")
            int comparison = ((Score) candidateScore).compareTo((Score) currentBest);
            if (comparison <= 0) {
                return false;  // Lost race, not better anymore
            }
        }

        // Update - store reference, don't clone
        bestScore = candidateScore;
        bestSolution = candidate;  // Store reference, don't clone
        notifyObservers(candidate);
        return true;
    }
}
```

**Key Points**:
- Remove `deepClone()` calls entirely
- Store solution reference directly
- **Critical assumption**: Observers clone solutions when they need to modify them
- Must audit all observers to verify they clone before modification
- Example: [`GlobalBestPropagator.updateMainSolverScope()`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/GlobalBestPropagator.java:91)

---

### 2.3 Phase 3: Non-blocking Migration Verification (1-2 days)

#### Day 1: Verify Current Implementation
- Review [`BoundedChannel`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/BoundedChannel.java) implementation
- Verify non-blocking API exists (timeout-based send/receive)
- Review dead agent forwarding logic in [`IslandAgent`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/IslandAgent.java)
- Check if dead agents skip migration

**Verification Checklist**:
- [ ] BoundedChannel has `send(T message, long timeout, TimeUnit unit)` method
- [ ] BoundedChannel has `tryReceive(long timeout, TimeUnit unit)` method
- [ ] IslandAgent uses timeout-based migration
- [ ] Dead agents skip sendMigration()
- [ ] Dead agents skip receiveMigration() processing

**Expected Implementation**:
```java
// In BoundedChannel
public boolean send(T message, long timeout, TimeUnit unit) throws InterruptedException {
    return queue.offer(message, timeout, unit);  // Timeout-based
}

public T tryReceive(long timeout, TimeUnit unit) throws InterruptedException {
    return queue.poll(timeout, unit);  // Timeout-based
}

// In IslandAgent
private void sendMigration() throws InterruptedException {
    if (status == AgentStatus.DEAD) {
        return;  // Skip for dead agents
    }
    // ... send with timeout ...
}

private void receiveMigration() throws InterruptedException {
    AgentUpdate<Solution_> update = receiver.tryReceive(100, TimeUnit.MILLISECONDS);
    if (update == null) {
        return;  // Timeout, skip this cycle
    }
    
    if (status == AgentStatus.DEAD) {
        return;  // Skip processing for dead agents
    }
    // ... process migrant ...
}
```

---

**Key Dependencies**:
- Observer audit must complete before Phase 2 deployment
- Non-blocking migration verification must complete before Phase 3
- All phases require comprehensive testing before final deployment
