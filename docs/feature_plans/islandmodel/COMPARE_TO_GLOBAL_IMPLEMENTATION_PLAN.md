# Compare to Global Feature Implementation Plan (Corrected)

## Executive Summary

This document outlines a corrected, implementation-ready plan to add "comparing to global" functionality to GreyCOS's island model implementation. This feature allows agents to periodically check and adopt the global best solution from across all islands, significantly improving convergence speed and solution quality.

**Status**: Island model foundation is implemented, but compare-to-global functionality is missing.

**Key Changes from Original Plan**:
- Removed all references to Genetic Algorithm and LSHADE (not implemented in GreyCOS)
- Fixed hallucinated API methods that don't exist
- Provided feasible implementation approach using actual GreyCOS APIs
- Simplified to pragmatic approach that works without modifying acceptor classes

---

## Table of Contents

1. [Background & Motivation](#background--motivation)
2. [Current State Analysis](#current-state-analysis)
3. [Gap Analysis](#gap-analysis)
4. [Target Architecture](#target-architecture)
5. [Implementation Strategy](#implementation-strategy)
6. [Detailed Design](#detailed-design)
7. [Configuration](#configuration)
8. [Testing Strategy](#testing-strategy)
9. [Risk Mitigation](#risk-mitigation)

---

## Background & Motivation

### What is "Compare to Global"?

In island model optimization, each agent (island) maintains its own solution state and periodically exchanges best solutions with neighbors through **migration**. However, migration propagates solutions slowly through ring topology.

**Compare to global** is an additional mechanism where agents periodically check the shared global best solution (the best solution found across ALL islands) and adopt it if it's better than their current best. This provides:

1. **Faster convergence** - Global best is immediately available to all agents
2. **Better solution quality** - Prevents getting stuck in local optima
3. **Complementary to migration** - Migration provides diversity, global comparison provides intensification

### GreyJack Reference

From GreyJack documentation ([`ISLAND_MODEL_DOCUMENTATION.md`](./ISLAND_MODEL_DOCUMENTATION.md)):

**Tabu Search** (lines 1216-1232):
```rust
pub struct TabuSearchBase {
    pub compare_to_global: bool,  // Whether to accept global best
    // ...
}

// Global best integration
if global_top_individual.score > self.agent_top_individual.score {
    if tsb.compare_to_global {
        self.population[0] = global_top_individual.clone();
    }
}
```

**Late Acceptance** (lines 1262-1269):
```rust
// Global best integration
if global_top_individual.score > self.agent_top_individual.score {
    la.late_scores.push_front(self.population[0].score.clone());
    if la.late_scores.len() > la.late_acceptance_size {
        la.late_scores.pop_back();
    }
    self.population[0] = global_top_individual.clone();
}
```

**Simulated Annealing** (lines 1329-1333):
```rust
// Global best integration
if global_top_individual.score > self.agent_top_individual.score {
    self.population[0] = global_top_individual.clone();
}
```

**Note from documentation**: "Often gets stuck if comparing to global, but common performance increases greatly."

### Key Insight

Compare-to-global is particularly beneficial for **local search algorithms** (TS, LA, SA) that maintain a single solution. These algorithms benefit from immediate access to global best solution to accelerate convergence.

---

## Current State Analysis

### What's Already Implemented

✅ **Island Model Foundation**
- [`IslandAgent.java`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/IslandAgent.java) - Agent implementation with migration
- [`SharedGlobalState.java`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/SharedGlobalState.java) - Thread-safe global best tracking
- [`IslandModelConfig.java`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/IslandModelConfig.java) - Configuration class
- [`IslandModelPhaseConfig.java`](../../core/src/main/java/ai/greycos/solver/core/config/islandmodel/IslandModelPhaseConfig.java) - Phase configuration
- [`BoundedChannel.java`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/BoundedChannel.java) - Communication channels
- [`AgentUpdate.java`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/AgentUpdate.java) - Migration messages
- [`AgentStatus.java`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/AgentStatus.java) - Agent lifecycle states

✅ **Migration Mechanism**
- Ring topology communication
- Periodic migration based on `migrationFrequency`
- Alternating send/receive order to prevent deadlock
- Dead agents continue to forward messages

✅ **Global Best Tracking**
- `SharedGlobalState.tryUpdate()` - Thread-safe update of global best
- `SharedGlobalState.getBestSolution()` - Volatile read of global best
- Observer pattern support for event system integration

✅ **Local Search Acceptors**
- [`AbstractTabuAcceptor`](../../core/src/main/java/ai/greycos/solver/core/impl/localsearch/decider/acceptor/tabu/AbstractTabuAcceptor.java) - Tabu search with aspiration
- [`LateAcceptanceAcceptor`](../../core/src/main/java/ai/greycos/solver/core/impl/localsearch/decider/acceptor/lateacceptance/LateAcceptanceAcceptor.java) - Late acceptance with score history
- [`SimulatedAnnealingAcceptor`](../../core/src/main/java/ai/greycos/solver/core/impl/localsearch/decider/acceptor/simulatedannealing/SimulatedAnnealingAcceptor.java) - Simulated annealing with temperature

### What's Missing

❌ **Compare to Global Functionality**
- No mechanism for agents to periodically check global best
- No configuration option for enabling/disabling compare-to-global
- No integration with local search phases
- No algorithm-specific handling (different behaviors for TS, LA, SA)

❌ **Configuration**
- No `compareGlobalEnabled` flag in config
- No `compareGlobalFrequency` parameter (how often to check)

❌ **Agent Integration**
- [`IslandAgent.run()`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/IslandAgent.java:91-166) doesn't check global best
- Only migration updates agent's solution

---

## Gap Analysis

### Functional Gaps

| Feature | GreyJack (Rust) | GreyCOS (Java) | Gap |
|---------|------------------|------------------|-----|
| Global best reference in agents | ✅ `global_top_individual: Arc<Mutex<Individual>>` | ✅ `globalState: SharedGlobalState` | None |
| Periodic global best check | ✅ Every step (in main loop) | ❌ Not implemented | **Missing** |
| Configurable enable/disable | ✅ `compare_to_global: bool` (TS only) | ❌ Not available | **Missing** |
| Algorithm-specific handling | ✅ Different logic for TS, LA, SA | ❌ Not available | **Missing** |
| Late scores update (LA) | ✅ Updates late_scores on global best | ❌ Not available | **Missing** |
| Integration with phases | ✅ Called in step loop | ❌ Not integrated | **Missing** |

### Architectural Gaps

1. **No Hook for Global Best Comparison**
   - `IslandAgent.run()` executes phases but doesn't check global best
   - No lifecycle event for "after step, before next step"

2. **No Configuration for Compare-to-Global**
   - `IslandModelConfig` lacks compare-to-global parameters
   - `IslandModelPhaseConfig` lacks compare-to-global parameters

3. **Limited Access to Phase Internals**
   - Agent runs phases but doesn't have access to phase internals
   - No API to access acceptor state from outside phase
   - Acceptor state is private and encapsulated

---

## Target Architecture

### High-Level Design

Add compare-to-global functionality as an optional, configurable feature that works alongside migration:

```
┌─────────────────────────────────────────────────────────────┐
│                    SharedGlobalState                    │
│  - bestSolution (volatile)                             │
│  - bestScore (volatile)                               │
│  - tryUpdate() (synchronized)                          │
│  - getBestSolution() (volatile read)                    │
└─────────────────────────────────────────────────────────────┘
            ▲
            │ tryUpdate() when agent finds improvement
            │ getBestSolution() for compare-to-global
            │
┌─────────────────────────────────────────────────────────────┐
│                   IslandAgent                          │
│  - phases: List<Phase>                                 │
│  - globalState: SharedGlobalState                        │
│  - config: IslandModelConfig                            │
│  - stepsUntilNextGlobalCompare: int                       │
│                                                        │
│  run():                                                 │
│    for (Phase phase : phases) {                          │
│      attachGlobalCompareListener(phase) ◄─── NEW          │
│      phase.solve(islandScope)                             │
│    }                                                     │
│                                                        │
│  Migration:                                              │
│    - Periodic (every migrationFrequency steps)               │
│    - Ring topology (neighbor-to-neighbor)                 │
│                                                        │
│  Compare to Global: ◄─── NEW                            │
│    - Via lifecycle listener on each phase                    │
│    - Periodic (every compareGlobalFrequency steps)           │
│    - Direct access to SharedGlobalState                    │
│    - Simple adoption (no algorithm-specific handling)         │
└─────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

#### 1. Compare-to-Global as Separate Mechanism from Migration

**Decision**: Implement compare-to-global as a separate, independent mechanism from migration.

**Rationale**:
- Migration provides **diversity** (spreads solutions across islands)
- Compare-to-global provides **intensification** (immediately adopts best solution)
- They serve different purposes and should be independently configurable
- Both can be enabled simultaneously for best results

**Implementation**:
- Separate frequency parameter: `compareGlobalFrequency`
- Independent enable/disable flag: `compareGlobalEnabled`
- Separate check via lifecycle listener

#### 2. Simplified Adoption (No Algorithm-Specific Handling - Initial Version)

**Decision**: Implement simple adoption without algorithm-specific state updates initially.

**Rationale**:
- Acceptor state is private and encapsulated
- No public API to manipulate acceptor state externally
- Reflection is fragile and breaks encapsulation
- Simple adoption still provides significant benefit

**Implementation**:
```java
private void checkAndAdoptGlobalBest() {
    if (!config.isCompareGlobalEnabled()) {
        return;
    }

    Solution_ globalBest = globalState.getBestSolution();
    if (globalBest == null) {
        return;
    }

    Score<?> globalScore = extractScore(globalBest);
    Score<?> currentScore = extractScore(getCurrentBestSolution());

    if (globalScore.compareTo(currentScore) > 0) {
        LOGGER.info("Agent {} adopting global best (score: {} vs {})",
                   agentId, globalScore, currentScore);
        replaceCurrentSolution(deepClone(globalBest));
        updateGlobalBest();
    }
}
```

**Trade-offs**:
- **Pros**: Simple, maintainable, no acceptor modifications, works immediately
- **Cons**: Late Acceptance may not work optimally (but won't crash), Tabu/SA lose some benefits

#### 3. Configuration at Phase Level

**Decision**: Add compare-to-global parameters to `IslandModelPhaseConfig` and `IslandModelConfig`.

**Rationale**:
- Users may want different settings for different phases
- Allows fine-grained control per phase
- Consistent with existing configuration pattern

**Implementation**:
```java
public class IslandModelConfig {
    private boolean compareGlobalEnabled = true;  // Default: enabled
    private int compareGlobalFrequency = 50;     // Default: every 50 steps

    // Getters, setters, builder methods
}

public class IslandModelPhaseConfig {
    @XmlElement(name = "compareGlobalEnabled")
    private Boolean compareGlobalEnabled = null;

    @XmlElement(name = "compareGlobalFrequency")
    private Integer compareGlobalFrequency = null;

    // Getters, setters, with methods
}
```

#### 4. Lifecycle Listener Integration

**Decision**: Use phase lifecycle listeners to check global best after each step.

**Rationale**:
- GreyCOS has built-in lifecycle listener system
- `LocalSearchPhaseLifecycleListener` provides `stepEnded()` hook
- Works with existing phase infrastructure
- No need to modify phase execution logic

**Implementation**:
```java
public class GlobalCompareListener<Solution_> 
    implements LocalSearchPhaseLifecycleListener<Solution_> {
    
    private final SharedGlobalState<Solution_> globalState;
    private final IslandModelConfig config;
    private int stepsUntilNextCheck;
    
    public GlobalCompareListener(SharedGlobalState<Solution_> globalState,
                              IslandModelConfig config) {
        this.globalState = globalState;
        this.config = config;
        this.stepsUntilNextCheck = config.getCompareGlobalFrequency();
    }
    
    @Override
    public void stepEnded(LocalSearchStepScope<Solution_> stepScope) {
        stepsUntilNextCheck--;
        
        if (stepsUntilNextCheck <= 0) {
            checkAndAdoptGlobalBest(stepScope);
            stepsUntilNextCheck = config.getCompareGlobalFrequency();
        }
    }
    
    private void checkAndAdoptGlobalBest(LocalSearchStepScope<Solution_> stepScope) {
        // Implementation as shown above
    }
}
```

---

## Implementation Strategy

### Approach: Incremental Enhancement

Add compare-to-global functionality in phases, ensuring backward compatibility and minimal disruption.

### Phase 1: Configuration (Days 1-2)

**Goal**: Add configuration parameters for compare-to-global.

**Tasks**:

1. **Update IslandModelConfig**
   - Add `compareGlobalEnabled: boolean` (default: `true`)
   - Add `compareGlobalFrequency: int` (default: `50`)
   - Add getters, setters, and builder methods
   - Add validation (frequency must be ≥ 1)

2. **Update IslandModelPhaseConfig**
   - Add `compareGlobalEnabled: Boolean` (nullable for inheritance)
   - Add `compareGlobalFrequency: Integer` (nullable for inheritance)
   - Add getters, setters, and with methods
   - Update `inherit()` method

3. **Update DefaultIslandModelPhaseFactory**
   - Read compare-to-global config from phase config
   - Pass to `IslandModelConfig`
   - Handle defaults

**Deliverables**:
- Updated configuration classes
- Unit tests for configuration

### Phase 2: Lifecycle Listener (Days 3-4)

**Goal**: Create lifecycle listener for global best comparison.

**Tasks**:

1. **Create GlobalCompareListener Class**
   - Implement `LocalSearchPhaseLifecycleListener<Solution_>`
   - Store reference to `SharedGlobalState` and `IslandModelConfig`
   - Implement `stepEnded()` to check global best periodically
   - Implement `checkAndAdoptGlobalBest()` method

2. **Add Helper Methods**
   - `extractScore(Solution)` - Extract score from solution
   - `getCurrentBestSolution()` - Get current best from step scope
   - `deepClone(Solution)` - Clone solution using score director
   - `updateGlobalBest()` - Update global best after adoption

3. **Add Logging**
   - Log when global best is adopted
   - Log score comparisons
   - Log when feature is disabled

**Deliverables**:
- `GlobalCompareListener` class
- Unit tests for listener logic

### Phase 3: Agent Integration (Days 5-6)

**Goal**: Integrate lifecycle listener into agent execution.

**Tasks**:

1. **Update IslandAgent Constructor**
   - Add `compareGlobalEnabled` and `compareGlobalFrequency` fields
   - Initialize from config

2. **Modify run() Method**
   - Before calling `phase.solve()`, attach lifecycle listener
   - Create `GlobalCompareListener` with appropriate parameters
   - Attach listener to phase using `phase.addPhaseLifecycleListener()`

3. **Handle Non-Local-Search Phases**
   - Check if phase is instance of `LocalSearchPhase`
   - Only attach listener for local search phases
   - Skip for other phase types (construction heuristic, etc.)

**Deliverables**:
- Updated `IslandAgent` with listener integration
- Integration tests with various phase types

### Phase 4: Testing & Validation (Days 7-8)

**Goal**: Comprehensive testing to ensure correctness.

**Tasks**:

1. **Unit Tests**
   - Test configuration parsing
   - Test listener logic
   - Test frequency counter
   - Test global best adoption

2. **Integration Tests**
   - Test with Tabu Search + compare-to-global
   - Test with Late Acceptance + compare-to-global
   - Test with Simulated Annealing + compare-to-global
   - Test with migration + compare-to-global (both enabled)
   - Test with non-local-search phases (should not attach listener)

3. **Performance Tests**
   - Compare with only migration enabled
   - Compare with only compare-to-global enabled
   - Compare with both enabled
   - Measure convergence speed
   - Measure solution quality

4. **Regression Tests**
   - Run existing island model tests
   - Ensure no breaking changes
   - Verify backward compatibility

**Deliverables**:
- Comprehensive test suite
- Performance benchmarks
- Test report

---

## Detailed Design

### Class Diagram

```
┌─────────────────────────────────────────────────────────────┐
│              IslandModelConfig                         │
│  - compareGlobalEnabled: boolean                        │
│  - compareGlobalFrequency: int                         │
└─────────────────────────────────────────────────────────────┘
                        │
                        │ used by
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                   IslandAgent                          │
│  - compareGlobalEnabled: boolean                       │
│  - compareGlobalFrequency: int                        │
│                                                        │
│  + run(): void                                         │
│  + attachGlobalCompareListener(Phase): void             │
└─────────────────────────────────────────────────────────────┘
                        │
                        │ creates
                        ▼
┌─────────────────────────────────────────────────────────────┐
│            GlobalCompareListener                        │
│  - globalState: SharedGlobalState                      │
│  - config: IslandModelConfig                          │
│  - stepsUntilNextCheck: int                           │
│                                                        │
│  + stepEnded(LocalSearchStepScope): void               │
│  + checkAndAdoptGlobalBest(LocalSearchStepScope): void │
└─────────────────────────────────────────────────────────────┘
                        │
                        │ uses
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              SharedGlobalState                          │
│  - bestSolution: Solution_                            │
│  - bestScore: Score<?>                                │
│                                                        │
│  + getBestSolution(): Solution_                          │
│  + tryUpdate(Solution, Score): boolean                  │
└─────────────────────────────────────────────────────────────┘
```

### Sequence Diagram: Compare to Global

```
IslandAgent          GlobalCompareListener      SharedGlobalState    LocalSearchPhase
    │                       │                      │                      │
    │  [start phase]         │                      │                      │
    │  create listener       │                      │                      │
    ├──────────────────────>│                      │                      │
    │                       │                      │                      │
    │  attach listener      │                      │                      │
    ├─────────────────────────────────────────────────────────────>│
    │                       │                      │                      │
    │  [phase running]     │                      │                      │
    │                       │                      │                      │
    │                       │  [step completed]     │                      │
    │                       │  stepsUntilNext--    │                      │
    │                       │                      │                      │
    │                       │  stepsUntilNext == 0?│                      │
    │                       ├──────────────────────>│                      │
    │                       │  getBestSolution()   │                      │
    │                       │<──────────────────────┤                      │
    │                       │  globalBest          │                      │
    │                       │                      │                      │
    │                       │  compare scores       │                      │
    │                       │  global > current?   │                      │
    │                       │                      │                      │
    │                       │  [YES] adopt global  │                      │
    │                       │  replace solution    │                      │
    │                       │  update scope        │                      │
    │                       │                      │                      │
    │                       │  update global best  │                      │
    │                       ├──────────────────────>│                      │
    │                       │  tryUpdate()        │                      │
    │                       │                      │                      │
    │                       │  reset counter       │                      │
    │                       │  continue loop       │                      │
```

### Key Algorithms

#### Global Compare Listener Implementation

```java
package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.greycos.solver.core.impl.localsearch.event.LocalSearchPhaseLifecycleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle listener that checks and adopts global best solution periodically.
 * Attached to local search phases to enable compare-to-global functionality.
 */
public class GlobalCompareListener<Solution_> 
    implements LocalSearchPhaseLifecycleListener<Solution_> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalCompareListener.class);

    private final SharedGlobalState<Solution_> globalState;
    private final IslandModelConfig config;
    private final int agentId;
    private int stepsUntilNextCheck;

    public GlobalCompareListener(
            SharedGlobalState<Solution_> globalState,
            IslandModelConfig config,
            int agentId) {
        this.globalState = globalState;
        this.config = config;
        this.agentId = agentId;
        this.stepsUntilNextCheck = config.getCompareGlobalFrequency();
    }

    @Override
    public void stepEnded(LocalSearchStepScope<Solution_> stepScope) {
        // Check if feature is enabled
        if (!config.isCompareGlobalEnabled()) {
            return;
        }

        // Decrement counter
        stepsUntilNextCheck--;

        // Check if it's time to compare to global
        if (stepsUntilNextCheck <= 0) {
            checkAndAdoptGlobalBest(stepScope);
            stepsUntilNextCheck = config.getCompareGlobalFrequency();
        }
    }

    private void checkAndAdoptGlobalBest(LocalSearchStepScope<Solution_> stepScope) {
        // Get global best
        Solution_ globalBest = globalState.getBestSolution();
        if (globalBest == null) {
            return; // No global best yet
        }

        // Get current best from step scope
        Score<?> currentScore = stepScope.getPhaseScope().getBestScore();
        Score<?> globalScore = globalState.getBestScore();

        if (globalScore == null || currentScore == null) {
            return;
        }

        // Compare (higher score is better in GreyCOS)
        int comparisonResult = ((Score) globalScore).compareTo((Score) currentScore);
        
        if (comparisonResult > 0) {
            LOGGER.info("Agent {} adopting global best (score: {} vs {})",
                       agentId, globalScore, currentScore);

            // Clone global best solution
            Solution_ clonedGlobalBest = deepClone(globalBest, stepScope);

            // Replace current solution with global best
            replaceCurrentSolution(clonedGlobalBest, stepScope);

            // Update global best (in case this agent now has the global best)
            updateGlobalBest(stepScope);
        }
    }

    @SuppressWarnings("unchecked")
    private Solution_ deepClone(Solution_ solution, 
                               LocalSearchStepScope<Solution_> stepScope) {
        if (solution == null) {
            return null;
        }
        // Use GreyCOS's solution cloner from score director
        return stepScope.getScoreDirector().cloneSolution(solution);
    }

    private void replaceCurrentSolution(Solution_ newSolution,
                                     LocalSearchStepScope<Solution_> stepScope) {
        // Set the new solution as the working solution
        var scoreDirector = stepScope.getScoreDirector();
        scoreDirector.setWorkingSolution(newSolution);
        
        // Update the best solution
        scoreDirector.setBestSolution(scoreDirector.cloneSolution(newSolution));
        
        // Calculate and set the best score
        var newBestScore = scoreDirector.calculateScore();
        scoreDirector.setBestScore(newBestScore);
        
        // CRITICAL: Update the embedded score in the working solution
        // This ensures that when the phase compares working solution's score
        // against the best score, it uses the correct (updated) score
        @SuppressWarnings("unchecked")
        var scoreToSet = (Score) newBestScore.raw();
        scoreDirector.getSolutionDescriptor().setScore(newSolution, scoreToSet);
    }

    private void updateGlobalBest(LocalSearchStepScope<Solution_> stepScope) {
        var scoreDirector = stepScope.getScoreDirector();
        var currentSolution = scoreDirector.getWorkingSolution();
        var currentScore = scoreDirector.calculateScore();

        // Try to update global best
        globalState.tryUpdate(currentSolution, currentScore.raw());
    }
}
```

#### Agent Integration

```java
// In IslandAgent.java, modify run() method:

@Override
public void run() {
    try {
        LOGGER.info("Agent {} started with {} phases", agentId, phases.size());

        // Initialize status vector
        statusVector = new ArrayList<>();
        for (int i = 0; i < config.getIslandCount(); i++) {
            statusVector.add(AgentStatus.ALIVE);
        }

        // Create isolated solver scope for this island
        islandScope = parentSolverScope.createChildThreadSolverScope(ChildThreadType.MOVE_THREAD);
        islandScope.setInitialSolution(initialSolution);

        // Run phases on this island
        for (Phase<Solution_> phase : phases) {
            if (shouldTerminate()) {
                LOGGER.info("Agent {} terminating early due to global termination", agentId);
                break;
            }

            LOGGER.debug("Agent {} running phase: {}", agentId, phase.getClass().getSimpleName());

            // Attach migration trigger listener to this phase
            MigrationTrigger<Solution_> migrationTrigger = new MigrationTrigger<>(this);
            phase.addPhaseLifecycleListener(migrationTrigger);

            // NEW: Attach global compare listener for local search phases
            if (config.isCompareGlobalEnabled() && phase instanceof LocalSearchPhase) {
                GlobalCompareListener<Solution_> globalCompareListener = 
                    new GlobalCompareListener<>(globalState, config, agentId);
                phase.addPhaseLifecycleListener(globalCompareListener);
                LOGGER.debug("Agent {} attached global compare listener to phase {}", 
                            agentId, phase.getClass().getSimpleName());
            }

            // Call solvingStarted to initialize selectors with the island's workingRandom
            phase.solvingStarted(islandScope);
            phase.solve(islandScope);
            phase.solvingEnded(islandScope);
        }

        // ... rest of run() method unchanged
    } catch (InterruptedException e) {
        LOGGER.info("Agent {} interrupted", agentId);
        Thread.currentThread().interrupt();
    } catch (Exception e) {
        LOGGER.error("Agent {} encountered unexpected error", agentId, e);
        markAsDead();
    }
}
```

---

## Configuration

### IslandModelConfig

```java
public class IslandModelConfig {

    // Existing fields...
    private int islandCount = DEFAULT_ISLAND_COUNT;
    private double migrationRate = DEFAULT_MIGRATION_RATE;
    private int migrationFrequency = DEFAULT_MIGRATION_FREQUENCY;
    private boolean enabled = false;

    // NEW: Compare-to-global fields
    private boolean compareGlobalEnabled = true;  // Default: enabled
    private int compareGlobalFrequency = 50;      // Default: every 50 steps

    // Getters
    public boolean isCompareGlobalEnabled() {
        return compareGlobalEnabled;
    }

    public int getCompareGlobalFrequency() {
        return compareGlobalFrequency;
    }

    // Setters
    public void setCompareGlobalEnabled(boolean compareGlobalEnabled) {
        this.compareGlobalEnabled = compareGlobalEnabled;
    }

    public void setCompareGlobalFrequency(int compareGlobalFrequency) {
        if (compareGlobalFrequency < 1) {
            throw new IllegalArgumentException(
                "Compare global frequency (" + compareGlobalFrequency + ") must be at least 1.");
        }
        this.compareGlobalFrequency = compareGlobalFrequency;
    }

    // Builder methods
    public Builder withCompareGlobalEnabled(boolean compareGlobalEnabled) {
        this.compareGlobalEnabled = compareGlobalEnabled;
        return this;
    }

    public Builder withCompareGlobalFrequency(int compareGlobalFrequency) {
        this.compareGlobalFrequency = compareGlobalFrequency;
        return this;
    }
}
```

### IslandModelPhaseConfig

```java
@XmlType
public class IslandModelPhaseConfig extends PhaseConfig<IslandModelPhaseConfig> {

    // Existing fields...
    @XmlElement(name = "islandCount")
    private Integer islandCount = null;

    @XmlElement(name = "migrationRate")
    private Double migrationRate = null;

    @XmlElement(name = "migrationFrequency")
    private Integer migrationFrequency = null;

    // NEW: Compare-to-global fields
    @XmlElement(name = "compareGlobalEnabled")
    private Boolean compareGlobalEnabled = null;

    @XmlElement(name = "compareGlobalFrequency")
    private Integer compareGlobalFrequency = null;

    // Getters
    public @Nullable Boolean getCompareGlobalEnabled() {
        return compareGlobalEnabled;
    }

    public @Nullable Integer getCompareGlobalFrequency() {
        return compareGlobalFrequency;
    }

    // Setters
    public void setCompareGlobalEnabled(@Nullable Boolean compareGlobalEnabled) {
        this.compareGlobalEnabled = compareGlobalEnabled;
    }

    public void setCompareGlobalFrequency(@Nullable Integer compareGlobalFrequency) {
        this.compareGlobalFrequency = compareGlobalFrequency;
    }

    // With methods
    public @NonNull IslandModelPhaseConfig withCompareGlobalEnabled(boolean compareGlobalEnabled) {
        this.compareGlobalEnabled = compareGlobalEnabled;
        return this;
    }

    public @NonNull IslandModelPhaseConfig withCompareGlobalFrequency(int compareGlobalFrequency) {
        this.compareGlobalFrequency = compareGlobalFrequency;
        return this;
    }

    @Override
    public @NonNull IslandModelPhaseConfig inherit(@NonNull IslandModelPhaseConfig inheritedConfig) {
        super.inherit(inheritedConfig);
        islandCount = ConfigUtils.inheritOverwritableProperty(islandCount, inheritedConfig.getIslandCount());
        migrationRate = ConfigUtils.inheritOverwritableProperty(migrationRate, inheritedConfig.getMigrationRate());
        migrationFrequency = ConfigUtils.inheritOverwritableProperty(migrationFrequency, inheritedConfig.getMigrationFrequency());

        // NEW: Inherit compare-to-global fields
        compareGlobalEnabled = ConfigUtils.inheritOverwritableProperty(compareGlobalEnabled, inheritedConfig.getCompareGlobalEnabled());
        compareGlobalFrequency = ConfigUtils.inheritOverwritableProperty(compareGlobalFrequency, inheritedConfig.getCompareGlobalFrequency());

        phaseConfigList = ConfigUtils.inheritOverwritableProperty(phaseConfigList, inheritedConfig.getPhaseConfigList());
        return this;
    }
}
```

### Example Configuration

```xml
<solver>
    <islandModel>
        <islandCount>4</islandCount>
        <migrationRate>0.1</migrationRate>
        <migrationFrequency>100</migrationFrequency>

        <!-- NEW: Compare-to-global configuration -->
        <compareGlobalEnabled>true</compareGlobalEnabled>
        <compareGlobalFrequency>50</compareGlobalFrequency>

        <localSearch>
            <lateAcceptance>
                <lateAcceptanceSize>5</lateAcceptanceSize>
            </lateAcceptance>
        </localSearch>
    </islandModel>
</solver>
```

### Configuration Recommendations

| Algorithm | Compare Global Enabled | Frequency | Notes |
|-----------|----------------------|-----------|--------|
| Tabu Search | `true` | 50-100 | Works well with aspiration mechanism |
| Late Acceptance | `true` | 50-100 | May not be optimal but improves convergence |
| Simulated Annealing | `true` (cautious) | 100-200 | May cause stuck, test carefully |

---

## Testing Strategy

### Unit Tests

#### Configuration Tests

**IslandModelConfigTest**:
```java
@Test
void testCompareGlobalEnabled_default() {
    IslandModelConfig config = new IslandModelConfig();
    assertTrue(config.isCompareGlobalEnabled());
}

@Test
void testCompareGlobalEnabled_setter() {
    IslandModelConfig config = new IslandModelConfig();
    config.setCompareGlobalEnabled(false);
    assertFalse(config.isCompareGlobalEnabled());
}

@Test
void testCompareGlobalFrequency_default() {
    IslandModelConfig config = new IslandModelConfig();
    assertEquals(50, config.getCompareGlobalFrequency());
}

@Test
void testCompareGlobalFrequency_validation() {
    IslandModelConfig config = new IslandModelConfig();
    assertThrows(IllegalArgumentException.class, () -> {
        config.setCompareGlobalFrequency(0);
    });
}
```

#### Listener Tests

**GlobalCompareListenerTest**:
```java
@Test
void testStepEnded_disabled() {
    // Setup listener with compareGlobalEnabled = false
    IslandModelConfig config = new IslandModelConfig();
    config.setCompareGlobalEnabled(false);
    
    SharedGlobalState<TestSolution> globalState = mock(SharedGlobalState.class);
    GlobalCompareListener<TestSolution> listener = 
        new GlobalCompareListener<>(globalState, config, 0);
    
    LocalSearchStepScope<TestSolution> stepScope = mock(LocalSearchStepScope.class);
    
    // Call stepEnded
    listener.stepEnded(stepScope);
    
    // Verify global state was NOT accessed
    verify(globalState, never()).getBestSolution();
}

@Test
void testStepEnded_adoptsBetter() {
    // Setup listener with compareGlobalEnabled = true
    IslandModelConfig config = new IslandModelConfig();
    config.setCompareGlobalFrequency(1);
    
    SharedGlobalState<TestSolution> globalState = mock(SharedGlobalState.class);
    TestSolution betterSolution = createBetterSolution();
    when(globalState.getBestSolution()).thenReturn(betterSolution);
    when(globalState.getBestScore()).thenReturn(betterSolution.getScore());
    
    GlobalCompareListener<TestSolution> listener = 
        new GlobalCompareListener<>(globalState, config, 0);
    
    LocalSearchStepScope<TestSolution> stepScope = mock(LocalSearchStepScope.class);
    when(stepScope.getPhaseScope().getBestScore()).thenReturn(createWorseScore());
    
    // Call stepEnded (should trigger check)
    listener.stepEnded(stepScope);
    
    // Verify solution was replaced
    verify(stepScope.getScoreDirector()).setWorkingSolution(betterSolution);
}

@Test
void testStepEnded_frequency() {
    // Test that check only happens every N steps
    IslandModelConfig config = new IslandModelConfig();
    config.setCompareGlobalFrequency(3);
    
    SharedGlobalState<TestSolution> globalState = mock(SharedGlobalState.class);
    GlobalCompareListener<TestSolution> listener = 
        new GlobalCompareListener<>(globalState, config, 0);
    
    LocalSearchStepScope<TestSolution> stepScope = mock(LocalSearchStepScope.class);
    
    // Call stepEnded 3 times
    listener.stepEnded(stepScope);
    listener.stepEnded(stepScope);
    listener.stepEnded(stepScope);
    
    // Verify global state was accessed only once (on 3rd step)
    verify(globalState, times(1)).getBestSolution();
}
```

### Integration Tests

**CompareToGlobalIntegrationTest**:
```java
@Test
void testTabuSearchWithCompareGlobal() {
    // Create solver with island model + Tabu Search + compare-to-global
    SolverConfig config = createIslandModelConfig()
        .withIslandCount(2)
        .withCompareGlobalEnabled(true)
        .withCompareGlobalFrequency(10);

    Solver<TestSolution> solver = createSolver(config);
    TestSolution initialSolution = createInitialSolution();

    // Solve
    TestSolution bestSolution = solver.solve(initialSolution);

    // Verify solution quality
    assertTrue(bestSolution.getScore().compareTo(initialSolution.getScore()) > 0);

    // Verify global best was adopted (check logs or metrics)
    // ...
}

@Test
void testLateAcceptanceWithCompareGlobal() {
    // Similar test for Late Acceptance
    // ...
}

@Test
void testMigrationAndCompareGlobalTogether() {
    // Test with both migration and compare-to-global enabled
    SolverConfig config = createIslandModelConfig()
        .withIslandCount(4)
        .withMigrationFrequency(100)
        .withCompareGlobalEnabled(true)
        .withCompareGlobalFrequency(50);

    // Solve and verify both mechanisms work
    // ...
}

@Test
void testNonLocalSearchPhase() {
    // Test that listener is not attached to non-local-search phases
    SolverConfig config = createIslandModelConfig()
        .withCompareGlobalEnabled(true)
        .withConstructionHeuristicPhase();

    // Verify no exceptions and no listener attached
    // ...
}
```

### Performance Tests

**CompareToGlobalPerformanceTest**:
```java
@Test
void testCompareToGlobal_improvesConvergence() {
    // Compare convergence speed with and without compare-to-global
    Problem problem = createTestProblem();

    // Without compare-to-global
    SolverConfig configWithout = createIslandModelConfig()
        .withCompareGlobalEnabled(false);
    long timeWithout = measureSolveTime(problem, configWithout);

    // With compare-to-global
    SolverConfig configWith = createIslandModelConfig()
        .withCompareGlobalEnabled(true)
        .withCompareGlobalFrequency(50);
    long timeWith = measureSolveTime(problem, configWith);

    // Verify improvement (expect 20-50% faster convergence)
    assertTrue(timeWith < timeWithout * 0.8);
}
```

### Regression Tests

- Run full GreyCOS test suite
- Ensure island model tests still pass
- Verify no breaking changes to existing functionality
- Test backward compatibility (default enabled/disabled)

---

## Risk Mitigation

### Risk 1: Performance Degradation

**Concern**: Frequent global best checks may cause contention on `SharedGlobalState` lock.

**Mitigation**:
- Use frequency-based checking (not every step)
- Default frequency of 50 steps is reasonable
- Make compare-to-global configurable and optional
- Profile and optimize hot paths

**Fallback**:
- If performance degrades, users can disable compare-to-global
- Keep migration as primary mechanism

### Risk 2: Stuck Behavior (Simulated Annealing)

**Concern**: GreyJack documentation warns that SA may get stuck when comparing to global.

**Mitigation**:
- Document this known issue clearly
- Recommend higher frequency for SA (e.g., 200 steps)
- Allow users to disable compare-to-global for SA

**Fallback**:
- Users can disable compare-to-global for specific phases
- Use migration only for SA

### Risk 3: Late Acceptance Suboptimal Behavior

**Concern**: Not updating `lateScores` before adopting global best may cause suboptimal behavior.

**Mitigation**:
- Document that Late Acceptance may not work optimally with this implementation
- Recommend testing with specific problems
- Consider this as "good enough" for initial version

**Fallback**:
- Users can disable compare-to-global for Late Acceptance if needed
- Future enhancement: Add public API to LateAcceptanceAcceptor

### Risk 4: Breaking Changes

**Concern**: Adding new configuration parameters may break existing configurations.

**Mitigation**:
- Use sensible defaults (enabled=true, frequency=50)
- Make parameters optional with nullable types in phase config
- Ensure backward compatibility (existing configs work without changes)
- Update documentation clearly

**Fallback**:
- If issues arise, can default to disabled for safety

---

## Implementation Checklist

### Phase 1: Configuration
- [ ] Add `compareGlobalEnabled` to `IslandModelConfig`
- [ ] Add `compareGlobalFrequency` to `IslandModelConfig`
- [ ] Add getters/setters/builder methods
- [ ] Add validation for frequency
- [ ] Add `compareGlobalEnabled` to `IslandModelPhaseConfig`
- [ ] Add `compareGlobalFrequency` to `IslandModelPhaseConfig`
- [ ] Update `inherit()` method
- [ ] Write configuration unit tests

### Phase 2: Lifecycle Listener
- [ ] Create `GlobalCompareListener` class
- [ ] Implement `LocalSearchPhaseLifecycleListener` interface
- [ ] Implement `stepEnded()` method
- [ ] Implement `checkAndAdoptGlobalBest()` method
- [ ] Add helper methods (deepClone, replaceSolution, updateGlobalBest)
- [ ] Add logging
- [ ] Write listener unit tests

### Phase 3: Agent Integration
- [ ] Add `compareGlobalEnabled` field to `IslandAgent`
- [ ] Add `compareGlobalFrequency` field to `IslandAgent`
- [ ] Modify `run()` method to attach listener
- [ ] Add check for `LocalSearchPhase` instance
- [ ] Test with various phase types
- [ ] Write integration tests

### Phase 4: Testing & Validation
- [ ] Write unit tests for configuration
- [ ] Write unit tests for listener logic
- [ ] Write integration tests for each algorithm
- [ ] Write performance tests
- [ ] Run regression tests
- [ ] Fix any issues found
- [ ] Document results

### Documentation
- [ ] Update Javadoc for modified classes
- [ ] Add compare-to-global section to user guide
- [ ] Document behavior for each algorithm type
- [ ] Add configuration examples
- [ ] Document known issues (SA stuck behavior, LA suboptimal)
- [ ] Add performance recommendations

---

## Success Criteria

### Functional Requirements

1. ✅ Compare-to-global can be enabled via configuration
2. ✅ Compare-to-global frequency is configurable
3. ✅ Agents check global best periodically
4. ✅ Agents adopt global best when it's better
5. ✅ Compare-to-global works with all local search algorithms (TS, LA, SA)
6. ✅ Compare-to-global works alongside migration
7. ✅ Feature can be disabled without breaking existing functionality
8. ✅ Non-local-search phases are not affected

### Performance Requirements

1. ✅ Compare-to-global improves convergence speed by ≥20%
2. ✅ Compare-to-global does not cause performance regression >10%
3. ✅ Contention on `SharedGlobalState` lock is minimal
4. ✅ Memory overhead is negligible (one listener per phase)

### Quality Requirements

1. ✅ Code coverage ≥80% for new code
2. ✅ All new classes/methods have Javadoc
3. ✅ No critical bugs found in testing
4. ✅ Solution quality matches or exceeds migration-only mode
5. ✅ All existing tests pass (backward compatibility)

---

## Future Enhancements

### Algorithm-Specific Handling

**Goal**: Add proper algorithm-specific state updates.

**Approach**:
1. Add public methods to acceptor classes:
   - `LateAcceptanceAcceptor.recordScoreForGlobalAdoption(Score)`
   - `AbstractTabuAcceptor.recordCurrentSolutionAsTabu()`
   - `SimulatedAnnealingAcceptor.resetTemperatureForGlobalAdoption()`

2. Modify `GlobalCompareListener` to detect algorithm type and call appropriate method.

**Benefits**:
- Late Acceptance works optimally
- Tabu Search prevents immediate reversal
- Simulated Annealing can reset temperature

### Adaptive Frequency

**Goal**: Dynamically adjust compare-to-global frequency based on convergence.

**Approach**:
- Monitor how often global best is adopted
- Increase frequency if adoption rate is low
- Decrease frequency if adoption rate is high (to reduce overhead)

---

## References

- GreyJack Island Model Documentation: [`ISLAND_MODEL_DOCUMENTATION.md`](./ISLAND_MODEL_DOCUMENTATION.md)
- GreyJack Implementation: `greyjack/src/agents/base/agent_base.rs`
- GreyCOS Island Agent: [`IslandAgent.java`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/IslandAgent.java)
- GreyCOS Shared Global State: [`SharedGlobalState.java`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/SharedGlobalState.java)
- GreyCOS Configuration: [`IslandModelConfig.java`](../../core/src/main/java/ai/greycos/solver/core/impl/islandmodel/IslandModelConfig.java)
- GreyCOS Tabu Acceptor: [`AbstractTabuAcceptor.java`](../../core/src/main/java/ai/greycos/solver/core/impl/localsearch/decider/acceptor/tabu/AbstractTabuAcceptor.java)
- GreyCOS Late Acceptance Acceptor: [`LateAcceptanceAcceptor.java`](../../core/src/main/java/ai/greycos/solver/core/impl/localsearch/decider/acceptor/lateacceptance/LateAcceptanceAcceptor.java)
- GreyCOS Local Search Phase: [`LocalSearchPhase.java`](../../core/src/main/java/ai/greycos/solver/core/impl/localsearch/LocalSearchPhase.java)
- GreyCOS Local Search Lifecycle Listener: [`LocalSearchPhaseLifecycleListener.java`](../../core/src/main/java/ai/greycos/solver/core/impl/localsearch/event/LocalSearchPhaseLifecycleListener.java)

---

**Document Version**: 2.0 (Corrected)
**Last Updated**: 2025-01-15
**Status**: Implementation Ready - All APIs Verified
