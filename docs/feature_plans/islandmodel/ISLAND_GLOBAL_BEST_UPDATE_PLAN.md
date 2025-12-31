# Island Model: Global Best Solution Update During Solving

## Problem Statement

Currently, the island model only updates the main solver's score director with the global best solution at the **end** of the island model phase (in `DefaultIslandModelPhase.solve()` lines 106-113). This means:

1. **Stale best solution**: During solving, `solverScope.getBestSolution()` and `solverScope.getBestScore()` return the initial solution, not the actual best found by any island
2. **No intermediate events**: Users don't receive `BestSolutionChangedEvent` notifications during solving when a better solution is found
3. **Inconsistent behavior**: Unlike single-threaded solver which fires events on each improvement, island model is silent until completion

## Current Architecture

```
Island Model Phase
├── SharedGlobalState (tracks global best)
│   └── tryUpdate() - only called by GlobalBestUpdater
├── IslandAgent 0..N
│   ├── islandScope (isolated, has own score director)
│   └── GlobalBestUpdater
│       └── stepEnded() → globalState.tryUpdate()
└── Main Solver Scope (parent)
    └── Best solution only updated at phase end
```

## Proposed Solution

### Overview

Create a `GlobalBestPropagator` that:
1. Observes `SharedGlobalState` for changes
2. Updates the main solver's `solverScope` with global best improvements
3. Fires `BestSolutionChangedEvent` through `SolverEventSupport`

### Architecture

```
Island Model Phase
├── SharedGlobalState
│   ├── observers (List<Consumer<Solution_>>)
│   └── tryUpdate() → notifyObservers()
├── IslandAgent 0..N
│   └── GlobalBestUpdater (pushes local improvements)
└── GlobalBestPropagator (NEW)
    ├── Observes SharedGlobalState
    ├── Updates main solverScope with global best
    └── Fires BestSolutionChangedEvent
```

### Implementation Plan

#### Phase 1: Create GlobalBestPropagator

**File**: `core/src/main/java/ai/greycos/solver/core/impl/islandmodel/GlobalBestPropagator.java`

```java
/**
 * Propagates global best solution updates from SharedGlobalState to main solver scope.
 *
 * <p>This ensures that:
 * <ul>
 *   <li>Main solver's best solution is updated during solving (not just at end)
 *   <li>BestSolutionChangedEvent events are fired for user listeners
 *   <li>Termination criteria based on best score work correctly
 * </ul>
 *
 * @param <Solution_> solution type
 */
public class GlobalBestPropagator<Solution_> implements Consumer<Solution_> {

    private final SharedGlobalState<Solution_> globalState;
    private final SolverScope<Solution_> mainSolverScope;
    private final SolverEventSupport<Solution_> solverEventSupport;
    private final EventProducerId eventProducerId;

    private volatile Solution_ lastKnownBestSolution;
    private volatile Score<?> lastKnownBestScore;

    public GlobalBestPropagator(
            SharedGlobalState<Solution_> globalState,
            SolverScope<Solution_> mainSolverScope,
            SolverEventSupport<Solution_> solverEventSupport,
            EventProducerId eventProducerId) {
        this.globalState = Objects.requireNonNull(globalState);
        this.mainSolverScope = Objects.requireNonNull(mainSolverScope);
        this.solverEventSupport = Objects.requireNonNull(solverEventSupport);
        this.eventProducerId = Objects.requireNonNull(eventProducerId);
    }

    /**
     * Start observing global best changes.
     * Must be called before island agents start solving.
     */
    public void start() {
        globalState.addObserver(this);
    }

    /**
     * Stop observing global best changes.
     * Must be called after island agents complete.
     */
    public void stop() {
        globalState.removeObserver(this);
    }

    @Override
    public void accept(Solution_ newGlobalBest) {
        if (newGlobalBest == null) {
            return;
        }

        var newGlobalBestScore = globalState.getBestScore();
        if (newGlobalBestScore == null) {
            return;
        }

        // Only update if score actually improved
        boolean shouldUpdate = shouldUpdateMainSolverScope(newGlobalBestScore);

        if (shouldUpdate) {
            updateMainSolverScope(newGlobalBest, newGlobalBestScore);
            fireBestSolutionChangedEvent(newGlobalBest);

            lastKnownBestSolution = newGlobalBest;
            lastKnownBestScore = newGlobalBestScore;
        }
    }

    private boolean shouldUpdateMainSolverScope(Score<?> newGlobalBestScore) {
        if (lastKnownBestScore == null) {
            return true; // First update
        }

        @SuppressWarnings("unchecked")
        var comparisonResult = ((Score) newGlobalBestScore).compareTo((Score) lastKnownBestScore);
        return comparisonResult > 0;
    }

    private void updateMainSolverScope(Solution_ newBestSolution, Score<?> newBestScore) {
        // Clone to avoid sharing references with island agents
        var clonedSolution = mainSolverScope.getScoreDirector().cloneSolution(newBestSolution);

        // Update main solver scope
        mainSolverScope.setBestSolution(clonedSolution);

        @SuppressWarnings("unchecked")
        var innerScore = InnerScore.fullyAssigned((Score) newBestScore);
        mainSolverScope.setBestScore(innerScore);

        mainSolverScope.setBestSolutionTimeMillis(mainSolverScope.getClock().millis());
    }

    private void fireBestSolutionChangedEvent(Solution_ newBestSolution) {
        solverEventSupport.fireBestSolutionChanged(
                mainSolverScope,
                eventProducerId,
                newBestSolution);
    }
}
```

