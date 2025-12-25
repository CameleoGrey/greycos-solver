# Best Solution Throttling Feature - Implementation Plan for Greycos

## Executive Summary

This document outlines the plan to reimplement Timefold's `ThrottlingBestSolutionEventConsumer` feature in the greycos solver. The feature prevents system overload by throttling best solution events during rapid solution improvement phases.

## Current State Analysis

### Greycos Architecture
- **SolverManager**: Manages multiple solver jobs with thread pools for solver and consumer threads
- **SolverJobBuilder**: Fluent API for configuring solver jobs
- **ConsumerSupport**: Handles event consumption on separate consumer thread using `ExecutorService`
- **Event Types**: `NewBestSolutionEvent`, `FinalBestSolutionEvent`, `FirstInitializedSolutionEvent`, `SolverJobStartedEvent`
- **Event Flow**: Solver thread → BestSolutionHolder → ConsumerSupport → Consumer thread → User consumer

### Key Files Identified
- `core/src/main/java/ai/greycos/solver/core/api/solver/SolverManager.java` - Main API
- `core/src/main/java/ai/greycos/solver/core/api/solver/SolverJobBuilder.java` - Builder interface
- `core/src/main/java/ai/greycos/solver/core/impl/solver/DefaultSolverJobBuilder.java` - Builder implementation
- `core/src/main/java/ai/greycos/solver/core/impl/solver/ConsumerSupport.java` - Event consumption logic
- `core/src/main/java/ai/greycos/solver/core/api/solver/event/NewBestSolutionEvent.java` - Event interface

## Feature Requirements (from Official Docs)

1. **Throttle Rate**: Configurable time interval (e.g., 1 event per second)
2. **Skip-Ahead Logic**: If multiple events arrive during interval, only deliver the last one
3. **Final Delivery**: Always deliver the last event when SolverJob terminates
4. **Exception Handling**: Consumer exceptions don't affect throttle counting
5. **Thread Safety**: Event delivery delayed if consumer thread unavailable
6. **Dual Delivery**: Both intermediate and final consumers receive final solution

## Implementation Plan

### Phase 1: Core Throttling Consumer Class

**File**: `core/src/main/java/ai/greycos/solver/core/impl/solver/ThrottlingBestSolutionEventConsumer.java`

**Design**:
```java
public final class ThrottlingBestSolutionEventConsumer<Solution_> 
    implements Consumer<NewBestSolutionEvent<Solution_>> {
    
    private final Consumer<NewBestSolutionEvent<Solution_>> delegate;
    private final Duration throttleDuration;
    private final AtomicReference<NewBestSolutionEvent<Solution_>> pendingEvent;
    private final AtomicLong lastDeliveryTime;
    private final ScheduledExecutorService scheduler;
    private volatile boolean terminated;
    
    // Factory method
    public static <Solution_> ThrottlingBestSolutionEventConsumer<Solution_> of(
        Consumer<NewBestSolutionEvent<Solution_>> delegate,
        Duration throttleDuration)
}
```

**Key Behaviors**:
- Store incoming events in `pendingEvent` (overwrites previous)
- Use `ScheduledExecutorService` to schedule delivery after throttle interval
- On delivery: check if newer event arrived, if yes skip (delegate already has latest)
- On termination: immediately deliver pending event regardless of throttle
- Thread-safe using atomic references and volatile flags

**Complexity**:
- Time: O(1) per event (simple reference updates)
- Space: O(1) (single pending event reference)

### Phase 2: Integration with SolverJobBuilder

**File**: `core/src/main/java/ai/greycos/solver/core/api/solver/SolverJobBuilder.java`

**Additions**:
```java
/**
 * Sets a throttled best solution consumer.
 * The delegate consumer will receive at most one event per throttleDuration.
 * If multiple events arrive during the interval, only the last is delivered.
 * The final best solution is always delivered regardless of throttle.
 *
 * @param delegate the actual consumer to call
 * @param throttleDuration minimum time between event deliveries
 * @return this
 */
@NonNull
default SolverJobBuilder<Solution_, ProblemId_> withThrottledBestSolutionEventConsumer(
    @NonNull Consumer<NewBestSolutionEvent<Solution_>> delegate,
    @NonNull Duration throttleDuration) {
    var throttledConsumer = ThrottlingBestSolutionEventConsumer.of(delegate, throttleDuration);
    return withBestSolutionEventConsumer(throttledConsumer);
}
```

**Rationale**: Default method keeps implementation in one place, minimal interface change.

### Phase 3: Termination Handling

**Challenge**: Need to ensure final event is delivered when solver terminates.

**Approach 1: Explicit Termination Method** (Preferred)
```java
public final class ThrottlingBestSolutionEventConsumer<Solution_> ... {
    public void terminateAndDeliverPending() {
        terminated = true;
        scheduler.shutdown();
        var event = pendingEvent.get();
        if (event != null) {
            delegate.accept(event);
        }
    }
}
```

