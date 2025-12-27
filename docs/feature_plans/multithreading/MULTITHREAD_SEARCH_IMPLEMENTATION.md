# OptaPlanner Multithread Search Feature - Implementation Guide

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Core Components](#core-components)
4. [Threading Model](#threading-model)
5. [Step-by-Step Reimplementation Guide](#step-by-step-reimplementation-guide)
6. [Configuration](#configuration)
7. [Testing Considerations](#testing-considerations)

---

## Overview

The multithreaded local search feature in OptaPlanner enables parallel evaluation of moves during local search phases, significantly improving performance on multi-core systems. This implementation distributes move evaluation across multiple worker threads while maintaining thread safety and solution consistency.

### Key Benefits
- **Parallel Move Evaluation**: Multiple moves are evaluated simultaneously across threads
- **Improved Throughput**: Better CPU utilization on multi-core systems
- **Transparent Integration**: Works with existing local search algorithms (Hill Climbing, Tabu Search, Simulated Annealing, etc.)
- **Configurable**: Thread count and buffer size can be tuned per application

---

## Architecture

### High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│                    Solver Thread (Main)                      │
│                                                              │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │         MultiThreadedLocalSearchDecider                │   │
│  │                                                         │   │
│  │  ┌──────────────┐  ┌──────────────────────────────┐  │   │
│  │  │ MoveSelector │──▶│   Operation Queue (Producer)  │  │   │
│  │  └──────────────┘  └──────────────────────────────┘  │   │
│  │                          │                           │   │
│  │                          ▼                           │   │
│  │  ┌──────────────────────────────────────────────────┐  │   │
│  │  │      OrderByMoveIndexBlockingQueue (Consumer)   │  │   │
│  │  └──────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                          │
                    ┌─────┴─────┐
                    ▼           ▼
         ┌─────────────────┐ ┌─────────────────┐
         │ MoveThread 0   │ │ MoveThread 1   │  ...
         │ (Worker)        │ │ (Worker)        │
         └─────────────────┘ └─────────────────┘
                    │           │
                    └─────┬─────┘
                          ▼
                   ┌────────────────┐
                   │ CyclicBarrier │
                   └────────────────┘
```

### Component Relationships

```
SolverConfig
    │
    ├─ moveThreadCount (String: "NONE", "AUTO", or number)
    ├─ moveThreadBufferSize (Integer)
    └─ threadFactoryClass (ThreadFactory)
            │
            ▼
DefaultLocalSearchPhaseFactory.buildDecider()
    │
    ├─ if moveThreadCount == null
    │       └─ creates LocalSearchDecider (single-threaded)
    │
    └─ else
            ├─ creates MultiThreadedLocalSearchDecider
            ├─ creates ExecutorService with moveThreadCount threads
            ├─ creates MoveThreadRunner instances
            ├─ creates operationQueue (ArrayBlockingQueue)
            └─ creates resultQueue (OrderByMoveIndexBlockingQueue)
```

---

## Core Components

### 1. MultiThreadedLocalSearchDecider

**Location**: [`core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java:1)

**Purpose**: Main coordinator for multithreaded local search. Extends [`LocalSearchDecider`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/LocalSearchDecider.java:40) and orchestrates parallel move evaluation.

**Key Fields**:
```java
protected final ThreadFactory threadFactory;        // Factory for creating threads
protected final int moveThreadCount;              // Number of worker threads
protected final int selectedMoveBufferSize;         // Buffer size for moves in flight

protected BlockingQueue<MoveThreadOperation<Solution_>> operationQueue;  // Commands to workers
protected OrderByMoveIndexBlockingQueue<Solution_> resultQueue;           // Results from workers
protected CyclicBarrier moveThreadBarrier;        // Synchronization barrier
protected ExecutorService executor;                // Thread pool
protected List<MoveThreadRunner<Solution_, ?>> moveThreadRunnerList;  // Worker instances
```

**Key Methods**:

#### `phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope)`
- Initializes multithreading infrastructure
- Creates queues, barrier, and thread pool
- Spawns worker threads with [`SetupOperation`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/thread/SetupOperation.java:25)
- Queue capacity: `selectedMoveBufferSize + moveThreadCount + moveThreadCount`
  - `selectedMoveBufferSize`: moves in circulation
  - First `moveThreadCount`: setup operations
  - Second `moveThreadCount`: destroy operations

#### `decideNextStep(LocalSearchStepScope<Solution_> stepScope)`
- Main step execution logic
- Coordinates move selection, evaluation, and foraging
- **Producer-Consumer Pattern**:
  1. Produces [`MoveEvaluationOperation`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/thread/MoveEvaluationOperation.java:24) for each move
  2. Consumes results via [`forageResult()`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java:202)
  3. Maintains buffer of moves in flight
- After picking best move, broadcasts [`ApplyStepOperation`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/thread/ApplyStepOperation.java:25) to all threads

#### `phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope)`
- Sends [`DestroyOperation`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/thread/DestroyOperation.java:22) to all workers
- Shuts down thread pool
- Aggregates calculation counts from all threads

---

### 2. MoveThreadRunner

**Location**: [`core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/thread/MoveThreadRunner.java`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/thread/MoveThreadRunner.java:1)

**Purpose**: Worker thread that processes operations from the operation queue. Each thread has its own [`InnerScoreDirector`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/score/director/InnerScoreDirector.java:1) for independent score calculation.

**Key Fields**:
```java
private final int moveThreadIndex;              // Unique thread identifier
private final boolean evaluateDoable;           // Whether to check move doability
private final BlockingQueue<MoveThreadOperation<Solution_>> operationQueue;
private final OrderByMoveIndexBlockingQueue<Solution_> resultQueue;
private final CyclicBarrier moveThreadBarrier;
private InnerScoreDirector<Solution_, Score_> scoreDirector;  // Thread-local score director
```

**Operation Processing Loop**:

```java
public void run() {
    while (true) {
        MoveThreadOperation<Solution_> operation = operationQueue.take();
        
        if (operation instanceof SetupOperation) {
            // Initialize thread-local score director
            scoreDirector = setupOperation.getScoreDirector()
                .createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
            moveThreadBarrier.await();  // Wait for all threads
        }
        else if (operation instanceof DestroyOperation) {
            // Clean up and exit
            break;
        }
        else if (operation instanceof ApplyStepOperation) {
            // Apply the selected step to maintain synchronization
            step.doMoveOnly(scoreDirector);
            moveThreadBarrier.await();  // Wait for all threads
        }
        else if (operation instanceof MoveEvaluationOperation) {
            // Evaluate a move and return result
            Score<?> score = scoreDirector.doAndProcessMove(move);
            resultQueue.addMove(moveThreadIndex, stepIndex, moveIndex, move, score);
        }
    }
}
```

**Critical Synchronization Points**:
1. **After Setup**: All threads must complete setup before proceeding
2. **After ApplyStep**: All threads must apply the step before evaluating new moves
3. Ensures all worker threads have the same solution state

---

### 3. MoveThreadOperation (Abstract Base)

**Location**: [`core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/thread/MoveThreadOperation.java`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/thread/MoveThreadOperation.java:1)

**Purpose**: Base class for all operations sent to worker threads.

**Subclasses**:

#### SetupOperation
- **Purpose**: Initialize worker thread with a child [`InnerScoreDirector`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/score/director/InnerScoreDirector.java:1)
- **Sent**: Once per phase, to each worker thread
- **Contains**: Parent [`InnerScoreDirector`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/score/director/InnerScoreDirector.java:1) to clone

#### MoveEvaluationOperation
- **Purpose**: Request evaluation of a single move
- **Sent**: For each move to be evaluated
- **Contains**: `stepIndex`, `moveIndex`, [`Move`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/move/Move.java:1) object

#### ApplyStepOperation
- **Purpose**: Apply the selected best move to all worker threads
- **Sent**: After picking the best move for the step
- **Contains**: `stepIndex`, [`Move`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/move/Move.java:1), `Score`

#### DestroyOperation
- **Purpose**: Signal worker threads to terminate
- **Sent**: Once per phase, to each worker thread
- **Contains**: No data

---

### 4. OrderByMoveIndexBlockingQueue

**Location**: [`core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/thread/OrderByMoveIndexBlockingQueue.java`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/thread/OrderByMoveIndexBlockingQueue.java:1)

**Purpose**: Thread-safe queue that returns results in move index order, regardless of completion order.

**Key Challenge**: Worker threads complete moves in arbitrary order due to varying move complexity. The decider needs results in sequential order for deterministic behavior.

**Solution**:
- **Inner Queue**: [`ArrayBlockingQueue`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/thread/OrderByMoveIndexBlockingQueue.java:39) stores results as they arrive
- **Backlog Map**: [`HashMap`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/thread/OrderByMoveIndexBlockingQueue.java:33) stores out-of-order results
- **take() Method**: Returns next sequential result or blocks until available

**Algorithm**:
```java
public MoveResult<Solution_> take() throws InterruptedException {
    int moveIndex = nextMoveIndex++;
    
    // Check backlog first
    if (!backlog.isEmpty()) {
        MoveResult<Solution_> result = backlog.remove(moveIndex);
        if (result != null) {
            return result;
        }
    }
    
    // Wait for next result
    while (true) {
        MoveResult<Solution_> result = innerQueue.take();
        
        if (result.hasThrownException()) {
            throw new IllegalStateException("...", result.getThrowable());
        }
        
        if (result.getMoveIndex() == moveIndex) {
            return result;  // Expected order
        } else {
            backlog.put(result.getMoveIndex(), result);  // Out of order
        }
    }
}
```

**Thread Safety**:
- All `add` methods are synchronized
- `startNextStep()` clears queue and resets `nextMoveIndex`
- Exception handling: exceptions are propagated immediately

**MoveResult Inner Class**:
- Encapsulates result data: `moveThreadIndex`, `stepIndex`, `moveIndex`, `move`, `moveDoable`, `score`
- Special constructor for exceptions

---

### 5. DefaultLocalSearchPhaseFactory

**Location**: [`core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/DefaultLocalSearchPhaseFactory.java`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/DefaultLocalSearchPhaseFactory.java:1)

**Purpose**: Factory that creates either single-threaded or multithreaded decider based on configuration.

**Decision Logic** (lines 97-123):
```java
Integer moveThreadCount = configPolicy.getMoveThreadCount();

if (moveThreadCount == null) {
    // Single-threaded mode
    decider = new LocalSearchDecider<>(...);
} else {
    // Multithreaded mode
    Integer moveThreadBufferSize = configPolicy.getMoveThreadBufferSize();
    if (moveThreadBufferSize == null) {
        moveThreadBufferSize = 10;  // Default value
    }
    
    ThreadFactory threadFactory = 
        configPolicy.buildThreadFactory(ChildThreadType.MOVE_THREAD);
    int selectedMoveBufferSize = moveThreadCount * moveThreadBufferSize;
    
    decider = new MultiThreadedLocalSearchDecider<>(
        configPolicy.getLogIndentation(), termination, moveSelector, 
        acceptor, forager, threadFactory, moveThreadCount, 
        selectedMoveBufferSize);
}
```

**Key Configuration Parameters**:
- `moveThreadCount`: Number of worker threads (null = disabled)
- `moveThreadBufferSize`: Moves per thread in buffer (default: 10)
- `selectedMoveBufferSize`: Total buffer capacity = `moveThreadCount * moveThreadBufferSize`

---

### 6. SolverConfig

**Location**: [`core/optaplanner-core-impl/src/main/java/org/optaplanner/core/config/solver/SolverConfig.java`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/config/solver/SolverConfig.java:1)

**Purpose**: Top-level configuration class that defines multithreading parameters.

**Configuration Fields**:
```java
protected String moveThreadCount = null;           // "NONE", "AUTO", or number
protected Integer moveThreadBufferSize = null;       // Moves per thread buffer
protected Class<? extends ThreadFactory> threadFactoryClass = null;
```

**Constants**:
```java
public static final String MOVE_THREAD_COUNT_NONE = "NONE";
public static final String MOVE_THREAD_COUNT_AUTO = "AUTO";
```

**XML Configuration Example**:
```xml
<solver>
    <moveThreadCount>AUTO</moveThreadCount>
    <moveThreadBufferSize>20</moveThreadBufferSize>
    <localSearch>
        ...
    </localSearch>
</solver>
```

---

## Threading Model

### Producer-Consumer Pattern

```
Solver Thread (Producer)           Worker Threads (Consumers)
     │                                   │
     ├─ Add MoveEvaluationOperation 0 ───▶│─ Process Move 0
     ├─ Add MoveEvaluationOperation 1 ───▶│─ Process Move 1
     ├─ Add MoveEvaluationOperation 2 ───▶│─ Process Move 2
     │                                   │
     ├─ Wait for result 0 ◀──────────────┤─ Add result 0
     ├─ Wait for result 1 ◀──────────────┤─ Add result 1
     ├─ Wait for result 2 ◀──────────────┤─ Add result 2
     │                                   │
```

### Synchronization with CyclicBarrier

The [`CyclicBarrier`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java:69) ensures all threads are in sync at critical points:

```
Step 1: Setup Phase
┌─────────┐  ┌─────────┐  ┌─────────┐
│ Thread 0 │  │ Thread 1 │  │ Thread 2 │
└────┬────┘  └────┬────┘  └────┬────┘
     │            │            │
     └────────────┴────────────┘
                  │
         Create child score directors
                  │
         ┌────────▼────────┐
         │ BARRIER AWAIT  │
         └────────┬────────┘
                  │
         All threads ready
                  ▼
           Begin evaluation

Step 2: Apply Best Move
┌─────────┐  ┌─────────┐  ┌─────────┐
│ Thread 0 │  │ Thread 1 │  │ Thread 2 │
└────┬────┘  └────┬────┘  └────┬────┘
     │            │            │
     └────────────┴────────────┘
                  │
         Apply selected move
                  │
         ┌────────▼────────┐
         │ BARRIER AWAIT  │
         └────────┬────────┘
                  │
         All threads synchronized
                  ▼
           Begin next step
```

### Move Evaluation Flow

```
1. Solver Thread:
   - Select next move from MoveSelector
   - Create MoveEvaluationOperation(stepIndex, moveIndex, move)
   - Add to operationQueue
   - Increment movesInPlay counter

2. Worker Thread:
   - Take operation from operationQueue
   - Validate stepIndex matches current step
   - Rebase move to thread-local score director
   - Check if move is doable (if evaluateDoable = true)
   - If not doable:
     * Add undoable result to resultQueue
   - If doable:
     * Execute move.doMoveOnly()
     * Calculate score
     * Undo move
     * Add scored result to resultQueue

3. Solver Thread:
   - Call forageResult() to get next sequential result
   - resultQueue.take() blocks until next sequential result available
   - Rebase move to solver's score director
   - Create LocalSearchMoveScope
   - Call acceptor.isAccepted()
   - Call forager.addMove()
   - Check for early quit or termination

4. After all moves evaluated:
   - Call pickMove() to select best move
   - If a move was picked:
     * Create ApplyStepOperation
     * Add to operationQueue for all threads
   - Clear remaining operations from queue
```

### Thread Safety Considerations

1. **Score Director Isolation**: Each worker thread has its own [`InnerScoreDirector`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/score/director/InnerScoreDirector.java:1) created via `createChildThreadScoreDirector()`

2. **Queue Synchronization**:
   - [`operationQueue`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java:67): [`ArrayBlockingQueue`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java:98) - thread-safe by design
   - [`resultQueue`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java:68): Custom synchronized wrapper

3. **Barrier Coordination**: [`CyclicBarrier`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java:69) ensures all threads reach synchronization points before proceeding

4. **Move Rebase**: Moves are rebased to the appropriate score director using `move.rebase(scoreDirector)`

5. **Exception Propagation**: Exceptions in worker threads are captured and propagated through the result queue

---

## Step-by-Step Reimplementation Guide

### Prerequisites

Before implementing multithreaded local search, ensure you have:

1. **Base Classes**:
   - [`LocalSearchDecider`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/LocalSearchDecider.java:1) (single-threaded implementation)
   - [`InnerScoreDirector`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/score/director/InnerScoreDirector.java:1) with `createChildThreadScoreDirector()` method
   - [`Move`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/move/Move.java:1) interface with `rebase()` method

2. **Infrastructure**:
   - [`CyclicBarrier`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java:69) from `java.util.concurrent`
   - [`ExecutorService`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java:70) and [`ThreadPoolExecutor`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java:145)
   - [`ArrayBlockingQueue`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java:98)

### Implementation Steps

#### Step 1: Create Operation Classes

Create the base operation class and its subclasses in `org.optaplanner.core.impl.heuristic.thread` package.

**1.1 MoveThreadOperation (Base Class)**
```java
package org.optaplanner.core.impl.heuristic.thread;

public abstract class MoveThreadOperation<Solution_> {
    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
```

**1.2 SetupOperation**
```java
package org.optaplanner.core.impl.heuristic.thread;

import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.impl.score.director.InnerScoreDirector;

public class SetupOperation<Solution_, Score_ extends Score<Score_>> 
        extends MoveThreadOperation<Solution_> {
    
    private final InnerScoreDirector<Solution_, Score_> innerScoreDirector;
    
    public SetupOperation(InnerScoreDirector<Solution_, Score_> innerScoreDirector) {
        this.innerScoreDirector = innerScoreDirector;
    }
    
    public InnerScoreDirector<Solution_, Score_> getScoreDirector() {
        return innerScoreDirector;
    }
}
```

**1.3 MoveEvaluationOperation**
```java
package org.optaplanner.core.impl.heuristic.thread;

import org.optaplanner.core.impl.heuristic.move.Move;

public class MoveEvaluationOperation<Solution_> extends MoveThreadOperation<Solution_> {
    
    private final int stepIndex;
    private final int moveIndex;
    private final Move<Solution_> move;
    
    public MoveEvaluationOperation(int stepIndex, int moveIndex, Move<Solution_> move) {
        this.stepIndex = stepIndex;
        this.moveIndex = moveIndex;
        this.move = move;
    }
    
    public int getStepIndex() {
        return stepIndex;
    }
    
    public int getMoveIndex() {
        return moveIndex;
    }
    
    public Move<Solution_> getMove() {
        return move;
    }
}
```

**1.4 ApplyStepOperation**
```java
package org.optaplanner.core.impl.heuristic.thread;

import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.impl.heuristic.move.Move;

public class ApplyStepOperation<Solution_, Score_ extends Score<Score_>> 
        extends MoveThreadOperation<Solution_> {
    
    private final int stepIndex;
    private final Move<Solution_> step;
    private final Score_ score;
    
    public ApplyStepOperation(int stepIndex, Move<Solution_> step, Score_ score) {
        this.stepIndex = stepIndex;
        this.step = step;
        this.score = score;
    }
    
    public int getStepIndex() {
        return stepIndex;
    }
    
    public Move<Solution_> getStep() {
        return step;
    }
    
    public Score_ getScore() {
        return score;
    }
}
```

**1.5 DestroyOperation**
```java
package org.optaplanner.core.impl.heuristic.thread;

public class DestroyOperation<Solution_> extends MoveThreadOperation<Solution_> {
    // Marker class - no data needed
}
```

---

#### Step 2: Create OrderByMoveIndexBlockingQueue

This queue ensures results are returned in sequential order.

```java
package org.optaplanner.core.impl.heuristic.thread;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.impl.heuristic.move.Move;

public class OrderByMoveIndexBlockingQueue<Solution_> {
    
    private final BlockingQueue<MoveResult<Solution_>> innerQueue;
    private final Map<Integer, MoveResult<Solution_>> backlog;
    
    private int filterStepIndex = Integer.MIN_VALUE;
    private int nextMoveIndex = Integer.MIN_VALUE;
    
    public OrderByMoveIndexBlockingQueue(int capacity) {
        innerQueue = new ArrayBlockingQueue<>(capacity);
        backlog = new HashMap<>(capacity);
    }
    
    public void startNextStep(int stepIndex) {
        synchronized (this) {
            if (filterStepIndex >= stepIndex) {
                throw new IllegalStateException("The old filterStepIndex (" + filterStepIndex
                        + ") must be less than the stepIndex (" + stepIndex + ")");
            }
            filterStepIndex = stepIndex;
            
            // Check for exceptions from previous step
            MoveResult<Solution_> exceptionResult = innerQueue.stream()
                    .filter(MoveResult::hasThrownException)
                    .findFirst()
                    .orElse(null);
            if (exceptionResult != null) {
                throw new IllegalStateException("The move thread with moveThreadIndex ("
                        + exceptionResult.getMoveThreadIndex() + ") has thrown an exception."
                        + " Relayed here in the parent thread.",
                        exceptionResult.getThrowable());
            }
            
            innerQueue.clear();
        }
        nextMoveIndex = 0;
        backlog.clear();
    }
    
    public void addUndoableMove(int moveThreadIndex, int stepIndex, 
            int moveIndex, Move<Solution_> move) {
        MoveResult<Solution_> result = new MoveResult<>(
                moveThreadIndex, stepIndex, moveIndex, move, false, null);
        synchronized (this) {
            if (result.getStepIndex() != filterStepIndex) {
                return;  // Discard element from previous step
            }
            innerQueue.add(result);
        }
    }
    
    public void addMove(int moveThreadIndex, int stepIndex, int moveIndex,
            Move<Solution_> move, Score score) {
        MoveResult<Solution_> result = new MoveResult<>(
                moveThreadIndex, stepIndex, moveIndex, move, true, score);
        synchronized (this) {
            if (result.getStepIndex() != filterStepIndex) {
                return;  // Discard element from previous step
            }
            innerQueue.add(result);
        }
    }
    
    public void addExceptionThrown(int moveThreadIndex, Throwable throwable) {
        MoveResult<Solution_> result = new MoveResult<>(moveThreadIndex, throwable);
        synchronized (this) {
            innerQueue.add(result);
        }
    }
    
    public MoveResult<Solution_> take() throws InterruptedException {
        int moveIndex = nextMoveIndex;
        nextMoveIndex++;
        
        // Check backlog first
        if (!backlog.isEmpty()) {
            MoveResult<Solution_> result = backlog.remove(moveIndex);
            if (result != null) {
                return result;
            }
        }
        
        // Wait for next result
        while (true) {
            MoveResult<Solution_> result = innerQueue.take();
            
            if (result.hasThrownException()) {
                throw new IllegalStateException("The move thread with moveThreadIndex ("
                        + result.getMoveThreadIndex() + ") has thrown an exception."
                        + " Relayed here in the parent thread.",
                        result.getThrowable());
            }
            
            if (result.getMoveIndex() == moveIndex) {
                return result;  // Expected order
            } else {
                backlog.put(result.getMoveIndex(), result);  // Out of order
            }
        }
    }
    
    public static class MoveResult<Solution_> {
        private final int moveThreadIndex;
        private final int stepIndex;
        private final int moveIndex;
        private final Move<Solution_> move;
        private final boolean moveDoable;
        private final Score score;
        private final Throwable throwable;
        
        public MoveResult(int moveThreadIndex, int stepIndex, int moveIndex,
                Move<Solution_> move, boolean moveDoable, Score score) {
            this.moveThreadIndex = moveThreadIndex;
            this.stepIndex = stepIndex;
            this.moveIndex = moveIndex;
            this.move = move;
            this.moveDoable = moveDoable;
            this.score = score;
            this.throwable = null;
        }
        
        public MoveResult(int moveThreadIndex, Throwable throwable) {
            this.moveThreadIndex = moveThreadIndex;
            this.stepIndex = -1;
            this.moveIndex = -1;
            this.move = null;
            this.moveDoable = false;
            this.score = null;
            this.throwable = throwable;
        }
        
        private boolean hasThrownException() {
            return throwable != null;
        }
        
        public int getMoveThreadIndex() {
            return moveThreadIndex;
        }
        
        public int getStepIndex() {
            return stepIndex;
        }
        
        public int getMoveIndex() {
            return moveIndex;
        }
        
        public Move<Solution_> getMove() {
            return move;
        }
        
        public boolean isMoveDoable() {
            return moveDoable;
        }
        
        public Score getScore() {
            return score;
        }
        
        private Throwable getThrowable() {
            return throwable;
        }
    }
}
```

---

#### Step 3: Create MoveThreadRunner

This is the worker thread implementation.

```java
package org.optaplanner.core.impl.heuristic.thread;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;

import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.impl.heuristic.move.Move;
import org.optaplanner.core.impl.score.director.InnerScoreDirector;
import org.optaplanner.core.impl.solver.thread.ChildThreadType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoveThreadRunner<Solution_, Score_ extends Score<Score_>> 
        implements Runnable {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveThreadRunner.class);
    
    private final String logIndentation;
    private final int moveThreadIndex;
    private final boolean evaluateDoable;
    
    private final BlockingQueue<MoveThreadOperation<Solution_>> operationQueue;
    private final OrderByMoveIndexBlockingQueue<Solution_> resultQueue;
    private final CyclicBarrier moveThreadBarrier;
    
    private final boolean assertMoveScoreFromScratch;
    private final boolean assertExpectedUndoMoveScore;
    private final boolean assertStepScoreFromScratch;
    private final boolean assertExpectedStepScore;
    private final boolean assertShadowVariablesAreNotStaleAfterStep;
    
    private InnerScoreDirector<Solution_, Score_> scoreDirector = null;
    private AtomicLong calculationCount = new AtomicLong(-1);
    
    public MoveThreadRunner(String logIndentation, int moveThreadIndex, boolean evaluateDoable,
            BlockingQueue<MoveThreadOperation<Solution_>> operationQueue,
            OrderByMoveIndexBlockingQueue<Solution_> resultQueue,
            CyclicBarrier moveThreadBarrier,
            boolean assertMoveScoreFromScratch, boolean assertExpectedUndoMoveScore,
            boolean assertStepScoreFromScratch, boolean assertExpectedStepScore,
            boolean assertShadowVariablesAreNotStaleAfterStep) {
        this.logIndentation = logIndentation;
        this.moveThreadIndex = moveThreadIndex;
        this.evaluateDoable = evaluateDoable;
        this.operationQueue = operationQueue;
        this.resultQueue = resultQueue;
        this.moveThreadBarrier = moveThreadBarrier;
        this.assertMoveScoreFromScratch = assertMoveScoreFromScratch;
        this.assertExpectedUndoMoveScore = assertExpectedUndoMoveScore;
        this.assertStepScoreFromScratch = assertStepScoreFromScratch;
        this.assertExpectedStepScore = assertExpectedStepScore;
        this.assertShadowVariablesAreNotStaleAfterStep = assertShadowVariablesAreNotStaleAfterStep;
    }
    
    @Override
    public void run() {
        try {
            int stepIndex = -1;
            Score_ lastStepScore = null;
            
            while (true) {
                MoveThreadOperation<Solution_> operation;
                try {
                    operation = operationQueue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                if (operation instanceof SetupOperation) {
                    SetupOperation<Solution_, Score_> setupOperation = 
                            (SetupOperation<Solution_, Score_>) operation;
                    scoreDirector = setupOperation.getScoreDirector()
                            .createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
                    stepIndex = 0;
                    lastStepScore = scoreDirector.calculateScore();
                    LOGGER.trace("{}            Move thread ({}) setup: step index ({}), score ({}).",
                            logIndentation, moveThreadIndex, stepIndex, lastStepScore);
                    try {
                        moveThreadBarrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else if (operation instanceof DestroyOperation) {
                    LOGGER.trace("{}            Move thread ({}) destroy: step index ({}).",
                            logIndentation, moveThreadIndex, stepIndex);
                    calculationCount.set(scoreDirector.getCalculationCount());
                    break;
                } else if (operation instanceof ApplyStepOperation) {
                    ApplyStepOperation<Solution_, Score_> applyStepOperation =
                            (ApplyStepOperation<Solution_, Score_>) operation;
                    if (stepIndex + 1 != applyStepOperation.getStepIndex()) {
                        throw new IllegalStateException("Impossible situation: the moveThread's stepIndex (" 
                                + stepIndex + ") is not followed by the operation's stepIndex ("
                                + applyStepOperation.getStepIndex() + ").");
                    }
                    stepIndex = applyStepOperation.getStepIndex();
                    Move<Solution_> step = applyStepOperation.getStep().rebase(scoreDirector);
                    Score_ score = applyStepOperation.getScore();
                    step.doMoveOnly(scoreDirector);
                    predictWorkingStepScore(step, score);
                    lastStepScore = score;
                    LOGGER.trace("{}            Move thread ({}) step: step index ({}), score ({}).",
                            logIndentation, moveThreadIndex, stepIndex, lastStepScore);
                    try {
                        moveThreadBarrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else if (operation instanceof MoveEvaluationOperation) {
                    MoveEvaluationOperation<Solution_> moveEvaluationOperation = 
                            (MoveEvaluationOperation<Solution_>) operation;
                    int moveIndex = moveEvaluationOperation.getMoveIndex();
                    if (stepIndex != moveEvaluationOperation.getStepIndex()) {
                        throw new IllegalStateException("Impossible situation: the moveThread's stepIndex ("
                                + stepIndex + ") differs from the operation's stepIndex ("
                                + moveEvaluationOperation.getStepIndex() + ") with moveIndex ("
                                + moveIndex + ").");
                    }
                    Move<Solution_> move = moveEvaluationOperation.getMove().rebase(scoreDirector);
                    
                    if (evaluateDoable && !move.isMoveDoable(scoreDirector)) {
                        LOGGER.trace("{}            Move thread ({}) evaluation: step index ({}), move index ({}), not doable.",
                                logIndentation, moveThreadIndex, stepIndex, moveIndex);
                        resultQueue.addUndoableMove(moveThreadIndex, stepIndex, moveIndex, move);
                    } else {
                        Score<?> score = scoreDirector.doAndProcessMove(move, assertMoveScoreFromScratch);
                        if (assertExpectedUndoMoveScore) {
                            scoreDirector.assertExpectedUndoMoveScore(move, lastStepScore);
                        }
                        LOGGER.trace("{}            Move thread ({}) evaluation: step index ({}), move index ({}), score ({}).",
                                logIndentation, moveThreadIndex, stepIndex, moveIndex, score);
                        resultQueue.addMove(moveThreadIndex, stepIndex, moveIndex, move, score);
                    }
                } else {
                    throw new IllegalStateException("Unknown operation (" + operation + ").");
                }
            }
            LOGGER.trace("{}            Move thread ({}) finished.", 
                    logIndentation, moveThreadIndex);
        } catch (RuntimeException | Error throwable) {
            LOGGER.trace("{}            Move thread ({}) exception that will be propagated to the solver thread.",
                    logIndentation, moveThreadIndex, throwable);
            resultQueue.addExceptionThrown(moveThreadIndex, throwable);
        } finally {
            if (scoreDirector != null) {
                scoreDirector.close();
            }
        }
    }
    
    protected void predictWorkingStepScore(Move<Solution_> step, Score_ score) {
        scoreDirector.getSolutionDescriptor().setScore(
                scoreDirector.getWorkingSolution(), score);
        if (assertStepScoreFromScratch) {
            scoreDirector.assertPredictedScoreFromScratch(score, step);
        }
        if (assertExpectedStepScore) {
            scoreDirector.assertExpectedWorkingScore(score, step);
        }
        if (assertShadowVariablesAreNotStaleAfterStep) {
            scoreDirector.assertShadowVariablesAreNotStale(score, step);
        }
    }
    
    public long getCalculationCount() {
        long calculationCount = this.calculationCount.get();
        if (calculationCount == -1L) {
            LOGGER.info("{}Score calculation speed will be too low"
                    + " because move thread ({})'s destroy wasn't processed soon enough.", 
                    logIndentation, moveThreadIndex);
            return 0L;
        }
        return calculationCount;
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "-" + moveThreadIndex;
    }
}
```

---

#### Step 4: Create MultiThreadedLocalSearchDecider

This is the main coordinator class.

```java
package org.optaplanner.core.impl.localsearch.decider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.impl.heuristic.move.Move;
import org.optaplanner.core.impl.heuristic.selector.move.MoveSelector;
import org.optaplanner.core.impl.heuristic.thread.ApplyStepOperation;
import org.optaplanner.core.impl.heuristic.thread.DestroyOperation;
import org.optaplanner.core.impl.heuristic.thread.MoveEvaluationOperation;
import org.optaplanner.core.impl.heuristic.thread.MoveThreadOperation;
import org.optaplanner.core.impl.heuristic.thread.MoveThreadRunner;
import org.optaplanner.core.impl.heuristic.thread.OrderByMoveIndexBlockingQueue;
import org.optaplanner.core.impl.heuristic.thread.SetupOperation;
import org.optaplanner.core.impl.localsearch.decider.acceptor.Acceptor;
import org.optaplanner.core.impl.localsearch.decider.forager.LocalSearchForager;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchMoveScope;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchPhaseScope;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchStepScope;
import org.optaplanner.core.impl.score.director.InnerScoreDirector;
import org.optaplanner.core.impl.solver.scope.SolverScope;
import org.optaplanner.core.impl.solver.termination.Termination;
import org.optaplanner.core.impl.solver.thread.ThreadUtils;

public class MultiThreadedLocalSearchDecider<Solution_> extends LocalSearchDecider<Solution_> {
    
    protected final ThreadFactory threadFactory;
    protected final int moveThreadCount;
    protected final int selectedMoveBufferSize;
    
    protected boolean assertStepScoreFromScratch = false;
    protected boolean assertExpectedStepScore = false;
    protected boolean assertShadowVariablesAreNotStaleAfterStep = false;
    
    protected BlockingQueue<MoveThreadOperation<Solution_>> operationQueue;
    protected OrderByMoveIndexBlockingQueue<Solution_> resultQueue;
    protected CyclicBarrier moveThreadBarrier;
    protected ExecutorService executor;
    protected List<MoveThreadRunner<Solution_, ?>> moveThreadRunnerList;
    
    public MultiThreadedLocalSearchDecider(String logIndentation, 
            Termination<Solution_> termination,
            MoveSelector<Solution_> moveSelector, 
            Acceptor<Solution_> acceptor, 
            LocalSearchForager<Solution_> forager,
            ThreadFactory threadFactory, 
            int moveThreadCount, 
            int selectedMoveBufferSize) {
        super(logIndentation, termination, moveSelector, acceptor, forager);
        this.threadFactory = threadFactory;
        this.moveThreadCount = moveThreadCount;
        this.selectedMoveBufferSize = selectedMoveBufferSize;
    }
    
    public void setAssertStepScoreFromScratch(boolean assertStepScoreFromScratch) {
        this.assertStepScoreFromScratch = assertStepScoreFromScratch;
    }
    
    public void setAssertExpectedStepScore(boolean assertExpectedStepScore) {
        this.assertExpectedStepScore = assertExpectedStepScore;
    }
    
    public void setAssertShadowVariablesAreNotStaleAfterStep(
            boolean assertShadowVariablesAreNotStaleAfterStep) {
        this.assertShadowVariablesAreNotStaleAfterStep = assertShadowVariablesAreNotStaleAfterStep;
    }
    
    @Override
    public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
        super.phaseStarted(phaseScope);
        
        // Create queues with appropriate capacity
        operationQueue = new ArrayBlockingQueue<>(
                selectedMoveBufferSize + moveThreadCount + moveThreadCount);
        resultQueue = new OrderByMoveIndexBlockingQueue<>(
                selectedMoveBufferSize + moveThreadCount);
        moveThreadBarrier = new CyclicBarrier(moveThreadCount);
        
        InnerScoreDirector<Solution_, ?> scoreDirector = phaseScope.getScoreDirector();
        executor = createThreadPoolExecutor();
        moveThreadRunnerList = new ArrayList<>(moveThreadCount);
        
        // Create and start worker threads
        for (int moveThreadIndex = 0; moveThreadIndex < moveThreadCount; moveThreadIndex++) {
            MoveThreadRunner<Solution_, ?> moveThreadRunner = new MoveThreadRunner<>(
                    logIndentation, moveThreadIndex, true,
                    operationQueue, resultQueue, moveThreadBarrier,
                    assertMoveScoreFromScratch, assertExpectedUndoMoveScore,
                    assertStepScoreFromScratch, assertExpectedStepScore, 
                    assertShadowVariablesAreNotStaleAfterStep);
            moveThreadRunnerList.add(moveThreadRunner);
            executor.submit(moveThreadRunner);
            operationQueue.add(new SetupOperation<>(scoreDirector));
        }
    }
    
    @Override
    public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
        super.phaseEnded(phaseScope);
        
        // Tell the move thread runners to stop
        DestroyOperation<Solution_> destroyOperation = new DestroyOperation<>();
        for (int i = 0; i < moveThreadCount; i++) {
            operationQueue.add(destroyOperation);
        }
        shutdownMoveThreads();
        
        // Aggregate calculation counts
        long childThreadsScoreCalculationCount = 0;
        for (MoveThreadRunner<Solution_, ?> moveThreadRunner : moveThreadRunnerList) {
            childThreadsScoreCalculationCount += moveThreadRunner.getCalculationCount();
        }
        phaseScope.addChildThreadsScoreCalculationCount(
                childThreadsScoreCalculationCount);
        
        operationQueue = null;
        resultQueue = null;
        moveThreadRunnerList = null;
    }
    
    @Override
    public void solvingError(SolverScope<Solution_> solverScope, Exception exception) {
        super.solvingError(solverScope, exception);
        shutdownMoveThreads();
    }
    
    protected ExecutorService createThreadPoolExecutor() {
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors
                .newFixedThreadPool(moveThreadCount, threadFactory);
        if (threadPoolExecutor.getMaximumPoolSize() < moveThreadCount) {
            throw new IllegalStateException(
                    "The threadPoolExecutor's maximumPoolSize (" 
                            + threadPoolExecutor.getMaximumPoolSize()
                            + ") is less than the moveThreadCount (" + moveThreadCount 
                            + "), this is unsupported.");
        }
        return threadPoolExecutor;
    }
    
    @Override
    public void decideNextStep(LocalSearchStepScope<Solution_> stepScope) {
        int stepIndex = stepScope.getStepIndex();
        resultQueue.startNextStep(stepIndex);
        
        int selectMoveIndex = 0;
        int movesInPlay = 0;
        Iterator<Move<Solution_>> moveIterator = moveSelector.iterator();
        
        do {
            boolean hasNextMove = moveIterator.hasNext();
            
            // Forage results to make room in buffer
            if (movesInPlay > 0 && (selectMoveIndex >= selectedMoveBufferSize || !hasNextMove)) {
                if (forageResult(stepScope, stepIndex)) {
                    break;
                }
                movesInPlay--;
            }
            
            // Add new moves to evaluate
            if (hasNextMove) {
                Move<Solution_> move = moveIterator.next();
                operationQueue.add(new MoveEvaluationOperation<>(
                        stepIndex, selectMoveIndex, move));
                selectMoveIndex++;
                movesInPlay++;
            }
        } while (movesInPlay > 0);
        
        // Clear remaining operations
        operationQueue.clear();
        pickMove(stepScope);
        
        // Apply step to all worker threads
        if (stepScope.getStep() != null) {
            InnerScoreDirector<Solution_, ?> scoreDirector = stepScope.getScoreDirector();
            if (scoreDirector.requiresFlushing() && stepIndex % 100 == 99) {
                scoreDirector.calculateScore();
            }
            ApplyStepOperation<Solution_, ?> stepOperation =
                    new ApplyStepOperation<>(stepIndex + 1, 
                            stepScope.getStep(), (Score) stepScope.getScore());
            for (int i = 0; i < moveThreadCount; i++) {
                operationQueue.add(stepOperation);
            }
        }
    }
    
    private boolean forageResult(LocalSearchStepScope<Solution_> stepScope, int stepIndex) {
        OrderByMoveIndexBlockingQueue.MoveResult<Solution_> result;
        try {
            result = resultQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
        
        if (stepIndex != result.getStepIndex()) {
            throw new IllegalStateException("Impossible situation: the solverThread's stepIndex (" 
                    + stepIndex + ") differs from the result's stepIndex (" 
                    + result.getStepIndex() + ").");
        }
        
        Move<Solution_> foragingMove = result.getMove().rebase(
                stepScope.getScoreDirector());
        int foragingMoveIndex = result.getMoveIndex();
        LocalSearchMoveScope<Solution_> moveScope = new LocalSearchMoveScope<>(
                stepScope, foragingMoveIndex, foragingMove);
        
        if (!result.isMoveDoable()) {
            logger.trace("{}        Move index ({}) not doable, ignoring move ({}).",
                    logIndentation, foragingMoveIndex, foragingMove);
        } else {
            moveScope.setScore(result.getScore());
            moveScope.getScoreDirector().incrementCalculationCount();
            boolean accepted = acceptor.isAccepted(moveScope);
            moveScope.setAccepted(accepted);
            logger.trace("{}        Move index ({}), score ({}), accepted ({}), move ({}).",
                    logIndentation, foragingMoveIndex, moveScope.getScore(), 
                    moveScope.getAccepted(), foragingMove);
            forager.addMove(moveScope);
            if (forager.isQuitEarly()) {
                return true;
            }
        }
        
        stepScope.getPhaseScope().getSolverScope().checkYielding();
        return termination.isPhaseTerminated(stepScope.getPhaseScope());
    }
    
    private void shutdownMoveThreads() {
        if (executor != null && !executor.isShutdown()) {
            ThreadUtils.shutdownAwaitOrKill(executor, logIndentation, 
                    "Multi-threaded Local Search");
        }
    }
}
```

---

#### Step 5: Update DefaultLocalSearchPhaseFactory

Modify the factory to create multithreaded decider when configured.

```java
// In buildDecider method, add this logic:

private LocalSearchDecider<Solution_> buildDecider(
        HeuristicConfigPolicy<Solution_> configPolicy,
        Termination<Solution_> termination) {
    MoveSelector<Solution_> moveSelector = buildMoveSelector(configPolicy);
    Acceptor<Solution_> acceptor = buildAcceptor(configPolicy);
    LocalSearchForager<Solution_> forager = buildForager(configPolicy);
    
    if (moveSelector.isNeverEnding() && !forager.supportsNeverEndingMoveSelector()) {
        throw new IllegalStateException("The moveSelector (" + moveSelector
                + ") has neverEnding (" + moveSelector.isNeverEnding()
                + "), but forager (" + forager
                + ") does not support it.\n"
                + "Maybe configure <forager> with an <acceptedCountLimit>.");
    }
    
    Integer moveThreadCount = configPolicy.getMoveThreadCount();
    EnvironmentMode environmentMode = configPolicy.getEnvironmentMode();
    LocalSearchDecider<Solution_> decider;
    
    if (moveThreadCount == null) {
        // Single-threaded mode
        decider = new LocalSearchDecider<>(
                configPolicy.getLogIndentation(), termination, 
                moveSelector, acceptor, forager);
    } else {
        // Multithreaded mode
        Integer moveThreadBufferSize = configPolicy.getMoveThreadBufferSize();
        if (moveThreadBufferSize == null) {
            moveThreadBufferSize = 10;  // Default value
        }
        
        ThreadFactory threadFactory = 
                configPolicy.buildThreadFactory(ChildThreadType.MOVE_THREAD);
        int selectedMoveBufferSize = moveThreadCount * moveThreadBufferSize;
        
        MultiThreadedLocalSearchDecider<Solution_> multiThreadedDecider = 
                new MultiThreadedLocalSearchDecider<>(
                        configPolicy.getLogIndentation(), termination, 
                        moveSelector, acceptor, forager,
                        threadFactory, moveThreadCount, selectedMoveBufferSize);
        
        if (environmentMode.isNonIntrusiveFullAsserted()) {
            multiThreadedDecider.setAssertStepScoreFromScratch(true);
        }
        if (environmentMode.isIntrusiveFastAsserted()) {
            multiThreadedDecider.setAssertExpectedStepScore(true);
            multiThreadedDecider.setAssertShadowVariablesAreNotStaleAfterStep(true);
        }
        decider = multiThreadedDecider;
    }
    
    if (environmentMode.isNonIntrusiveFullAsserted()) {
        decider.setAssertMoveScoreFromScratch(true);
    }
    if (environmentMode.isIntrusiveFastAsserted()) {
        decider.setAssertExpectedUndoMoveScore(true);
    }
    
    return decider;
}
```

---

#### Step 6: Update SolverConfig

Add configuration fields to [`SolverConfig`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/config/solver/SolverConfig.java:1).

```java
// Add these fields to SolverConfig:

public static final String MOVE_THREAD_COUNT_NONE = "NONE";
public static final String MOVE_THREAD_COUNT_AUTO = "AUTO";

protected String moveThreadCount = null;
protected Integer moveThreadBufferSize = null;
protected Class<? extends ThreadFactory> threadFactoryClass = null;

// Add getters and setters:

public String getMoveThreadCount() {
    return moveThreadCount;
}

public void setMoveThreadCount(String moveThreadCount) {
    this.moveThreadCount = moveThreadCount;
}

public Integer getMoveThreadBufferSize() {
    return moveThreadBufferSize;
}

public void setMoveThreadBufferSize(Integer moveThreadBufferSize) {
    this.moveThreadBufferSize = moveThreadBufferSize;
}

public Class<? extends ThreadFactory> getThreadFactoryClass() {
    return threadFactoryClass;
}

public void setThreadFactoryClass(Class<? extends ThreadFactory> threadFactoryClass) {
    this.threadFactoryClass = threadFactoryClass;
}

// Add with methods:

public SolverConfig withMoveThreadCount(String moveThreadCount) {
    this.moveThreadCount = moveThreadCount;
    return this;
}

public SolverConfig withMoveThreadBufferSize(Integer moveThreadBufferSize) {
    this.moveThreadBufferSize = moveThreadBufferSize;
    return this;
}

public SolverConfig withThreadFactoryClass(Class<? extends ThreadFactory> threadFactoryClass) {
    this.threadFactoryClass = threadFactoryClass;
    return this;
}

// Update @XmlType propOrder to include new fields:
@XmlType(propOrder = {
        "environmentMode",
        "daemon",
        "randomType",
        "randomSeed",
        "randomFactoryClass",
        "moveThreadCount",
        "moveThreadBufferSize",
        "threadFactoryClass",
        // ... rest of fields
})
```

---

#### Step 7: Update HeuristicConfigPolicy

Add methods to retrieve multithreading configuration.

```java
// In HeuristicConfigPolicy interface:

Integer getMoveThreadCount();

Integer getMoveThreadBufferSize();

ThreadFactory buildThreadFactory(ChildThreadType childThreadType);
```

---

## Configuration

### XML Configuration

```xml
<solver>
    <!-- Enable multithreading -->
    <moveThreadCount>AUTO</moveThreadCount>
    
    <!-- Optional: Configure buffer size (default: 10) -->
    <moveThreadBufferSize>20</moveThreadBufferSize>
    
    <!-- Optional: Custom thread factory -->
    <threadFactoryClass>com.example.CustomThreadFactory</threadFactoryClass>
    
    <localSearch>
        <localSearchType>LATE_ACCEPTANCE</localSearchType>
        <!-- ... other configuration ... -->
    </localSearch>
</solver>
```

### Programmatic Configuration

```java
SolverConfig solverConfig = new SolverConfig()
        .withMoveThreadCount("AUTO")
        .withMoveThreadBufferSize(20)
        .withPhases(
            new LocalSearchPhaseConfig()
                .withLocalSearchType(LocalSearchType.LATE_ACCEPTANCE)
        );

SolverFactory<MySolution> solverFactory = SolverFactory.create(solverConfig);
Solver<MySolution> solver = solverFactory.buildSolver();
```

### Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|-------|----------|-------------|
| `moveThreadCount` | String | `null` | Number of worker threads: `null` (disabled), `"NONE"` (disabled), `"AUTO"` (CPU cores), or specific number |
| `moveThreadBufferSize` | Integer | `10` | Number of moves per thread to buffer. Higher values = more parallelism but more memory |
| `threadFactoryClass` | Class | `null` | Custom [`ThreadFactory`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/config/solver/SolverConfig.java:36) for creating worker threads |

### Tuning Guidelines

1. **Thread Count**:
   - `AUTO`: Use number of available CPU cores
   - For CPU-bound problems: Use `AUTO` or `cores - 1`
   - For I/O-bound problems: May benefit from more threads than cores

2. **Buffer Size**:
   - Default (10) works well for most cases
   - Increase if moves vary greatly in evaluation time
   - Decrease if memory is constrained
   - Formula: `totalBufferSize = moveThreadCount * moveThreadBufferSize`

3. **Performance Monitoring**:
   - Monitor CPU utilization: Should be near 100% when threads are busy
   - Monitor thread contention: Excessive waiting indicates buffer is too small
   - Monitor memory: Larger buffers use more memory

---

## Testing Considerations

### Unit Testing

1. **Test Individual Components**:
   - Test [`OrderByMoveIndexBlockingQueue`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/thread/OrderByMoveIndexBlockingQueue.java:1) with concurrent access
   - Test [`MoveThreadRunner`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/thread/MoveThreadRunner.java:1) with mock operations
   - Test operation classes

2. **Test Synchronization**:
   - Verify barrier synchronization works correctly
   - Test with varying numbers of threads
   - Test exception propagation

3. **Test Determinism**:
   - Ensure results are in move index order
   - Verify reproducibility with same random seed

### Integration Testing

1. **Compare with Single-Threaded**:
   - Same configuration should produce same results
   - Performance should improve with more threads

2. **Test with Real Problems**:
   - Vehicle Routing
   - Employee Scheduling
   - Bin Packing

3. **Stress Testing**:
   - Large problem instances
   - Long-running solves
   - Concurrent solves

### Common Issues and Solutions

| Issue | Cause | Solution |
|-------|--------|----------|
| Deadlock | Barrier not reached | Ensure all threads process operations correctly |
| Memory leaks | Queue not cleared | Verify phaseEnded() cleans up properly |
| Incorrect results | Move not rebased | Always call `move.rebase()` before use |
| Performance degradation | Buffer too small | Increase `moveThreadBufferSize` |
| Thread starvation | Unfair scheduling | Use fair queue implementation |

---

## Summary

The multithreaded local search feature in OptaPlanner is a sophisticated implementation that:

1. **Uses Producer-Consumer Pattern**: Solver thread produces operations, worker threads consume them
2. **Maintains Ordering**: [`OrderByMoveIndexBlockingQueue`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/thread/OrderByMoveIndexBlockingQueue.java:1) ensures sequential results despite parallel execution
3. **Synchronizes State**: [`CyclicBarrier`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java:69) ensures all threads have consistent solution state
4. **Isolates State**: Each worker has its own [`InnerScoreDirector`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/score/director/InnerScoreDirector.java:1)
5. **Handles Exceptions**: Exceptions in worker threads are propagated to solver thread

The implementation is designed for performance while maintaining correctness and reproducibility. By following this guide, you can successfully reimplement this feature in your OptaPlanner fork.
