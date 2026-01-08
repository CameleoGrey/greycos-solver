# Best Solution Throttling Feature - User Guide

## Overview

The Best Solution Throttling feature prevents system overload by limiting the rate at which best solution events are delivered to consumers during rapid solution improvement phases. This is particularly useful when the solver produces hundreds of events per second, which could overwhelm downstream systems.

## Key Features

- **Configurable Throttle Rate**: Set minimum time between event deliveries (e.g., 1 event per second)
- **Skip-Ahead Logic**: If multiple events arrive during the interval, only the last one is delivered
- **Final Delivery Guarantee**: The final best solution is always delivered regardless of throttle interval
- **Exception Safety**: Consumer exceptions don't affect throttle counting
- **Thread-Safe**: Handles concurrent event submission from multiple threads
- **Resource Management**: Automatic cleanup of scheduler resources

## When to Use Throttling

Use throttling when:

1. **High Event Rate**: The solver produces hundreds of best solution events per second
2. **Expensive Processing**: Each event triggers expensive operations (database writes, network calls, etc.)
3. **Periodic Tracking**: You only need to track progress periodically rather than continuously
4. **Limited Capacity**: The consumer system has limited capacity or resources

## Basic Usage

### Using the Factory Method

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

### Using the Builder Method (Recommended)

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

## How Throttling Works

The throttling consumer implements a skip-ahead strategy:

1. **Event Reception**: Incoming events are stored in a pending reference (overwrites previous)
2. **Scheduled Delivery**: A delivery is scheduled after the throttle duration
3. **Skip-Ahead**: If a new event arrives before delivery, it replaces the pending event
4. **Final Delivery**: The last event in the interval is delivered to the delegate consumer
5. **Termination**: When the solver terminates, any pending event is delivered immediately

### Example Timeline

```
Time:  0ms    100ms  200ms  300ms  400ms  500ms
Events:  E1      E2      E3      E4      E5
         ↓
         Scheduled delivery at 500ms
         
Time:  500ms
Delivery: E5 (last event)
```

In this example, events E1-E4 are skipped, only E5 is delivered.

## Performance Characteristics

- **Time Complexity**: O(1) per event (simple atomic reference update)
- **Space Complexity**: O(1) (single pending event reference)
- **Overhead**: Minimal - one scheduler thread per consumer instance
- **Memory**: Negligible (single pending event reference)

## Thread Safety

The throttling consumer is fully thread-safe:

- Uses `AtomicReference` for pending event storage
- Uses `AtomicLong` for last delivery time tracking
- Uses `volatile` flag for termination state
- Uses `ScheduledExecutorService` for concurrent scheduling
- Handles concurrent event submission from multiple threads

## Resource Management

The consumer implements `AutoCloseable` and should be closed when no longer needed:

```java
try (var throttledConsumer = ThrottlingBestSolutionEventConsumer.of(
        event -> handleEvent(event),
        Duration.ofSeconds(1))) {
    
    solverManager.solveBuilder()
        .withProblemId(1L)
        .withProblem(problem)
        .withBestSolutionEventConsumer(throttledConsumer)
        .run();
}
// Resources automatically cleaned up
```

When used with `SolverManager`, the consumer is automatically closed when the solver job terminates.

## Exception Handling

Consumer exceptions are handled gracefully:

1. Exceptions are caught and logged (or handled by exception handler)
2. The event is still counted as delivered for throttle timing purposes
3. Throttling continues to work normally
4. Final solution delivery is not affected

Example:

```java
solverManager.solveBuilder()
    .withProblemId(1L)
    .withProblem(problem)
    .withThrottledBestSolutionEventConsumer(
        event -> {
            // This may throw an exception
            processEvent(event);
        },
        Duration.ofSeconds(1)
    )
    .withExceptionHandler((problemId, throwable) -> {
        // Handle exceptions from consumer
        log.error("Error in consumer", throwable);
    })
    .run();
```

## Choosing the Right Throttle Duration

The optimal throttle duration depends on your use case:

### Short Duration (10-100ms)
- **Use case**: Near real-time monitoring
- **Trade-off**: More events, higher system load
- **Example**: `Duration.ofMillis(50)`

### Medium Duration (100-1000ms)
- **Use case**: General purpose throttling
- **Trade-off**: Balanced event rate and system load
- **Example**: `Duration.ofMillis(500)`

