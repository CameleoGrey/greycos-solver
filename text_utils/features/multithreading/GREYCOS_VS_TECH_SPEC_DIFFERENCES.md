# Greycos vs Tech Spec Differences: Multithreading Implementation

This document analyzes the differences between the greycos implementation and the tech specification for the multithreading feature.

## Executive Summary

The greycos implementation follows the tech spec closely but introduces several architectural improvements and simplifications. Key differences include:

1. **Termination architecture**: Uses interface-based design instead of concrete bridge classes
2. **Shutdown behavior**: Faster thread pool shutdown (1 second vs 60 seconds)
3. **Exception handling**: More robust error handling with parent/child score director tracking
4. **API design**: Uses `OptionalInt` for better null-safety
5. **Move execution**: Uses `executeTemporaryMove` API instead of `doAndProcessMove`
6. **Thread naming**: Custom branding ("Greycos-" vs "OptaPool-")

## Detailed Differences

### 1. Termination Infrastructure

#### Tech Spec Approach
The tech spec defines two concrete classes:
- `ChildThreadPlumbingTermination`: Handles child thread termination with `daemon` flag and `terminatedEarly` volatile boolean
- `PhaseToSolverTerminationBridge`: Bridges phase termination to solver termination with special handling for `PART_THREAD`

#### Greycos Implementation
Greycos uses a more flexible interface-based approach:
- `ChildThreadSupportingTermination<Solution_, Scope_>`: Interface with `createChildThreadTermination()` method
- `PhaseTermination.bridge()`: Factory method to create the bridge
- No explicit `ChildThreadPlumbingTermination` class; instead, implementations support the interface

**Impact**: The interface-based design is more flexible and allows different termination strategies without concrete class proliferation.

**Code Reference**:
```java
// Tech spec (concrete class)
public class ChildThreadPlumbingTermination<Solution_> implements Termination<Solution_> {
    private final boolean daemon;
    private volatile boolean terminatedEarly = false;
    // ...
}

// Greycos (interface)
public interface ChildThreadSupportingTermination<Solution_, Scope_> {
    Termination<Solution_> createChildThreadTermination(
        Scope_ scope, ChildThreadType childThreadType);
}
```

### 2. Thread Pool Shutdown Timeout

#### Tech Spec
```java
public static void shutdownAwaitOrKill(ExecutorService executor, ...) {
    // ...
    if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
        LOGGER.warn("{} phase's thread pool did not terminate in the specified time...", ...);
        executor.shutdownNow();
    }
}
```

#### Greycos Implementation
```java
public static void shutdownAwaitOrKill(ExecutorService executor, ...) {
    // ...
    final int awaitingSeconds = 1;
    if (!executor.awaitTermination(awaitingSeconds, TimeUnit.SECONDS)) {
        LOGGER.error("{}'s ExecutorService didn't terminate within timeout ({} seconds).", ...);
    }
    // Always calls shutdownNow() in finally block
}
```

**Impact**: Greycos uses a 1-second timeout vs 60 seconds in tech spec. This is more aggressive and may not give threads enough time to clean up gracefully. The comment suggests this is intentional for faster shutdown.

### 3. MoveThreadRunner Enhancements

#### Parent Score Director Tracking
**Greycos Only**: Adds `parentScoreDirector` field to track the parent score director separately from child:

```java
private InnerScoreDirector<Solution_, Score_> scoreDirector = null;
private InnerScoreDirector<Solution_, Score_> parentScoreDirector = null;
```

This allows proper cleanup of both parent and child score directors in the finally block.

#### Exception Handling
**Greycos Only**: More sophisticated exception handling in `SetupOperation` processing:

```java
try {
    parentScoreDirector = setupOperation.getScoreDirector();
    scoreDirector = parentScoreDirector.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
    // ...
} catch (RuntimeException | Error throwable) {
    exceptionThrown = true;
    // Close child score director if setup fails
    if (scoreDirector != null) {
        try {
            scoreDirector.close();
        } catch (Exception e) {
            LOGGER.warn("...failed to close score director during setup.", e);
        }
    }
    // Also close parent score director on failure
    if (parentScoreDirector != null && parentScoreDirector != scoreDirector) {
        try {
            parentScoreDirector.close();
        } catch (Exception e) {
            LOGGER.warn("...failed to close parent score director during setup.", e);
        }
    }
    throw throwable;
}
```