**Integration Point**: Modify `ConsumerSupport.consumeFinalBestSolution()` to detect throttled consumer and call termination method.

**Approach 2: Final Solution Consumer Integration**
The throttled consumer should also implement `Consumer<FinalBestSolutionEvent<Solution_>>` to ensure final delivery.

**Decision**: Use Approach 1 - explicit termination method is cleaner and follows separation of concerns.

### Phase 4: ConsumerSupport Integration

**File**: `core/src/main/java/ai/greycos/solver/core/impl/solver/ConsumerSupport.java`

**Modifications**:
1. Detect if `bestSolutionConsumer` is a `ThrottlingBestSolutionEventConsumer`
2. In `consumeFinalBestSolution()`, call `terminateAndDeliverPending()` before final consumer
3. Ensure scheduler cleanup happens before releasing semaphores

**Code Changes**:
```java
void consumeFinalBestSolution(Solution_ finalBestSolution) {
    try {
        acquireAll();
    } catch (InterruptedException e) {
        // ... existing error handling
    }
    
    // Terminate throttled consumer to ensure final delivery
    if (bestSolutionConsumer instanceof ThrottlingBestSolutionEventConsumer) {
        ((ThrottlingBestSolutionEventConsumer<Solution_>) bestSolutionConsumer)
            .terminateAndDeliverPending();
    }
    
    // ... rest of existing logic
}
```

### Phase 5: Exception Handling

**Requirement**: "If your consumer throws an exception, we will still count the event as delivered."

**Implementation**:
```java
private void deliverEvent(NewBestSolutionEvent<Solution_> event) {
    try {
        delegate.accept(event);
    } catch (Throwable t) {
        // Log but don't affect throttle timing
        // Exception is handled by ConsumerSupport's exception handler
    } finally {
        lastDeliveryTime.set(System.currentTimeMillis());
    }
}
```

### Phase 6: Thread Safety Considerations

**Concerns**:
1. Multiple events arriving simultaneously
2. Delivery execution while new event arrives
3. Termination while delivery in progress

**Solutions**:
1. Use `AtomicReference` for pending event (write wins)
2. Use `volatile` for terminated flag
3. Use `synchronized` for critical delivery section if needed
4. Scheduler shutdown with `shutdownNow()` cancels pending deliveries

**Thread Safety Analysis**:
- `pendingEvent`: AtomicReference - thread-safe
- `lastDeliveryTime`: AtomicLong - thread-safe
- `terminated`: volatile - visible across threads
- `delegate`: immutable after construction - safe
- `scheduler`: `ScheduledExecutorService` - handles concurrent scheduling

### Phase 7: Resource Cleanup

**Requirements**:
- Clean up scheduler on termination
- Prevent memory leaks
- Handle edge cases (no events, immediate termination)

**Implementation**:
```java
@Override
public void close() {
    terminated = true;
    scheduler.shutdown();
    try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
            scheduler.shutdownNow();
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        scheduler.shutdownNow();
    }
}
```

**Integration**: Ensure `ConsumerSupport.close()` calls this if consumer is `AutoCloseable`.

## Testing Strategy

### Unit Tests

**File**: `core/src/test/java/ai/greycos/solver/core/impl/solver/ThrottlingBestSolutionEventConsumerTest.java`

**Test Cases**:
1. **Basic Throttling**: Multiple events within interval, only last delivered
2. **Interval Boundary**: Events just before/after interval
3. **Final Delivery**: Termination delivers pending event
4. **Exception Handling**: Consumer exception doesn't break throttling
5. **No Events**: Termination with no events
6. **Concurrent Events**: Multiple threads submitting events
7. **Skip-Ahead**: New event arrives during delivery
8. **Resource Cleanup**: Scheduler properly shutdown

### Integration Tests

**File**: `core/src/test/java/ai/greycos/solver/core/api/solver/SolverManagerThrottlingTest.java`

**Test Cases**:
1. **End-to-End**: Full solver job with throttling
2. **Rapid Improvement**: Simulate fast solution improvements
3. **Final Solution**: Both intermediate and final consumers receive final solution
4. **Multiple Jobs**: Concurrent jobs with different throttle rates
5. **Problem Changes**: Throttling with problem changes

### Performance Tests

**File**: `core/src/test/java/ai/greycos/solver/core/impl/solver/ThrottlingPerformanceTest.java`

**Metrics**:
- Overhead of throttling vs direct delivery
- Memory usage under high event rate
- Thread pool contention

## Implementation Order

1. **Phase 1**: Create `ThrottlingBestSolutionEventConsumer` class
2. **Phase 6**: Implement thread-safe throttling logic
3. **Phase 7**: Add resource cleanup
4. **Phase 5**: Add exception handling
5. **Phase 3**: Add termination handling
6. **Phase 4**: Integrate with `ConsumerSupport`
7. **Phase 2**: Add builder method to `SolverJobBuilder`
8. **Testing**: Write unit tests for each phase
9. **Integration**: Write integration tests
10. **Documentation**: Update javadoc and examples

