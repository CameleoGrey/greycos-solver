# OptaPlanner Multithreading Search Tests - Comprehensive Analysis

## Document Overview

This document provides a comprehensive analysis of all OptaPlanner tests related to the multithreading search feature (also known as "move threads"). The tests are categorized by their purpose and scope.

**Last Updated**: 2024
**OptaPlanner Version**: Based on codebase analysis
**Feature**: Multithreaded Local Search (Move Threads)

---

## Table of Contents

1. [Test Categories](#test-categories)
2. [Configuration Tests](#configuration-tests)
3. [Integration Tests](#integration-tests)
4. [Thread Infrastructure Tests](#thread-infrastructure-tests)
5. [SolverManager Tests](#solvermanager-tests)
6. [Partitioned Search Tests](#partitioned-search-tests)
7. [Test Coverage Summary](#test-coverage-summary)
8. [Test Execution Patterns](#test-execution-patterns)
9. [Missing Test Areas](#missing-test-areas)

---

## Test Categories

The multithreading search tests can be categorized into the following groups:

| Category | Purpose | Test Count |
|----------|-----------|-------------|
| Configuration Tests | Verify moveThreadCount configuration and resolution | 5 |
| Integration Tests | End-to-end multithreaded solving | 3 |
| Thread Infrastructure Tests | Test threading primitives (queues, barriers) | 3 |
| SolverManager Tests | Multithreading with SolverManager API | 1 |
| Partitioned Search Tests | Partitioned search + multithreading combination | 2 |

**Total Test Methods**: 14 (across 5 test files)

---

## Configuration Tests

### File: [`DefaultSolverFactoryTest.java`](../core/optaplanner-core-impl/src/test/java/org/optaplanner/core/impl/solver/DefaultSolverFactoryTest.java)

These tests verify that the `moveThreadCount` configuration is properly resolved.

#### Test 1: `moveThreadCountAutoIsCorrectlyResolvedWhenCpuCountIsPositive()`

**Location**: Lines 36-43

**Purpose**: Verify that `AUTO` moveThreadCount resolves correctly based on available CPU cores.

**Test Cases**:
- 1 CPU → `null` (no multithreading)
- 2 CPUs → `null` (no multithreading)
- 4 CPUs → 2 threads
- 5 CPUs → 3 threads
- 6 CPUs → 4 threads
- 100 CPUs → 4 threads (capped at 4)

**Key Assertions**:
```java
assertThat(mockMoveThreadCountResolverAuto(1)).isNull();
assertThat(mockMoveThreadCountResolverAuto(2)).isNull();
assertThat(mockMoveThreadCountResolverAuto(4)).isEqualTo(2);
assertThat(mockMoveThreadCountResolverAuto(5)).isEqualTo(3);
assertThat(mockMoveThreadCountResolverAuto(6)).isEqualTo(4);
assertThat(mockMoveThreadCountResolverAuto(100)).isEqualTo(4);
```

**What It Tests**:
- Auto-detection algorithm logic
- Maximum thread limit (4 threads)
- Minimum threshold (requires 4+ CPUs)

---

#### Test 2: `moveThreadCountAutoIsResolvedToNullWhenCpuCountIsNegative()`

**Location**: Lines 46-48

**Purpose**: Edge case handling for negative CPU counts.

**Test Cases**:
- -1 CPU → `null`

**Key Assertions**:
```java
assertThat(mockMoveThreadCountResolverAuto(-1)).isNull();
```

**What It Tests**:
- Robustness against invalid input
- Graceful degradation to single-threaded mode

---

#### Test 3: `moveThreadCountIsCorrectlyResolvedWhenValueIsPositive()`

**Location**: Lines 63-65

**Purpose**: Verify explicit positive integer values are parsed correctly.

**Test Cases**:
- "2" → 2

**Key Assertions**:
```java
assertThat(resolveMoveThreadCount("2")).isEqualTo(2);
```

**What It Tests**:
- String-to-integer parsing
- Positive value acceptance

---

#### Test 4: `moveThreadCountThrowsExceptionWhenValueIsNegative()`

**Location**: Lines 68-70

**Purpose**: Verify that negative values are rejected.

**Test Cases**:
- "-1" → throws exception

**Key Assertions**:
```java
assertThatIllegalArgumentException().isThrownBy(() -> resolveMoveThreadCount("-1"));
```

**What It Tests**:
- Input validation
- Exception handling for invalid values

---

#### Test 5: `moveThreadCountIsResolvedToNullWhenValueIsNone()`

**Location**: Lines 73-75

**Purpose**: Verify that "NONE" explicitly disables multithreading.

**Test Cases**:
- `SolverConfig.MOVE_THREAD_COUNT_NONE` → `null`

**Key Assertions**:
```java
assertThat(resolveMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_NONE)).isNull();
```

**What It Tests**:
- NONE constant handling
- Explicit single-threaded mode

---

### File: [`SolverConfigMultiThreadedTest.java`](../core/optaplanner-core-impl/src/test/java/org/optaplanner/core/config/solver/SolverConfigMultiThreadedTest.java)

These tests verify end-to-end solving with multithreading enabled.

#### Test 6: `solvingWithTooHighThreadCountFinishes()`

**Location**: Lines 40-43

**Purpose**: Verify that excessively high thread counts don't cause crashes.

**Test Configuration**:
- Entity count: 10
- Value count: 20
- moveThreadCount: "256"

**What It Tests**:
- System stability with many threads
- Thread pool management
- Graceful handling of resource contention

**Timeout**: 5 seconds

---

#### Test 7: `solvingOfVerySmallProblemFinishes()` (DISABLED)

**Location**: Lines 46-50

**Purpose**: Test multithreading with minimal problem size.

**Status**: `@Disabled("PLANNER-1180")`

**Test Configuration**:
- Entity count: 1
- Value count: 1
- moveThreadCount: "2"

**What It Tests**:
- Edge case: problem smaller than thread count
- Minimal move evaluation scenarios

**Why Disabled**: Likely related to issue PLANNER-1180 (edge case handling)

---

#### Test 8: `customThreadFactoryClassIsUsed()`

**Location**: Lines 80-93

**Purpose**: Verify custom thread factory integration.

**Test Configuration**:
- Entity count: 3
- Value count: 5
- moveThreadCount: "2"
- Thread factory: `MockThreadFactory.class`

**Key Assertions**:
```java
assertThat(solution).isNotNull();
assertThat(solution.getScore().isSolutionInitialized()).isTrue();
assertThat(MockThreadFactory.hasBeenCalled()).isTrue();
```

**What It Tests**:
- Custom thread factory invocation
- Thread creation with custom factory
- Multithreading completes successfully

---

## Integration Tests

### File: [`DefaultSolverTest.java`](../core/optaplanner-core-impl/src/test/java/org/optaplanner/core/impl/solver/DefaultSolverTest.java)

#### Test 9: `stopMultiThreadedSolving_whenThereIsNoMoveAvailable()`

**Location**: Lines 892-906

**Purpose**: Verify solver terminates correctly when no moves are available in multithreaded mode.

**Test Configuration**:
- moveThreadCount: "1" (enables multithreading)
- Custom `MoveListFactory` that returns empty list

**Key Assertions**:
```java
PlannerAssert.assertSolutionInitialized(solution);
```

**What It Tests**:
- Graceful termination when move list is empty
- No deadlock or hanging in multithreaded mode
- Proper cleanup of worker threads

**Timeout**: 10 seconds

**Custom Move List Factory**:
```java
public static class TestMoveListFactory implements MoveListFactory<TestdataSolution> {
    @Override
    public List<? extends Move<TestdataSolution>> createMoveList(TestdataSolution solution) {
        return List.of(); // Returns empty list
    }
}
```

---

## Thread Infrastructure Tests

### File: [`OrderByMoveIndexBlockingQueueTest.java`](../core/optaplanner-core-impl/src/test/java/org/optaplanner/core/impl/heuristic/thread/OrderByMoveIndexBlockingQueueTest.java)

These tests verify the core threading primitive that ensures deterministic result ordering.

#### Test 10: `addMove()`

**Location**: Lines 56-97

**Purpose**: Verify out-of-order move results are returned in correct moveIndex order.

**Test Setup**:
- Queue capacity: 6 (4 in circulation + 2 exception handling)
- 2 worker threads (simulated via executor)
- Multiple steps (0, 1, 2)

**Test Flow**:
1. Submit moves out of order from different threads
2. Verify results are consumed in moveIndex order
3. Handle step transitions with `startNextStep()`

**Key Assertions**:
```java
assertResult("a0", -100, queue.take()); // moveIndex 0
assertResult("a1", -1000, queue.take()); // moveIndex 1
assertResult("a2", -200, queue.take()); // moveIndex 2
// ... continues in order despite out-of-order submission
```

**What It Tests**:
- Deterministic ordering despite concurrent execution
- Step transition handling
- Backlog management for out-of-order results
- Multi-step scenario support

---

#### Test 11: `addUndoableMove()`

**Location**: Lines 100-119

**Purpose**: Verify handling of not-doable moves.

**Test Setup**:
- Queue capacity: 6
- Mix of scored moves and not-doable moves

**Key Assertions**:
```java
assertResult("a0", false, queue.take()); // Not doable
assertResult("a1", -1, queue.take());   // Scored
assertResult("a2", false, queue.take()); // Not doable
```

**What It Tests**:
- Not-doable move handling
- Mixed result types in same step
- Boolean doable flag preservation

---

#### Test 12: `addExceptionThrown()`

**Location**: Lines 122-159

**Purpose**: Verify exception propagation and fail-fast behavior.

**Test Flow**:
1. Submit normal moves for step 0
2. Submit moves for step 1
3. Submit exception from a worker thread
4. Verify exception is thrown when consuming

**Key Assertions**:
```java
assertResult("b0", false, queue.take());
assertResult("b1", -1, queue.take());
assertThatThrownBy(queue::take).hasCause(exception);
```

**What It Tests**:
- Exception propagation from workers to solver thread
- Fail-fast on worker errors
- Exception takes precedence over pending results
- Synchronization with CountDownLatch for timing

---

#### Test 13: `addExceptionIsNotEatenIfNextStepStartsBeforeTaken()`

**Location**: Lines 162-181

**Purpose**: Verify exceptions are not lost during step transitions.

**Test Flow**:
1. Submit moves for step 0
2. Submit exception
3. Call `startNextStep(1)` before consuming all results
4. Verify exception is still thrown

**Key Assertions**:
```java
assertThatThrownBy(() -> {
    assertResult("a0", 0, queue.take());
    assertResult("a1", -1, queue.take());
    assertResult("a2", -2, queue.take());
    
    exceptionFuture.get();
    queue.startNextStep(1); // Exception should be thrown here
}).hasCause(exception);
```

**What It Tests**:
- Exception preservation across step boundaries
- No exception swallowing during queue clearing
- Timing-sensitive scenario handling

---

## SolverManager Tests

### File: [`SolverManagerTest.java`](../core/optaplanner-core-impl/src/test/java/org/optaplanner/core/api/solver/SolverManagerTest.java)

#### Test 14: `solveMultipleThreadedMovesWithSolverManager_allGetSolved()` (DISABLED)

**Location**: Lines 375-396

**Purpose**: Test SolverManager with multiple solvers using multithreaded moves.

**Status**: `@Disabled("https://issues.redhat.com/browse/PLANNER-1837")`

**Test Configuration**:
- Process count: `Runtime.getRuntime().availableProcessors()`
- moveThreadCount: "AUTO"
- Multiple parallel solver jobs (one per CPU)

**Test Flow**:
1. Create SolverManager with default parallel solver count
2. Submit `processCount` solver jobs simultaneously
3. Verify all complete with initialized solutions

**Key Assertions**:
```java
for (SolverJob<TestdataSolution, Long> job : jobs) {
    assertSolutionInitialized(job.getFinalBestSolution());
}
```

**What It Tests**:
- Multiple solvers with move threads concurrently
- Resource management across multiple solver instances
- AUTO thread count resolution in concurrent context

**Why Disabled**: Issue PLANNER-1837 - likely related to concurrent resource management

**Timeout**: 60 seconds

---

## Partitioned Search Tests

### File: [`DefaultPartitionedSearchPhaseTest.java`](../core/optaplanner-core-impl/src/test/java/org/optaplanner/core/impl/partitionedsearch/DefaultPartitionedSearchPhaseTest.java)

These tests verify partitioned search combined with multithreading.

#### Test 15: `partCount()`

**Location**: Lines 61-63

**Purpose**: Baseline test without multithreading.

**Test Configuration**:
- moveThreadCount: `SolverConfig.MOVE_THREAD_COUNT_NONE`
- Part size: 3
- Part count: 7

**What It Tests**:
- Partitioned search works in single-threaded mode
- Baseline for comparison with multithreaded version

**Timeout**: 5 seconds

---

#### Test 16: `partCountAndMoveThreadCount()`

**Location**: Lines 67-69

**Purpose**: Verify partitioned search works with multithreading enabled.

**Test Configuration**:
- moveThreadCount: "2"
- Part size: 3
- Part count: 7

**Key Assertions**:
```java
assertThat(((PartitionedSearchPhaseScope) phaseScope).getPartCount())
    .isEqualTo(Integer.valueOf(partCount));
```

**What It Tests**:
- Partitioned search + move threads combination
- Each partition can use multithreaded evaluation
- Part count verification

**Timeout**: 5 seconds

---

## Test Coverage Summary

### Coverage Matrix

| Feature Area | Covered | Test Count | Notes |
|--------------|----------|-------------|--------|
| Configuration parsing | ✅ | 5 | AUTO, explicit, NONE, negative, edge cases |
| Custom thread factory | ✅ | 1 | MockThreadFactory integration |
| End-to-end solving | ✅ | 3 | Basic, high thread count, small problem |
| Empty move list | ✅ | 1 | Graceful termination |
| Queue ordering | ✅ | 3 | Out-of-order, not-doable, exceptions |
| Step transitions | ✅ | 2 | Normal, with exceptions |
| SolverManager integration | ⚠️ | 1 | Disabled (PLANNER-1837) |
| Partitioned search | ✅ | 2 | Single-threaded and multithreaded |
| Thread count limits | ✅ | 1 | Max 4 threads, CPU-based scaling |
| Exception handling | ✅ | 2 | Worker exceptions, step transitions |

### Coverage Gaps

1. **Performance Regression Tests**: No tests comparing single-thread vs multi-thread performance
2. **Determinism Tests**: No tests verifying identical results between single/multi-thread runs with same seed
3. **Thread Count Scaling**: Limited testing of different thread counts (1, 2, 256 tested)
4. **Memory Leak Tests**: No verification of proper resource cleanup
5. **Concurrent SolverManager**: Main integration test is disabled
6. **Edge Cases**: Small problem test is disabled
7. **Score Director State**: No tests verifying worker score director isolation

---

## Test Execution Patterns

### Common Test Structure

Most multithreading tests follow this pattern:

```java
@Test
@Timeout(5)  // Most have 5-10 second timeout
void testName() {
    // 1. Configure solver
    SolverConfig solverConfig = PlannerTestUtils.buildSolverConfig(...);
    solverConfig.setMoveThreadCount("2");  // Enable multithreading
    
    // 2. Create solution
    TestdataSolution solution = createTestSolution(...);
    
    // 3. Solve
    solution = PlannerTestUtils.solve(solverConfig, solution);
    
    // 4. Assert
    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
}
```

### Threading Test Patterns

Thread infrastructure tests use:

```java
// Executor service to simulate multiple workers
ExecutorService executorService = Executors.newFixedThreadPool(2);

// Submit tasks out of order
executorService.submit(() -> queue.addMove(0, 0, 0, moveA, scoreA));
executorService.submit(() -> queue.addMove(1, 0, 3, moveB, scoreB));

// Verify ordered consumption
assertResult("a0", scoreA, queue.take());  // First by moveIndex
assertResult("a3", scoreB, queue.take());  // Second by moveIndex
```

### Configuration Test Patterns

```java
// Mock CPU count for predictable AUTO resolution
private Integer mockMoveThreadCountResolverAuto(int mockCpuCount) {
    DefaultSolverFactory.MoveThreadCountResolver resolver = 
        new DefaultSolverFactory.MoveThreadCountResolver() {
            @Override
            protected int getAvailableProcessors() {
                return mockCpuCount;  // Override for testing
            }
        };
    return resolver.resolveMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_AUTO);
}
```

---

## Missing Test Areas

### High Priority Missing Tests

1. **Determinism Verification**
   - **Purpose**: Ensure multithreading produces identical results to single-threading
   - **Test Approach**: Run same problem with same random seed, compare accepted steps
   - **Why Important**: Multithreading should not change algorithm behavior

2. **Performance Benchmarking**
   - **Purpose**: Verify performance improves with more threads
   - **Test Approach**: Measure solve time with 1, 2, 4 threads
   - **Why Important**: Validate feature provides actual benefit

3. **Thread Pool Cleanup**
   - **Purpose**: Verify no thread leaks after solving
   - **Test Approach**: Solve multiple times, monitor thread count
   - **Why Important**: Prevent resource exhaustion in long-running applications

4. **Score Director Isolation**
   - **Purpose**: Ensure worker score directors don't corrupt each other
   - **Test Approach**: Have workers modify shadow variables, verify no cross-contamination
   - **Why Important**: Core correctness invariant

5. **Barrier Synchronization**
   - **Purpose**: Verify CyclicBarrier works correctly
   - **Test Approach**: Simulate worker at different speeds, verify all wait
   - **Why Important**: Prevents race conditions

### Medium Priority Missing Tests

6. **Variable Thread Counts**
   - Test with 1, 2, 3, 4 threads explicitly
   - Current tests only cover 1, 2, and 256

7. **Move Thread Buffer Size**
   - Test different `moveThreadBufferSize` values
   - Verify impact on throughput and memory

8. **Concurrent SolverManager**
   - Re-enable and fix PLANNER-1837
   - Test multiple solvers with move threads simultaneously

9. **Problem Change Handling**
   - Test `addProblemChange()` with multithreading enabled
   - Verify workers handle solution changes correctly

10. **Termination During Evaluation**
    - Test `terminateEarly()` while workers are evaluating moves
    - Verify graceful shutdown without deadlock

### Low Priority Missing Tests

11. **Environment Mode Variants**
    - Test with `NON_INTRUSIVE_FULL_ASSERT`
    - Test with `INTRUSIVE_FAST_ASSERT`
    - Verify score assertions work in multithreaded context

12. **Different Score Types**
    - Test with SimpleScore, HardSoftScore, BendableScore
    - Verify multithreading works with all score types

13. **List Variables**
    - Test multithreading with list variable planning entities
    - Verify move evaluation correctness

14. **Chained Planning**
    - Test with chained entities (e.g., VRP)
    - Verify move rebasing works correctly

---

## Test Utilities

### MockThreadFactory

**Location**: [`MockThreadFactory.java`](../core/optaplanner-core-impl/src/test/java/org/optaplanner/core/config/solver/testutil/MockThreadFactory.java)

**Purpose**: Verify custom thread factory is invoked.

**Implementation**:
```java
public class MockThreadFactory implements ThreadFactory {
    private static boolean called;
    
    public static boolean hasBeenCalled() {
        return called;
    }
    
    @Override
    public Thread newThread(Runnable r) {
        called = true;
        Thread newThread = new Thread(r, "testing thread");
        newThread.setDaemon(false);
        return newThread;
    }
}
```

**Usage in Tests**:
```java
solverConfig.setThreadFactoryClass(MockThreadFactory.class);
solverConfig.setMoveThreadCount("2");
// ... solve ...
assertThat(MockThreadFactory.hasBeenCalled()).isTrue();
```

---

## Key Test Insights

### 1. AUTO Thread Count Resolution

The tests reveal the AUTO resolution algorithm:
- 1-2 CPUs: No multithreading (returns `null`)
- 3-4 CPUs: `floor(CPUs / 2)` threads
- 5-6 CPUs: `floor(CPUs / 2)` threads
- 7+ CPUs: Capped at 4 threads maximum

**Rationale**: 
- Too few CPUs → overhead not worth it
- Many CPUs → diminishing returns, memory pressure

### 2. Thread Safety Mechanisms

Tests verify three key thread safety mechanisms:

1. **OrderByMoveIndexBlockingQueue**
   - Ensures deterministic result consumption
   - Handles out-of-order worker completion
   - Filters stale results from previous steps

2. **CyclicBarrier**
   - Synchronizes workers after setup and step application
   - Prevents workers from getting ahead of each other
   - Used in worker-only context (solver thread doesn't participate)

3. **Child Thread Score Directors**
   - Each worker has isolated score director
   - Created via `createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD)`
   - Prevents cross-thread score corruption

### 3. Exception Handling Strategy

Tests show a fail-fast exception propagation strategy:

1. Worker throws exception → immediately adds to result queue
2. Solver thread detects exception → throws `IllegalStateException` with worker exception as cause
3. Exception takes precedence over normal results
4. No attempt to recover or continue

**Design Philosophy**: Multithreading failures are critical; fail fast rather than corrupt results.

### 4. Step Transition Handling

Tests verify careful step transition logic:

1. `startNextStep(stepIndex)` called before processing moves
2. Previous step's stale results are discarded
3. Any pending exceptions are checked before clearing queue
4. `nextMoveIndex` reset to 0

**Critical Invariant**: Step index must be strictly increasing to prevent queue corruption.

---

## Recommendations for Additional Testing

### 1. Add Determinism Test Suite

```java
@Test
void multithreadingProducesIdenticalResultsToSingleThread() {
    // Run same problem twice with same seed
    SolverConfig singleThreadConfig = buildConfig("NONE");
    SolverConfig multiThreadConfig = buildConfig("2");
    
    // Use fixed random seed
    singleThreadConfig.setRandomSeed(42L);
    multiThreadConfig.setRandomSeed(42L);
    
    TestdataSolution singleResult = solve(singleThreadConfig);
    TestdataSolution multiResult = solve(multiThreadConfig);
    
    // Compare scores and step counts
    assertThat(multiResult.getScore()).isEqualTo(singleResult.getScore());
}
```

### 2. Add Performance Regression Test

```java
@Test
@Timeout(30)
void performanceImprovesWithMoreThreads() {
    int[] threadCounts = {1, 2, 4};
    long[] times = new long[threadCounts.length];
    
    for (int i = 0; i < threadCounts.length; i++) {
        times[i] = timeSolve(threadCounts[i]);
    }
    
    // Verify 2 threads is faster than 1
    assertThat(times[1]).isLessThan(times[0]);
    
    // Verify 4 threads is faster than 2 (on 4+ CPU machine)
    if (Runtime.getRuntime().availableProcessors() >= 4) {
        assertThat(times[2]).isLessThan(times[1]);
    }
}
```

### 3. Add Resource Leak Test

```java
@Test
void noThreadLeaksAfterMultipleSolves() {
    SolverConfig config = buildConfig("2");
    int initialThreadCount = Thread.activeCount();
    
    for (int i = 0; i < 10; i++) {
        TestdataSolution solution = createSolution();
        solution = solve(config, solution);
    }
    
    int finalThreadCount = Thread.activeCount();
    assertThat(finalThreadCount - initialThreadCount).isLessThan(5);
}
```

### 4. Re-enable SolverManager Test

Investigate and fix issue PLANNER-1837 to enable:
```java
@Test
void solveMultipleThreadedMovesWithSolverManager_allGetSolved() {
    // Currently disabled
    // Should test concurrent solvers with move threads
}
```

---

## Conclusion

The OptaPlanner multithreading search feature has **moderate test coverage** with 14 test methods across 5 test files. The tests cover:

**Strengths**:
- ✅ Configuration parsing and validation
- ✅ Core threading infrastructure (queue ordering, exception handling)
- ✅ Basic end-to-end integration
- ✅ Custom thread factory support
- ✅ Partitioned search integration

**Gaps**:
- ❌ No determinism verification (critical for correctness)
- ❌ No performance benchmarking (important for feature validation)
- ❌ Limited thread count variation testing
- ❌ Missing resource leak verification
- ❌ SolverManager integration test disabled
- ❌ No score director isolation verification
- ❌ No concurrent solver testing

**Priority Recommendations**:

1. **High**: Add determinism tests to verify multithreading doesn't change algorithm behavior
2. **High**: Add performance tests to validate actual speedup
3. **High**: Add thread leak detection tests
4. **Medium**: Fix and re-enable SolverManager test (PLANNER-1837)
5. **Medium**: Test with varied thread counts (1, 2, 3, 4)
6. **Low**: Add tests for edge cases (small problems, termination during evaluation)

The existing tests provide a solid foundation but should be expanded to ensure production reliability, especially for correctness and performance guarantees.