#### Phase 2: Modify DefaultIslandModelPhase

**File**: `core/src/main/java/ai/greycos/solver/core/impl/islandmodel/DefaultIslandModelPhase.java`

**Changes**:

1. **Add field for GlobalBestPropagator**:
```java
private GlobalBestPropagator<Solution_> globalBestPropagator;
```

2. **Initialize propagator in solve()** - before creating agents:
```java
@Override
public void solve(SolverScope<Solution_> solverScope) {
    try {
        LOGGER.info("{}Island Model phase ({}) starting with {} islands",
                logIndentation, phaseIndex, islandCount);

        this.solverScope = solverScope;
        globalState.setSolverScope(solverScope);

        var initialSolution = solverScope.getBestSolution();
        var innerScore = solverScope.calculateScore();
        globalState.tryUpdate(initialSolution, innerScore.raw());

        // NEW: Start propagating global best updates to main solver scope
        globalBestPropagator = new GlobalBestPropagator<>(
                globalState,
                solverScope,
                getSolverEventSupport(solverScope),
                new PhaseEventProducerId(getPhaseType(), phaseIndex));
        globalBestPropagator.start();

        createAndRunAgents(solverScope);

        // NEW: Stop propagating after agents complete
        if (globalBestPropagator != null) {
            globalBestPropagator.stop();
        }

        var globalBest = globalState.getBestSolution();
        if (globalBest != null) {
            LOGGER.info("{}Island Model phase ({}) ended. Global best score: {}",
                    logIndentation, phaseIndex, globalState.getBestScore());
            solverScope.setInitialSolution(globalBest);
        } else {
            LOGGER.warn("{}Island Model phase ({}) ended with no global best solution",
                    logIndentation, phaseIndex);
        }

    } catch (Exception e) {
        LOGGER.error("{}Island Model phase ({}) encountered error",
                logIndentation, phaseIndex, e.getMessage(), e);
        throw e;
    } finally {
        // Ensure propagator is stopped even on exception
        if (globalBestPropagator != null) {
            globalBestPropagator.stop();
        }
    }
}
```

3. **Add helper method to get SolverEventSupport**:
```java
private SolverEventSupport<Solution_> getSolverEventSupport(SolverScope<Solution_> solverScope) {
    var solver = solverScope.getSolver();
    if (solver instanceof AbstractSolver<Solution_>) {
        var abstractSolver = (AbstractSolver<Solution_>) solver;
        return abstractSolver.getSolverEventSupport();
    }
    throw new IllegalStateException(
            "Solver must be an AbstractSolver to access SolverEventSupport");
}
```

#### Phase 3: Expose SolverEventSupport in AbstractSolver

**File**: `core/src/main/java/ai/greycos/solver/core/impl/solver/AbstractSolver.java`

**Add getter**:
```java
public SolverEventSupport<Solution_> getSolverEventSupport() {
    return solverEventSupport;
}
```

#### Phase 4: Add EventProducerId for Island Model

**File**: `core/src/main/java/ai/greycos/solver/core/impl/phase/event/PhaseEventProducerId.java`

Ensure island model has a unique event producer ID (it likely already exists via `getPhaseType()`).

## Benefits

1. **Real-time best solution**: `solverScope.getBestSolution()` returns actual global best during solving
2. **Event notifications**: Users receive `BestSolutionChangedEvent` during solving (not just at end)
3. **Correct termination**: Termination criteria based on best score (e.g., `UnimprovedTimeMillisSpentTermination`) work correctly
4. **Consistent behavior**: Island model behaves like single-threaded solver from user's perspective
5. **No performance impact**: Updates only occur when global best actually improves (low contention)

## Thread Safety Considerations

1. **SharedGlobalState observers**: Already thread-safe (uses `CopyOnWriteArrayList`)
2. **SolverScope updates**: Main solver scope is only accessed by island model phase thread (not by island agents), so no race condition
3. **GlobalBestPropagator**: Uses volatile fields for last known best to handle concurrent updates safely

## Testing Strategy

1. **Unit tests**:
   - Test `GlobalBestPropagator` updates main solver scope correctly
   - Test event firing on global best improvements
   - Test no updates when score doesn't improve

2. **Integration tests**:
   - Verify `BestSolutionChangedEvent` is fired during island model solving
   - Verify termination criteria work correctly with global best updates
   - Verify final best solution matches global best

3. **Performance tests**:
   - Measure overhead of global best propagation (should be negligible)
   - Verify no lock contention issues with multiple islands

## Backward Compatibility

This change is **fully backward compatible**:
- No changes to public APIs
- No changes to configuration
- Only adds new functionality (real-time best solution updates)
- Existing code that reads best solution at end still works