**Finally Block**: Greycos has conditional parent cleanup:
```java
finally {
    // Close child score director
    if (scoreDirector != null) {
        try {
            scoreDirector.close();
        } catch (Exception e) {
            LOGGER.warn("...failed to close score director.", e);
        }
    }
    // Close parent score director only if:
    // 1. It's different from child, AND
    // 2. No exception was thrown during setup (parent already closed in catch block)
    if (parentScoreDirector != null && parentScoreDirector != scoreDirector && !exceptionThrown) {
        try {
            parentScoreDirector.close();
        } catch (Exception e) {
            LOGGER.warn("...failed to close parent score director.", e);
        }
    }
}
```

**Impact**: Greycos has more robust resource cleanup and prevents double-closing of score directors.

### 4. Move Execution API

#### Tech Spec
```java
Score<?> score = scoreDirector.doAndProcessMove(move, assertMoveScoreFromScratch);
if (assertExpectedUndoMoveScore) {
    scoreDirector.assertExpectedUndoMoveScore(move, lastStepScore);
}
```

#### Greycos Implementation
```java
var score = scoreDirector.executeTemporaryMove(move, assertMoveScoreFromScratch);
if (score == null) {
    // Fallback to a default score if the mock returns null
    score = scoreDirector.calculateScore();
}
if (assertExpectedUndoMoveScore) {
    // Note: assertExpectedUndoMoveScore is not applicable in move threads
    // as they don't have access to the proper lifecycle context
}
```

**Impact**: Greycos uses `executeTemporaryMove` which may be a newer API. It also handles null score fallback and explicitly documents why `assertExpectedUndoMoveScore` is not applicable in move threads.

### 5. MoveThreadCountResolver API

#### Tech Spec
```java
protected Integer resolveMoveThreadCount(String moveThreadCount) {
    // ...
    return resolvedMoveThreadCount; // Returns Integer (nullable)
}
```

#### Greycos Implementation
```java
protected OptionalInt resolveMoveThreadCount(String moveThreadCount) {
    // ...
    return OptionalInt.empty(); // or OptionalInt.of(resolvedMoveThreadCount);
}
```

**Impact**: Greycos uses `OptionalInt` which is more explicit about nullability and prevents NPEs. This is a better API design.

### 6. Thread Naming Convention

#### Tech Spec
```java
namePrefix = "OptaPool-" + poolNumber.getAndIncrement() + "-" + threadPrefix + "-";
```

#### Greycos Implementation
```java
namePrefix = "Greycos-" + poolNumber.getAndIncrement() + "-" + threadPrefix + "-";
```

**Impact**: Purely cosmetic difference for branding purposes.

### 7. OrderByMoveIndexBlockingQueue Implementation

#### Tech Spec (Missing Implementation Details)
The missing implementation details document suggests adding synchronization:
```java
private final Object queueLock = new Object();

public void startNextStep(int stepIndex) {
    synchronized (queueLock) {
        nextStepIndex.set(stepIndex);
        nextMoveIndex.set(0);
    }
}
```

#### Greycos Implementation
Uses the underlying `ArrayBlockingQueue` which is already thread-safe:
```java
public void startNextStep(int stepIndex) {
    nextStepIndex.set(stepIndex);
    nextMoveIndex.set(0);
}
```

**Impact**: Greycos relies on `AtomicInteger` for `nextStepIndex` and `nextMoveIndex`, and `ArrayBlockingQueue` for the queue itself, which are already thread-safe. No additional synchronization is needed.

### 8. MultiThreaded Deciders - Move Selector vs Move Repository

#### Tech Spec
```java
Iterator<Move<Solution_>> moveIterator = moveSelector.iterator();
```

#### Greycos Implementation
```java
Iterator<?> moveIterator = moveRepository.iterator();
// Later:
var move = moveIterator.next();
@SuppressWarnings("unchecked")
var legacyMove = MoveAdapters.toLegacyMove(
    (ai.greycos.solver.preview.api.move.Move<Solution_>) move);
```