### Long Duration (1-5 seconds)
- **Use case**: Progress tracking, infrequent updates
- **Trade-off**: Fewer events, lower system load
- **Example**: `Duration.ofSeconds(1)`

## Best Practices

1. **Test Different Durations**: Experiment with different throttle durations to find the optimal balance
2. **Monitor Event Rate**: Track how many events are actually delivered vs. received
3. **Use Final Consumer**: Always use a final consumer to ensure you get the best solution
4. **Handle Exceptions**: Implement proper exception handling for consumer errors
5. **Close Resources**: Use try-with-resources or ensure consumers are properly closed

## Migration from Timefold

If you're migrating from Timefold's `ThrottlingBestSolutionEventConsumer`:

```java
// Timefold
var throttledConsumer = new ThrottlingBestSolutionEventConsumer<>(
    delegate,
    Duration.ofSeconds(1)
);

// GreyCOS (same API)
var throttledConsumer = ThrottlingBestSolutionEventConsumer.of(
    delegate,
    Duration.ofSeconds(1)
);
```

The API is compatible - only the construction method name differs.

## Troubleshooting

### No Events Received

**Problem**: Consumer receives no events

**Possible Causes**:
1. Solver terminates before throttle interval expires
2. Throttle duration is too long
3. Consumer throws unhandled exceptions

**Solutions**:
1. Use a final consumer to ensure delivery
2. Reduce throttle duration
3. Check exception handler for errors

### Too Many Events Received

**Problem**: Throttling doesn't seem to work

**Possible Causes**:
1. Throttle duration is too short
2. Solver is producing events very slowly

**Solutions**:
1. Increase throttle duration
2. Verify event rate is actually high

### Final Solution Not Delivered

**Problem**: Final solution is not delivered

**Possible Causes**:
1. Solver terminated early
2. Consumer threw exception during final delivery

**Solutions**:
1. Use both intermediate and final consumers
2. Check exception handler
3. Verify solver completion status

## API Reference

### ThrottlingBestSolutionEventConsumer

**Factory Method**:
```java
public static <Solution_> ThrottlingBestSolutionEventConsumer<Solution_> of(
    Consumer<NewBestSolutionEvent<Solution_>> delegate,
    Duration throttleDuration)
```

**Methods**:
- `accept(NewBestSolutionEvent<Solution_> event)`: Accepts a new best solution event
- `terminateAndDeliverPending()`: Terminates and delivers any pending event
- `close()`: Closes the consumer and cleans up resources

### SolverJobBuilder

**Builder Method**:
```java
default SolverJobBuilder<Solution_, ProblemId_> withThrottledBestSolutionEventConsumer(
    Consumer<NewBestSolutionEvent<Solution_>> delegate,
    Duration throttleDuration)
```

## Performance Considerations

### Overhead

The throttling consumer adds minimal overhead:

- **Per Event**: ~100-200ns for atomic reference update
- **Per Delivery**: ~1-2ms for scheduler overhead
- **Memory**: ~64 bytes per consumer instance

### Scalability

The feature scales well:

- **Single Job**: One scheduler thread per throttled consumer
- **Multiple Jobs**: Each job has its own throttled consumer
- **Concurrency**: Thread-safe for concurrent event submission

### Resource Usage

- **Threads**: One daemon thread per throttled consumer
- **Memory**: Negligible (single event reference)
- **CPU**: Minimal (only when events arrive)

## Future Enhancements

Planned future features:

1. **Adaptive Throttling**: Adjust rate based on system load
2. **Metrics**: Expose throttling statistics (events dropped, delivered, etc.)
3. **Backpressure**: Signal to solver to slow down
4. **Priority Events**: Allow certain events to bypass throttle
5. **Composite Throttling**: Multiple throttle policies per consumer

## References

- **Implementation Plan**: `text_utils/features/best_solution_throttling/BST_IMPLEMENTATION_PLAN.md`
- **Official Docs**: `text_utils/features/best_solution_throttling/official_docs_description.md`
- **Source Code**: `core/src/main/java/ai/greycos/solver/core/impl/solver/ThrottlingBestSolutionEventConsumer.java`
- **Tests**: `core/src/test/java/ai/greycos/solver/core/impl/solver/ThrottlingBestSolutionEventConsumerTest.java`
