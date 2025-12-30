# Island Model Logic Update Plan

## Executive Summary

This document outlines the plan to update the island model logic to implement a more efficient global best synchronization mechanism. The key change is that islands will only update the global best when they find their own new best solution, rather than updating on every step. The `compareGlobalFrequency` parameter will be renamed to `receiveGlobalUpdateFrequency` to better reflect its purpose.

**Status**: Ready for implementation

**Key Changes**:
- Islands update global best ONLY when they find a new local best
- Islands continue working with their local best solution
- Rename `compareGlobalFrequency` to `receiveGlobalUpdateFrequency`
- Maintain backward compatibility with existing configurations

---

## Table of Contents

1. [Background & Motivation](#background--motivation)
2. [Current State Analysis](#current-state-analysis)
3. [Problem Statement](#problem-statement)
4. [Target Architecture](#target-architecture)
5. [Implementation Strategy](#implementation-strategy)
6. [Detailed Design](#detailed-design)
7. [Configuration Changes](#configuration-changes)
8. [Testing Strategy](#testing-strategy)
9. [Migration Guide](#migration-guide)
10. [Risk Mitigation](#risk-mitigation)

---

## Background & Motivation

### Current Behavior

In the current implementation:

1. **GlobalBestUpdater** updates the global best on EVERY step (lines 31-42 in [`GlobalBestUpdater.java`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/GlobalBestUpdater.java:31-42))
2. **GlobalCompareListener** checks the global best periodically based on `compareGlobalFrequency` and adopts if better
3. This causes unnecessary contention on the `SharedGlobalState` lock, even when no improvement was made

### Issues with Current Approach

1. **Performance Overhead**: Updating global best on every step causes unnecessary lock contention
2. **Redundant Updates**: Most steps don't improve the local best, so global updates are wasted
3. **Misleading Parameter Name**: `compareGlobalFrequency` suggests comparison frequency, but it's actually the frequency of receiving/adopting global updates

### Proposed Solution

1. **Optimize Global Updates**: Only update global best when an island finds a new local best
2. **Clarify Parameter Semantics**: Rename `compareGlobalFrequency` to `receiveGlobalUpdateFrequency`
3. **Maintain Local Autonomy**: Islands continue working with their local best solution
4. **Preserve Benefits**: Keep the benefit of periodic global best adoption for faster convergence

---

## Current State Analysis

### Existing Components

#### GlobalBestUpdater.java

**Current Implementation** (lines 30-43):
```java
@Override
public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    var phaseScope = stepScope.getPhaseScope();
    var solverScope = phaseScope.getSolverScope();

    // Update global state with the agent's best solution
    var bestSolution = solverScope.getBestSolution();
    var bestScore = solverScope.getBestScore();

    if (bestSolution != null && bestScore != null) {
        globalState.tryUpdate(bestSolution, bestScore.raw());
    }
}
```

**Issues**:
- Updates global best on every step, regardless of whether the local best improved
- Causes unnecessary lock contention
- No check for whether the local best actually changed

#### GlobalCompareListener.java

**Current Implementation** (lines 49-67):
```java
@Override
public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    if (!config.isCompareGlobalEnabled()) {
        return;
    }

    if (!(stepScope instanceof LocalSearchStepScope)) {
        return;
    }

    var localSearchStepScope = (LocalSearchStepScope) stepScope;

    stepsUntilNextCheck--;

    if (stepsUntilNextCheck <= 0) {
        checkAndAdoptGlobalBest(localSearchStepScope);
        stepsUntilNextCheck = config.getCompareGlobalFrequency();
    }
}
```

**Behavior**:
- Checks global best every `compareGlobalFrequency` steps
- Adopts global best if it's better than local best
- Works correctly, but parameter name is misleading

#### IslandModelConfig.java

**Current Fields** (lines 17-24):
```java
public static final int DEFAULT_COMPARE_GLOBAL_FREQUENCY = 50;

private boolean compareGlobalEnabled = true;
private int compareGlobalFrequency = DEFAULT_COMPARE_GLOBAL_FREQUENCY;
```

**Issues**:
- Field name `compareGlobalFrequency` is misleading
- Should be `receiveGlobalUpdateFrequency` to reflect actual behavior

---

## Problem Statement

### Functional Requirements

1. **Optimized Global Updates**: Islands should only update global best when they find a new local best solution
2. **Maintain Local Autonomy**: Islands continue working with their local best solution
3. **Periodic Global Adoption**: Islands periodically check and adopt global best if better
4. **Clear Parameter Semantics**: Parameter names should accurately reflect their purpose

### Non-Functional Requirements

1. **Performance**: Reduce lock contention on `SharedGlobalState`
2. **Backward Compatibility**: Existing configurations should continue to work
3. **Code Clarity**: Code should be easy to understand and maintain
4. **Minimal Changes**: Make the smallest possible changes to achieve the goal

---

## Target Architecture

### High-Level Design

```
┌─────────────────────────────────────────────────────────────┐
│                    SharedGlobalState                    │
│  - bestSolution (volatile)                             │
│  - bestScore (volatile)                               │
│  - tryUpdate() (synchronized)                          │
│  - getBestSolution() (volatile read)                    │
└─────────────────────────────────────────────────────────────┘
             ▲
             │ tryUpdate() ONLY when island finds new local best
             │ getBestSolution() for periodic adoption
             │
┌─────────────────────────────────────────────────────────────┐
│                   IslandAgent                          │
│  - phases: List<Phase>                                 │
│  - globalState: SharedGlobalState                        │
│  - config: IslandModelConfig                            │
│                                                        │
│  run():                                                 │
│    for (Phase phase : phases) {                          │
│      attachGlobalBestUpdater(phase) ◄─── MODIFIED         │
│      attachGlobalCompareListener(phase) ◄─── RENAMED      │
│      phase.solve(islandScope)                             │
│    }                                                     │
│                                                        │
│  Global Best Updater: ◄─── MODIFIED                      │
│    - Updates global best ONLY when local best improves   │
│    - Reduces lock contention                             │
│                                                        │
│  Global Compare Listener: ◄─── RENAMED PARAMETER         │
│    - Receives global best updates periodically            │
│    - Adopts global best if better                        │
│    - Frequency: receiveGlobalUpdateFrequency            │
└─────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

#### 1. Update Global Best Only on Local Improvement

**Decision**: Modify `GlobalBestUpdater` to only update global best when the island's local best improves.

**Rationale**:
- Most steps don't improve the local best
- Reduces lock contention on `SharedGlobalState`
- Maintains correctness (global best is always the best across all islands)
- Aligns with the principle of "push when improved, pull periodically"

**Implementation**:
- Track previous best score in `GlobalBestUpdater`
- Only call `globalState.tryUpdate()` when best score improves
- Use `stepScope.getBestScoreImproved()` to detect improvements

#### 2. Rename Parameter for Clarity

**Decision**: Rename `compareGlobalFrequency` to `receiveGlobalUpdateFrequency`.

**Rationale**:
- `compareGlobalFrequency` suggests comparison frequency, but it's actually the frequency of receiving/adopting global updates
- `receiveGlobalUpdateFrequency` accurately reflects the behavior
- Improves code readability and reduces confusion

**Implementation**:
- Add new parameter `receiveGlobalUpdateFrequency` to `IslandModelConfig`
- Keep old parameter `compareGlobalFrequency` for backward compatibility
- Deprecate old parameter with clear migration path
- Update documentation and examples

#### 3. Maintain Backward Compatibility

**Decision**: Support old parameter names during transition period.

**Rationale**:
- Existing configurations should continue to work
- Users can migrate at their own pace
- No breaking changes in this release

**Implementation**:
- Keep old getters/setters with `@Deprecated` annotation
- Map old parameter values to new parameter values in factory
- Add warnings in logs when deprecated parameters are used
- Document migration path clearly

---

## Implementation Strategy

### Approach: Incremental Refactoring

Implement changes in phases to minimize risk and ensure correctness.

### Phase 1: Modify GlobalBestUpdater (Days 1-2)

**Goal**: Update `GlobalBestUpdater` to only update global best when local best improves.

**Tasks**:

1. **Add Previous Best Score Tracking**
   - Add `private Score<?> previousBestScore` field to `GlobalBestUpdater`
   - Initialize in constructor with `null`

2. **Modify stepEnded() Method**
   - Check `stepScope.getBestScoreImproved()` before updating
   - Compare current best score with previous best score
   - Only call `globalState.tryUpdate()` when improvement detected
   - Update `previousBestScore` after successful update

3. **Add Logging**
   - Log when global best is updated (with scores)
   - Log when update is skipped (no improvement)

**Deliverables**:
- Modified `GlobalBestUpdater` class
- Unit tests for new behavior
- Performance benchmarks showing reduced contention

### Phase 2: Rename Configuration Parameter (Days 3-4)

**Goal**: Rename `compareGlobalFrequency` to `receiveGlobalUpdateFrequency`.

**Tasks**:

1. **Update IslandModelConfig**
   - Add `receiveGlobalUpdateFrequency` field with default value
   - Add getter/setter/builder methods for new field
   - Deprecate `compareGlobalFrequency` with `@Deprecated` annotation
   - Add migration logic: if old field is set, copy to new field

2. **Update IslandModelPhaseConfig**
   - Add `receiveGlobalUpdateFrequency` field (nullable)
   - Deprecate `compareGlobalFrequency` field
   - Update `inherit()` method to handle both fields
   - Add migration logic in factory

3. **Update DefaultIslandModelPhaseFactory**
   - Read new parameter from config
   - Fall back to old parameter if new parameter is null
   - Log warning when deprecated parameter is used
   - Pass new parameter to `IslandModelConfig`

4. **Update GlobalCompareListener**
   - Update constructor to use new parameter name
   - Update field name to `stepsUntilNextReceive`
   - Update comments and documentation

**Deliverables**:
- Updated configuration classes
- Updated factory class
- Updated listener class
- Configuration migration tests

### Phase 3: Update Documentation (Days 5)

**Goal**: Update all documentation to reflect new behavior and parameter names.

**Tasks**:

1. **Update Javadoc**
   - Update `GlobalBestUpdater` class documentation
   - Update `GlobalCompareListener` class documentation
   - Update `IslandModelConfig` class documentation
   - Update `IslandModelPhaseConfig` class documentation

2. **Update User Documentation**
   - Update island model documentation
   - Add migration guide for parameter rename
   - Update configuration examples
   - Add performance tuning recommendations

3. **Update Implementation Plan**
   - Update this plan with actual implementation details
   - Document any deviations from original plan
   - Add lessons learned

**Deliverables**:
- Updated Javadoc
- Updated user documentation
- Migration guide

### Phase 4: Testing & Validation (Days 6-7)

**Goal**: Comprehensive testing to ensure correctness and performance improvements.

**Tasks**:

1. **Unit Tests**
   - Test `GlobalBestUpdater` only updates on improvement
   - Test configuration parameter migration
   - Test deprecated parameter handling
   - Test listener frequency logic

2. **Integration Tests**
   - Test with multiple islands
   - Test with various local search algorithms
   - Test with migration enabled/disabled
   - Test with different frequency values

3. **Performance Tests**
   - Measure lock contention before and after
   - Measure throughput (steps per second)
   - Measure convergence speed
   - Compare with baseline (current implementation)

4. **Regression Tests**
   - Run all existing island model tests
   - Run all existing local search tests
   - Ensure no breaking changes

**Deliverables**:
- Comprehensive test suite
- Performance benchmarks
- Test report

---

## Detailed Design

### Modified GlobalBestUpdater

```java
package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.api.score.Score;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle listener that updates global best state when an agent finds a better solution.
 * 
 * <p>Unlike the original implementation that updated global best on every step,
 * this optimized version only updates global best when the island's local best
 * improves. This reduces lock contention on SharedGlobalState while maintaining
 * correctness.
 *
 * <p>This listener complements GlobalCompareListener:
 * <ul>
 *   <li>GlobalBestUpdater: Pushes local improvements to global state (only when improved)
 *   <li>GlobalCompareListener: Pulls global improvements to local agent (periodically)
 * </ul>
 *
 * @param <Solution_> solution type
 */
public class GlobalBestUpdater<Solution_> extends PhaseLifecycleListenerAdapter<Solution_> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalBestUpdater.class);

    private final SharedGlobalState<Solution_> globalState;
    private final int agentId;
    private Score<?> previousBestScore;

    public GlobalBestUpdater(SharedGlobalState<Solution_> globalState, int agentId) {
        this.globalState = globalState;
        this.agentId = agentId;
        this.previousBestScore = null;
    }

    @Override
    public void stepEnded(AbstractStepScope<Solution_> stepScope) {
        var phaseScope = stepScope.getPhaseScope();
        var solverScope = phaseScope.getSolverScope();

        // Get current best solution and score
        var bestSolution = solverScope.getBestSolution();
        var bestScore = solverScope.getBestScore();

        if (bestSolution == null || bestScore == null) {
            return;
        }

        // Only update global best if local best improved
        boolean shouldUpdate = shouldUpdateGlobalBest(stepScope, bestScore);
        
        if (shouldUpdate) {
            boolean updated = globalState.tryUpdate(bestSolution, bestScore.raw());
            if (updated) {
                LOGGER.debug("Agent {} updated global best (score: {})", 
                           agentId, bestScore.raw());
                previousBestScore = bestScore.raw();
            }
        }
    }

    /**
     * Determines whether the global best should be updated.
     * 
     * <p>Updates occur when:
     * <ul>
     *   <li>This is the first step (previousBestScore is null)
     *   <li>The best score improved in this step
     *   <li>The current best score is better than the previous best score
     * </ul>
     *
     * @param stepScope the step scope containing improvement information
     * @param currentBestScore the current best score
     * @return true if global best should be updated, false otherwise
     */
    private boolean shouldUpdateGlobalBest(AbstractStepScope<Solution_> stepScope, 
                                          InnerScore<?> currentBestScore) {
        // First step - always update
        if (previousBestScore == null) {
            return true;
        }

        // Check if best score improved in this step
        Boolean bestScoreImproved = stepScope.getBestScoreImproved();
        if (bestScoreImproved != null && bestScoreImproved) {
            return true;
        }

        // Compare current best with previous best
        // This handles edge cases where bestScoreImproved might be null
        @SuppressWarnings("unchecked")
        var currentScore = (Score) currentBestScore.raw();
        int comparisonResult = currentScore.compareTo((Score) previousBestScore);
        
        return comparisonResult > 0;
    }
}
```

### Updated IslandModelConfig

```java
package ai.greycos.solver.core.impl.islandmodel;

import java.util.Objects;

/**
 * Configuration for the island model phase in Greycos. Controls the number of islands, migration
 * behavior, and global best synchronization.
 */
public class IslandModelConfig {

    /** Default number of islands to use. */
    public static final int DEFAULT_ISLAND_COUNT = 4;

    /** Default frequency of migration (number of steps between migrations). */
    public static final int DEFAULT_MIGRATION_FREQUENCY = 100;

    /**
     * Default frequency of receiving global best updates (number of steps between checks).
     * This is the frequency at which islands check and adopt the global best solution.
     */
    public static final int DEFAULT_RECEIVE_GLOBAL_UPDATE_FREQUENCY = 50;

    private int islandCount = DEFAULT_ISLAND_COUNT;
    private int migrationFrequency = DEFAULT_MIGRATION_FREQUENCY;
    private boolean enabled = false; // Default disabled for backward compatibility
    private boolean compareGlobalEnabled = true; // Default enabled for compare-to-global
    
    // NEW: Receive global update frequency
    private int receiveGlobalUpdateFrequency = DEFAULT_RECEIVE_GLOBAL_UPDATE_FREQUENCY;
    
    // DEPRECATED: Compare global frequency (use receiveGlobalUpdateFrequency instead)
    @Deprecated
    private int compareGlobalFrequency = DEFAULT_RECEIVE_GLOBAL_UPDATE_FREQUENCY;

    public IslandModelConfig() {}

    public IslandModelConfig(int islandCount, int migrationFrequency) {
        setIslandCount(islandCount);
        setMigrationFrequency(migrationFrequency);
    }

    // ... existing getters/setters for islandCount, migrationFrequency, enabled ...

    public boolean isCompareGlobalEnabled() {
        return compareGlobalEnabled;
    }

    public void setCompareGlobalEnabled(boolean compareGlobalEnabled) {
        this.compareGlobalEnabled = compareGlobalEnabled;
    }

    /**
     * Gets the frequency at which islands receive and check global best updates.
     * Islands will check the global best solution every N steps and adopt it if better.
     *
     * @return the receive global update frequency (in steps)
     */
    public int getReceiveGlobalUpdateFrequency() {
        return receiveGlobalUpdateFrequency;
    }

    /**
     * Sets the frequency at which islands receive and check global best updates.
     *
     * @param receiveGlobalUpdateFrequency the frequency (must be at least 1)
     * @throws IllegalArgumentException if frequency is less than 1
     */
    public void setReceiveGlobalUpdateFrequency(int receiveGlobalUpdateFrequency) {
        if (receiveGlobalUpdateFrequency < 1) {
            throw new IllegalArgumentException(
                "Receive global update frequency (" + receiveGlobalUpdateFrequency + ") must be at least 1.");
        }
        this.receiveGlobalUpdateFrequency = receiveGlobalUpdateFrequency;
    }

    /**
     * @deprecated Use {@link #getReceiveGlobalUpdateFrequency()} instead.
     */
    @Deprecated
    public int getCompareGlobalFrequency() {
        return compareGlobalFrequency;
    }

    /**
     * @deprecated Use {@link #setReceiveGlobalUpdateFrequency(int)} instead.
     */
    @Deprecated
    public void setCompareGlobalFrequency(int compareGlobalFrequency) {
        if (compareGlobalFrequency < 1) {
            throw new IllegalArgumentException(
                "Compare global frequency (" + compareGlobalFrequency + ") must be at least 1.");
        }
        this.compareGlobalFrequency = compareGlobalFrequency;
        // Also set new field for compatibility
        this.receiveGlobalUpdateFrequency = compareGlobalFrequency;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int islandCount = DEFAULT_ISLAND_COUNT;
        private int migrationFrequency = DEFAULT_MIGRATION_FREQUENCY;
        private boolean enabled = false;
        private boolean compareGlobalEnabled = true;
        private int receiveGlobalUpdateFrequency = DEFAULT_RECEIVE_GLOBAL_UPDATE_FREQUENCY;
        
        @Deprecated
        private int compareGlobalFrequency = DEFAULT_RECEIVE_GLOBAL_UPDATE_FREQUENCY;

        public Builder withIslandCount(int islandCount) {
            this.islandCount = islandCount;
            return this;
        }

        public Builder withMigrationFrequency(int migrationFrequency) {
            this.migrationFrequency = migrationFrequency;
            return this;
        }

        public Builder withEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder withCompareGlobalEnabled(boolean compareGlobalEnabled) {
            this.compareGlobalEnabled = compareGlobalEnabled;
            return this;
        }

        public Builder withReceiveGlobalUpdateFrequency(int receiveGlobalUpdateFrequency) {
            this.receiveGlobalUpdateFrequency = receiveGlobalUpdateFrequency;
            return this;
        }

        /**
         * @deprecated Use {@link #withReceiveGlobalUpdateFrequency(int)} instead.
         */
        @Deprecated
        public Builder withCompareGlobalFrequency(int compareGlobalFrequency) {
            this.compareGlobalFrequency = compareGlobalFrequency;
            this.receiveGlobalUpdateFrequency = compareGlobalFrequency;
            return this;
        }

        public IslandModelConfig build() {
            IslandModelConfig config = new IslandModelConfig();
            config.setIslandCount(islandCount);
            config.setMigrationFrequency(migrationFrequency);
            config.setEnabled(enabled);
            config.setCompareGlobalEnabled(compareGlobalEnabled);
            config.setReceiveGlobalUpdateFrequency(receiveGlobalUpdateFrequency);
            // Also set deprecated field for compatibility
            config.setCompareGlobalFrequency(compareGlobalFrequency);
            return config;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IslandModelConfig that = (IslandModelConfig) o;
        return islandCount == that.islandCount
            && migrationFrequency == that.migrationFrequency
            && enabled == that.enabled
            && compareGlobalEnabled == that.compareGlobalEnabled
            && receiveGlobalUpdateFrequency == that.receiveGlobalUpdateFrequency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            islandCount,
            migrationFrequency,
            enabled,
            compareGlobalEnabled,
            receiveGlobalUpdateFrequency);
    }

    @Override
    public String toString() {
        return "IslandModelConfig{"
            + "islandCount="
            + islandCount
            + ", migrationFrequency="
            + migrationFrequency
            + ", enabled="
            + enabled
            + ", compareGlobalEnabled="
            + compareGlobalEnabled
            + ", receiveGlobalUpdateFrequency="
            + receiveGlobalUpdateFrequency
            + '}';
    }
}
```

### Updated GlobalCompareListener

```java
package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle listener that checks and adopts global best solution periodically. Attached to local
 * search phases to enable compare-to-global functionality.
 *
 * <p>This listener provides "receive global update" mechanism where agents periodically check the
 * shared global best solution (the best solution found across ALL islands) and adopt it if it's
 * better than their current best. This provides:
 *
 * <ul>
 *   <li>Faster convergence - Global best is immediately available to all agents
 *   <li>Better solution quality - Prevents getting stuck in local optima
 *   <li>Complementary to migration - Migration provides diversity, global comparison provides
 *       intensification
 * </ul>
 *
 * <p>The frequency of checking the global best is controlled by the
 * {@code receiveGlobalUpdateFrequency} parameter. Islands will check the global best every N steps
 * and adopt it if it's better than their local best.
 *
 * <p>This listener only performs meaningful work when attached to local search phases, as it
 * requires access to {@link LocalSearchStepScope}. When attached to other phase types, it will
 * simply do nothing.
 *
 * @param <Solution_> solution type
 */
public class GlobalCompareListener<Solution_> extends PhaseLifecycleListenerAdapter<Solution_> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalCompareListener.class);

    private final SharedGlobalState<Solution_> globalState;
    private final IslandModelConfig config;
    private final int agentId;
    private int stepsUntilNextReceive;

    public GlobalCompareListener(
        SharedGlobalState<Solution_> globalState, IslandModelConfig config, int agentId) {
        this.globalState = globalState;
        this.config = config;
        this.agentId = agentId;
        // Use receiveGlobalUpdateFrequency (or fall back to deprecated compareGlobalFrequency)
        this.stepsUntilNextReceive = getReceiveFrequency(config);
    }

    /**
     * Gets the receive frequency from config, with fallback to deprecated parameter.
     */
    private int getReceiveFrequency(IslandModelConfig config) {
        // Try new parameter first
        int frequency = config.getReceiveGlobalUpdateFrequency();
        if (frequency > 0) {
            return frequency;
        }
        // Fall back to deprecated parameter
        return config.getCompareGlobalFrequency();
    }

    @Override
    public void stepEnded(AbstractStepScope<Solution_> stepScope) {
        if (!config.isCompareGlobalEnabled()) {
            return;
        }

        if (!(stepScope instanceof LocalSearchStepScope)) {
            return;
        }

        var localSearchStepScope = (LocalSearchStepScope) stepScope;

        stepsUntilNextReceive--;

        if (stepsUntilNextReceive <= 0) {
            checkAndAdoptGlobalBest(localSearchStepScope);
            stepsUntilNextReceive = getReceiveFrequency(config);
        }
    }

    private void checkAndAdoptGlobalBest(LocalSearchStepScope<Solution_> stepScope) {
        Solution_ globalBest = globalState.getBestSolution();
        if (globalBest == null) {
            return;
        }

        var phaseScope = stepScope.getPhaseScope();
        var currentInnerScore = phaseScope.getBestScore();
        var globalScore = globalState.getBestScore();

        if (globalScore == null || currentInnerScore == null) {
            return;
        }

        @SuppressWarnings("unchecked")
        var currentScore = (Score) currentInnerScore.raw();
        @SuppressWarnings("unchecked")
        var globalScoreCast = (Score) globalScore;

        int comparisonResult = globalScoreCast.compareTo(currentScore);

        if (comparisonResult > 0) {
            LOGGER.info(
                "Agent {} adopting global best (score: {} vs {})",
                agentId,
                globalScoreCast,
                currentScore);

            Solution_ clonedGlobalBest = deepClone(globalBest, stepScope);

            replaceCurrentSolution(clonedGlobalBest, stepScope);

            updateGlobalBest(stepScope);
        }
    }

    @SuppressWarnings("unchecked")
    private Solution_ deepClone(Solution_ solution, LocalSearchStepScope<Solution_> stepScope) {
        if (solution == null) {
            return null;
        }
        return stepScope.getScoreDirector().cloneSolution(solution);
    }

    private void replaceCurrentSolution(
        Solution_ newSolution, LocalSearchStepScope<Solution_> stepScope) {
        var phaseScope = stepScope.getPhaseScope();
        var solverScope = phaseScope.getSolverScope();

        solverScope.getScoreDirector().setWorkingSolution(newSolution);

        solverScope.setBestSolution(solverScope.getScoreDirector().cloneSolution(newSolution));

        var newBestScore = solverScope.getScoreDirector().calculateScore();
        solverScope.setBestScore(newBestScore);

        @SuppressWarnings("unchecked")
        var scoreToSet = (Score) newBestScore.raw();
        solverScope.getScoreDirector().getSolutionDescriptor().setScore(newSolution, scoreToSet);

        // Update step scope's score to reflect the new best solution
        stepScope.setScore(newBestScore);
        // Mark that the best score was improved in this step (for logging)
        stepScope.setBestScoreImproved(true);
        // Update the phase scope's best solution step index
        phaseScope.setBestSolutionStepIndex(stepScope.getStepIndex());
    }

    private void updateGlobalBest(LocalSearchStepScope<Solution_> stepScope) {
        var phaseScope = stepScope.getPhaseScope();
        var solverScope = phaseScope.getSolverScope();
        
        // Use the best solution and its score directly, not the working solution
        var bestSolution = solverScope.getBestSolution();
        var bestScore = solverScope.getBestScore();
        
        if (bestSolution != null && bestScore != null) {
            globalState.tryUpdate(bestSolution, bestScore.raw());
        }
    }
}
```

---

## Configuration Changes

### IslandModelPhaseConfig Updates

```java
@XmlType
public class IslandModelPhaseConfig extends PhaseConfig<IslandModelPhaseConfig> {

    // Existing fields...
    @XmlElement(name = "islandCount")
    private Integer islandCount = null;

    @XmlElement(name = "migrationFrequency")
    private Integer migrationFrequency = null;

    // NEW: Receive global update frequency
    @XmlElement(name = "receiveGlobalUpdateFrequency")
    private Integer receiveGlobalUpdateFrequency = null;

    // DEPRECATED: Compare global frequency
    @Deprecated
    @XmlElement(name = "compareGlobalFrequency")
    private Integer compareGlobalFrequency = null;

    /**
     * Gets the frequency at which islands receive and check global best updates.
     */
    public @Nullable Integer getReceiveGlobalUpdateFrequency() {
        return receiveGlobalUpdateFrequency;
    }

    /**
     * Sets the frequency at which islands receive and check global best updates.
     */
    public void setReceiveGlobalUpdateFrequency(@Nullable Integer receiveGlobalUpdateFrequency) {
        this.receiveGlobalUpdateFrequency = receiveGlobalUpdateFrequency;
    }

    /**
     * @deprecated Use {@link #getReceiveGlobalUpdateFrequency()} instead.
     */
    @Deprecated
    public @Nullable Integer getCompareGlobalFrequency() {
        return compareGlobalFrequency;
    }

    /**
     * @deprecated Use {@link #setReceiveGlobalUpdateFrequency(Integer)} instead.
     */
    @Deprecated
    public void setCompareGlobalFrequency(@Nullable Integer compareGlobalFrequency) {
        this.compareGlobalFrequency = compareGlobalFrequency;
    }

    /**
     * With method for fluent API.
     */
    public @NonNull IslandModelPhaseConfig withReceiveGlobalUpdateFrequency(int receiveGlobalUpdateFrequency) {
        this.receiveGlobalUpdateFrequency = receiveGlobalUpdateFrequency;
        return this;
    }

    @Override
    public @NonNull IslandModelPhaseConfig inherit(@NonNull IslandModelPhaseConfig inheritedConfig) {
        super.inherit(inheritedConfig);
        islandCount = ConfigUtils.inheritOverwritableProperty(islandCount, inheritedConfig.getIslandCount());
        migrationFrequency = ConfigUtils.inheritOverwritableProperty(migrationFrequency, inheritedConfig.getMigrationFrequency());

        // NEW: Inherit receive global update frequency
        receiveGlobalUpdateFrequency = ConfigUtils.inheritOverwritableProperty(
            receiveGlobalUpdateFrequency, inheritedConfig.getReceiveGlobalUpdateFrequency());

        // DEPRECATED: Inherit compare global frequency for backward compatibility
        compareGlobalFrequency = ConfigUtils.inheritOverwritableProperty(
            compareGlobalFrequency, inheritedConfig.getCompareGlobalFrequency());

        phaseConfigList = ConfigUtils.inheritOverwritableProperty(phaseConfigList, inheritedConfig.getPhaseConfigList());
        return this;
    }
}
```

### DefaultIslandModelPhaseFactory Updates

```java
private IslandModelPhase<Solution_> buildPhase(IslandModelPhaseConfig phaseConfig,
                                               SolverConfigPolicy<Solution_> solverConfigPolicy,
                                               BestSolutionRecaller<Solution_> bestSolutionRecaller,
                                               int phaseIndex) {
    // ... existing code ...

    // Read receive global update frequency
    int receiveGlobalUpdateFrequency =
        phaseConfig.getReceiveGlobalUpdateFrequency() != null
            ? phaseConfig.getReceiveGlobalUpdateFrequency()
            : IslandModelPhaseConfig.DEFAULT_RECEIVE_GLOBAL_UPDATE_FREQUENCY;

    // Fall back to deprecated parameter if new parameter is not set
    if (phaseConfig.getReceiveGlobalUpdateFrequency() == null 
        && phaseConfig.getCompareGlobalFrequency() != null) {
        receiveGlobalUpdateFrequency = phaseConfig.getCompareGlobalFrequency();
        LOGGER.warn("Using deprecated 'compareGlobalFrequency' parameter. "
                  + "Please use 'receiveGlobalUpdateFrequency' instead.");
    }

    // ... rest of the method ...
}
```

---

## Testing Strategy

### Unit Tests

#### GlobalBestUpdaterTest

```java
class GlobalBestUpdaterTest {

    @Test
    void testUpdateGlobalBestOnFirstStep() {
        // Should update global best on first step
        SharedGlobalState<TestSolution> globalState = mock(SharedGlobalState.class);
        GlobalBestUpdater<TestSolution> updater = new GlobalBestUpdater<>(globalState, 0);
        
        AbstractStepScope<TestSolution> stepScope = mockStepScopeWithImprovedScore();
        
        updater.stepEnded(stepScope);
        
        verify(globalState).tryUpdate(any(), any());
    }

    @Test
    void testUpdateGlobalBestOnlyOnImprovement() {
        // Should update only when best score improves
        SharedGlobalState<TestSolution> globalState = mock(SharedGlobalState.class);
        GlobalBestUpdater<TestSolution> updater = new GlobalBestUpdater<>(globalState, 0);
        
        // First step - should update
        AbstractStepScope<TestSolution> step1 = mockStepScopeWithImprovedScore();
        updater.stepEnded(step1);
        verify(globalState, times(1)).tryUpdate(any(), any());
        
        // Second step - no improvement - should not update
        AbstractStepScope<TestSolution> step2 = mockStepScopeWithoutImprovement();
        updater.stepEnded(step2);
        verify(globalState, times(1)).tryUpdate(any(), any()); // Still only 1 call
        
        // Third step - improvement - should update again
        AbstractStepScope<TestSolution> step3 = mockStepScopeWithImprovedScore();
        updater.stepEnded(step3);
        verify(globalState, times(2)).tryUpdate(any(), any());
    }

    @Test
    void testNoUpdateWhenSolutionIsNull() {
        // Should not update when solution is null
        SharedGlobalState<TestSolution> globalState = mock(SharedGlobalState.class);
        GlobalBestUpdater<TestSolution> updater = new GlobalBestUpdater<>(globalState, 0);
        
        AbstractStepScope<TestSolution> stepScope = mockStepScopeWithNullSolution();
        
        updater.stepEnded(stepScope);
        
        verify(globalState, never()).tryUpdate(any(), any());
    }
}
```

#### Configuration Migration Test

```java
class IslandModelConfigMigrationTest {

    @Test
    void testDeprecatedParameterStillWorks() {
        // Old configuration should still work
        IslandModelConfig config = IslandModelConfig.builder()
            .withCompareGlobalFrequency(100)
            .build();
        
        assertEquals(100, config.getReceiveGlobalUpdateFrequency());
        assertEquals(100, config.getCompareGlobalFrequency());
    }

    @Test
    void testNewParameterTakesPrecedence() {
        // New parameter should take precedence
        IslandModelConfig config = IslandModelConfig.builder()
            .withCompareGlobalFrequency(100)
            .withReceiveGlobalUpdateFrequency(50)
            .build();
        
        assertEquals(50, config.getReceiveGlobalUpdateFrequency());
    }
}
```

### Integration Tests

#### IslandModelIntegrationTest

```java
class IslandModelIntegrationTest {

    @Test
    void testGlobalBestUpdatedOnlyOnImprovement() {
        // Create island model with 2 islands
        SolverConfig config = createIslandModelConfig()
            .withIslandCount(2)
            .withReceiveGlobalUpdateFrequency(10);
        
        Solver<TestSolution> solver = createSolver(config);
        TestSolution initialSolution = createInitialSolution();
        
        // Solve
        TestSolution bestSolution = solver.solve(initialSolution);
        
        // Verify global best was updated fewer times than total steps
        // (only when local best improved)
        long globalUpdateCount = getGlobalUpdateCount();
        long totalSteps = getTotalSteps();
        assertTrue(globalUpdateCount < totalSteps);
    }

    @Test
    void testReceiveGlobalUpdateFrequencyWorks() {
        // Test that islands receive global updates at specified frequency
        SolverConfig config = createIslandModelConfig()
            .withIslandCount(2)
            .withReceiveGlobalUpdateFrequency(20);
        
        // Run solver and count global adoptions
        List<Integer> adoptionSteps = getGlobalAdoptionSteps();
        
        // Verify adoptions occur at approximately the specified frequency
        for (int i = 1; i < adoptionSteps.size(); i++) {
            int gap = adoptionSteps.get(i) - adoptionSteps.get(i - 1);
            assertTrue(Math.abs(gap - 20) <= 2, // Allow small variance
                      "Adoption gap should be ~20, but was " + gap);
        }
    }
}
```

### Performance Tests

#### PerformanceBenchmark

```java
class IslandModelPerformanceBenchmark {

    @Test
    void benchmarkLockContention() {
        // Compare lock contention before and after optimization
        Problem problem = createTestProblem();
        
        // Baseline (current implementation)
        long baselineContention = measureLockContention(problem, BASELINE_CONFIG);
        
        // Optimized (new implementation)
        long optimizedContention = measureLockContention(problem, OPTIMIZED_CONFIG);
        
        // Verify reduced contention
        assertTrue(optimizedContention < baselineContention * 0.5,
                  "Optimized implementation should reduce lock contention by at least 50%");
    }

    @Test
    void benchmarkThroughput() {
        // Compare throughput (steps per second)
        Problem problem = createTestProblem();
        
        double baselineThroughput = measureThroughput(problem, BASELINE_CONFIG);
        double optimizedThroughput = measureThroughput(problem, OPTIMIZED_CONFIG);
        
        // Verify improved or maintained throughput
        assertTrue(optimizedThroughput >= baselineThroughput * 0.95,
                  "Optimized implementation should maintain at least 95% of baseline throughput");
    }
}
```

---

## Migration Guide

### For Users

#### Updating Configuration XML

**Before (deprecated)**:
```xml
<islandModel>
    <islandCount>4</islandCount>
    <migrationFrequency>100</migrationFrequency>
    <compareGlobalEnabled>true</compareGlobalEnabled>
    <compareGlobalFrequency>50</compareGlobalFrequency>
</islandModel>
```

**After (recommended)**:
```xml
<islandModel>
    <islandCount>4</islandCount>
    <migrationFrequency>100</migrationFrequency>
    <compareGlobalEnabled>true</compareGlobalEnabled>
    <receiveGlobalUpdateFrequency>50</receiveGlobalUpdateFrequency>
</islandModel>
```

#### Updating Java Code

**Before (deprecated)**:
```java
IslandModelConfig config = IslandModelConfig.builder()
    .withIslandCount(4)
    .withCompareGlobalEnabled(true)
    .withCompareGlobalFrequency(50)
    .build();
```

**After (recommended)**:
```java
IslandModelConfig config = IslandModelConfig.builder()
    .withIslandCount(4)
    .withCompareGlobalEnabled(true)
    .withReceiveGlobalUpdateFrequency(50)
    .build();
```

### For Developers

#### Updating Custom Listeners

If you have custom listeners that interact with `GlobalBestUpdater`:

```java
// Before
int frequency = config.getCompareGlobalFrequency();

// After
int frequency = config.getReceiveGlobalUpdateFrequency();
```

#### Updating Tests

Update tests to use new parameter names:

```java
// Before
config.setCompareGlobalFrequency(50);

// After
config.setReceiveGlobalUpdateFrequency(50);
```

---

## Risk Mitigation

### Risk 1: Performance Regression

**Concern**: Changes might degrade performance instead of improving it.

**Mitigation**:
- Comprehensive performance benchmarks before and after
- Measure lock contention, throughput, and convergence speed
- Compare with baseline implementation
- If performance degrades, revert to baseline

**Fallback**:
- Keep baseline implementation as reference
- Allow users to opt-out of optimization via configuration
- Document performance characteristics clearly

### Risk 2: Backward Compatibility Issues

**Concern**: Deprecated parameter handling might break existing configurations.

**Mitigation**:
- Support both old and new parameters during transition period
- Add comprehensive tests for parameter migration
- Log warnings when deprecated parameters are used
- Document migration path clearly

**Fallback**:
- If issues arise, extend deprecation period
- Provide automated migration scripts
- Offer support for migration

### Risk 3: Incorrect Global Best Updates

**Concern**: Logic to detect local best improvements might have bugs.

**Mitigation**:
- Comprehensive unit tests for all edge cases
- Integration tests with multiple islands
- Verify global best is always the best across all islands
- Add logging for debugging

**Fallback**:
- If bugs found, add additional validation
- Consider adding a "safe mode" that updates on every step
- Monitor global best correctness in production

### Risk 4: Documentation Confusion

**Concern**: Parameter rename might confuse users.

**Mitigation**:
- Clear migration guide with before/after examples
- Deprecation warnings in logs and IDE
- Update all documentation simultaneously
- Provide examples for both old and new parameters

**Fallback**:
- Extend deprecation period if needed
- Provide additional support resources
- Consider maintaining old parameter names longer

---

## Implementation Checklist

### Phase 1: Modify GlobalBestUpdater
- [ ] Add `previousBestScore` field to `GlobalBestUpdater`
- [ ] Implement `shouldUpdateGlobalBest()` method
- [ ] Modify `stepEnded()` to check for improvements
- [ ] Add logging for updates and skips
- [ ] Write unit tests for new behavior
- [ ] Run performance benchmarks

### Phase 2: Rename Configuration Parameter
- [ ] Add `receiveGlobalUpdateFrequency` to `IslandModelConfig`
- [ ] Deprecate `compareGlobalFrequency` with `@Deprecated`
- [ ] Add migration logic in `IslandModelConfig`
- [ ] Update `IslandModelPhaseConfig`
- [ ] Update `DefaultIslandModelPhaseFactory`
- [ ] Update `GlobalCompareListener`
- [ ] Write configuration migration tests
- [ ] Update XML schema if needed

### Phase 3: Update Documentation
- [ ] Update Javadoc for modified classes
- [ ] Update island model documentation
- [ ] Write migration guide
- [ ] Update configuration examples
- [ ] Add performance tuning recommendations
- [ ] Update implementation plan

### Phase 4: Testing & Validation
- [ ] Write unit tests for `GlobalBestUpdater`
- [ ] Write unit tests for configuration
- [ ] Write integration tests for island model
- [ ] Write performance benchmarks
- [ ] Run regression tests
- [ ] Fix any issues found
- [ ] Document results

---

## Success Criteria

### Functional Requirements

1. ✅ Islands only update global best when local best improves
2. ✅ Islands continue working with their local best solution
3. ✅ Islands receive global updates at specified frequency
4. ✅ Deprecated parameters still work
5. ✅ New parameters work correctly
6. ✅ Configuration migration works seamlessly

### Performance Requirements

1. ✅ Lock contention reduced by at least 50%
2. ✅ Throughput maintained at ≥95% of baseline
3. ✅ Convergence speed maintained or improved
4. ✅ Memory overhead is negligible

### Quality Requirements

1. ✅ Code coverage ≥80% for new code
2. ✅ All modified classes have updated Javadoc
3. ✅ No critical bugs found in testing
4. ✅ All existing tests pass
5. ✅ Migration guide is clear and complete

---

## Future Enhancements

### Adaptive Receive Frequency

**Goal**: Dynamically adjust receive frequency based on convergence.

**Approach**:
- Monitor how often global best is adopted
- Increase frequency if adoption rate is low
- Decrease frequency if adoption rate is high

### Event-Driven Global Updates

**Goal**: Use event-driven approach instead of polling.

**Approach**:
- Islands subscribe to global best change events
- Receive notification when global best changes
- Eliminate polling overhead

### Selective Global Adoption

**Goal**: Only adopt global best if it's significantly better.

**Approach**:
- Add threshold parameter for adoption
- Only adopt if global best is X% better than local best
- Reduces disruption from marginal improvements

---

## References

- Current Implementation: [`GlobalBestUpdater.java`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/GlobalBestUpdater.java)
- Current Implementation: [`GlobalCompareListener.java`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/GlobalCompareListener.java)
- Configuration: [`IslandModelConfig.java`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/IslandModelConfig.java)
- Factory: [`DefaultIslandModelPhaseFactory.java`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/DefaultIslandModelPhaseFactory.java)
- Original Plan: [`COMPARE_TO_GLOBAL_IMPLEMENTATION_PLAN.md`](./COMPARE_TO_GLOBAL_IMPLEMENTATION_PLAN.md)
- Island Model Documentation: [`ISLAND_MODEL_DOCUMENTATION.md`](./ISLAND_MODEL_DOCUMENTATION.md)

---

**Document Version**: 1.0
**Last Updated**: 2025-01-15
**Status**: Ready for Implementation