**Impact**: Greycos uses a newer `MoveRepository` abstraction that wraps the new preview API move types, requiring adaptation to legacy move types via `MoveAdapters.toLegacyMove()`. This suggests greycos is in transition between move APIs.

### 9. Step Index Validation

#### Tech Spec
```java
if (stepIndex != result.getStepIndex()) {
    throw new IllegalStateException("...");
}
```

#### Greycos Implementation (Local Search)
```java
// Allow stepIndex to be -1 (initial state) or match the expected stepIndex
if (stepIndex != -1 && stepIndex != result.getStepIndex()) {
    throw new IllegalStateException("...");
}
```

**Impact**: Greycos allows `stepIndex` to be `-1` for initial state, which is more flexible and prevents false positives during initialization.

### 10. Configuration Integration

Both implementations properly integrate multithreading configuration into `SolverConfig`, but greycos has:

- Proper XML element ordering in `@XmlType` annotation
- Full inheritance support in `inherit()` method
- Complete with-methods for fluent API

**Impact**: Greycos has better XML serialization support and configuration inheritance.

### 11. HeuristicConfigPolicy Thread Factory

Both implementations have `buildThreadFactory()` method, but greycos uses modern switch expression:

```java
var threadPrefix = switch (childThreadType) {
    case MOVE_THREAD -> "MoveThread";
    case PART_THREAD -> "PartThread";
};
```

**Impact**: Modern Java syntax, more concise.

### 12. Missing Components from Tech Spec

The following components mentioned in the tech spec are **not found** in greycos:

1. **ChildThreadPlumbingTermination class**: Replaced by interface-based design
2. **PhaseToSolverTerminationBridge class**: Replaced by `PhaseTermination.bridge()` factory method
3. **Memory monitoring**: No evidence of memory monitoring utilities like `MemoryMonitor`
4. **Performance metrics**: No evidence of performance monitoring utilities like `PerformanceMetrics`
5. **Error recovery manager**: No evidence of `ErrorRecoveryManager`
6. **Adaptive thread pool manager**: No evidence of `AdaptiveThreadPoolManager`
7. **Runtime configuration manager**: No evidence of `RuntimeConfigurationManager`

**Impact**: These components may be planned for future implementation or may not be needed for the current architecture.

## Summary Table

| Component | Tech Spec | Greycos | Impact |
|-----------|------------|-----------|---------|
| Termination architecture | Concrete classes | Interface-based | More flexible |
| Shutdown timeout | 60 seconds | 1 second | Faster but potentially less graceful |
| Parent score director tracking | No | Yes | Better resource cleanup |
| Exception handling | Basic | Sophisticated | More robust |
| Move execution API | `doAndProcessMove` | `executeTemporaryMove` | Newer API |
| MoveThreadCountResolver return type | `Integer` | `OptionalInt` | Better null-safety |
| Thread naming | "OptaPool-" | "Greycos-" | Cosmetic |
| Queue synchronization | Manual lock | Relies on `ArrayBlockingQueue` | Simpler, thread-safe by design |
| Move selector | `MoveSelector` | `MoveRepository` with adapters | Newer API in transition |
| Step index validation | Strict | Allows -1 for init | More flexible |
| XML support | Not specified | Full support | Better configuration |
| Memory/performance monitoring | Mentioned | Not implemented | Missing feature |
| Error recovery | Mentioned | Not implemented | Missing feature |

## Recommendations

1. **Consider increasing shutdown timeout**: The 1-second timeout may be too aggressive for production use. Consider making it configurable.

2. **Document the API transition**: The use of `MoveRepository` with adapters suggests an ongoing transition. Document this for users.

3. **Consider implementing missing features**: Memory monitoring, performance metrics, and error recovery could be valuable additions.

4. **Keep the interface-based termination design**: It's more flexible than the concrete class approach in the tech spec.

5. **Document the stepIndex = -1 behavior**: This is a useful flexibility but should be well-documented.

## Conclusion

The greycos implementation is more modern and robust than the tech spec in several areas:
- Better API design with `OptionalInt`
- More sophisticated exception handling
- Better resource cleanup
- Interface-based termination architecture
- Modern Java syntax

However, some advanced features mentioned in the tech spec (memory monitoring, performance metrics, error recovery) are not yet implemented in greycos. The faster shutdown timeout may also need reconsideration for production use.