## Design Decisions

### Decision 1: ScheduledExecutorService vs Simple Delay
**Choice**: `ScheduledExecutorService`
**Rationale**: 
- More precise timing
- Built-in thread management
- Can cancel pending deliveries
- Standard Java concurrency primitive

### Decision 2: Overwrite vs Queue Pending Events
**Choice**: Overwrite (keep only latest)
**Rationale**: 
- Matches spec: "only the last event will be delivered"
- Simpler implementation
- Lower memory footprint
- Better for rapid improvement scenarios

### Decision 3: Separate Termination Method vs Auto-detection
**Choice**: Explicit termination method
**Rationale**:
- Clear intent
- Easier to test
- Follows explicit over implicit principle
- No need for reflection or type checking

### Decision 4: Factory Method vs Constructor
**Choice**: Static factory method `of()`
**Rationale**:
- Matches Timefold API
- More readable
- Can add validation
- Follows Java best practices

## Risk Mitigation

### Risk 1: Thread Starvation
**Mitigation**: 
- Use daemon threads for scheduler
- Set reasonable timeout on shutdown
- Monitor scheduler queue size

### Risk 2: Memory Leaks
**Mitigation**:
- Ensure scheduler shutdown in all code paths
- Use try-finally blocks
- Add close() method
- Test with long-running jobs

### Risk 3: Timing Issues
**Mitigation**:
- Use `System.nanoTime()` for precise timing
- Add tolerance in tests
- Document timing behavior

### Risk 4: Integration Complexity
**Mitigation**:
- Keep changes minimal to ConsumerSupport
- Use type checking with `instanceof`
- Add comprehensive integration tests
- Document integration points clearly

## Performance Considerations

### Time Complexity
- Event acceptance: O(1) - atomic reference update
- Scheduled delivery: O(1) - scheduler overhead
- Termination: O(1) - single event delivery

### Space Complexity
- Per consumer: O(1) - single event reference
- Scheduler queue: O(k) where k = pending deliveries (typically 1)

### Overhead
- Minimal: one extra object reference per event
- Scheduler thread: one per throttled consumer
- Memory: negligible (single pending event)

## API Examples

### Basic Usage
```java
SolverManager<TestdataSolution, Long> solverManager = 
    SolverManager.create(solverConfig);

var throttledConsumer = ThrottlingBestSolutionEventConsumer.of(
    event -> {
        // Handle best solution
        System.out.println("Score: " + event.solution().getScore());
    },
    Duration.ofSeconds(1) // Throttle to 1 event per second
);

solverManager.solveBuilder()
    .withProblemId(1L)
    .withProblem(problem)
    .withBestSolutionEventConsumer(throttledConsumer)
    .run();
```

### Using Builder Method
```java
solverManager.solveBuilder()
    .withProblemId(1L)
    .withProblem(problem)
    .withThrottledBestSolutionEventConsumer(
        event -> handleBestSolution(event),
        Duration.ofMillis(500)
    )
    .run();
```

### With Final Consumer
```java
solverManager.solveBuilder()
    .withProblemId(1L)
    .withProblem(problem)
    .withThrottledBestSolutionEventConsumer(
        event -> trackProgress(event),
        Duration.ofSeconds(1)
    )
    .withFinalBestSolutionEventConsumer(
        event -> saveFinalSolution(event.solution())
    )
    .run();
```

## Documentation Requirements

1. **Javadoc**: Comprehensive documentation on all public methods
2. **Package-info**: Overview of throttling feature
3. **User Guide**: Section on when and how to use throttling
4. **Examples**: Code examples in documentation
5. **Migration Guide**: Note for Timefold users

## Success Criteria

1. ✅ Throttling works correctly with configurable intervals
2. ✅ Only last event delivered during interval
3. ✅ Final event always delivered on termination
4. ✅ Consumer exceptions don't break throttling
5. ✅ Thread-safe under concurrent access
6. ✅ No memory leaks in long-running scenarios
7. ✅ Minimal performance overhead
8. ✅ Clean API matching Timefold patterns
9. ✅ Comprehensive test coverage
10. ✅ Clear documentation

## Future Enhancements

1. **Adaptive Throttling**: Adjust rate based on system load
2. **Metrics**: Expose throttling statistics (events dropped, delivered, etc.)
3. **Backpressure**: Signal to solver to slow down
4. **Priority Events**: Allow certain events to bypass throttle
5. **Composite Throttling**: Multiple throttle policies per consumer

## References

- Timefold Documentation: `text_utils/features/best_solution_throttling/official_docs_description.md`
- Greycos Event System: `core/src/main/java/ai/greycos/solver/core/api/solver/event/`
- ConsumerSupport: `core/src/main/java/ai/greycos/solver/core/impl/solver/ConsumerSupport.java`
- Java Concurrency: `java.util.concurrent` package
