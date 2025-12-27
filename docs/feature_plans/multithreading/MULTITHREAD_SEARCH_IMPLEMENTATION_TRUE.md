# OptaPlanner Multithreaded Local Search (Move Threads) — Implementation Guide (final)

This guide describes how OptaPlanner’s **multithreaded local search** works and how to reimplement it faithfully.
It focuses on the **move-thread** architecture: the solver thread remains the decision-maker; worker threads only
evaluate moves and keep their internal state synchronized after each accepted step.

---

## Table of contents

1. [Goal and non-goals](#goal-and-non-goals)  
2. [Architecture at a glance](#architecture-at-a-glance)  
3. [Core invariants](#core-invariants)  
4. [Data structures and operations](#data-structures-and-operations)  
5. [OrderByMoveIndexBlockingQueue](#orderbymoveindexblockingqueue)  
6. [MoveThreadRunner](#movethreadrunner)  
7. [MultiThreadedLocalSearchDecider](#multithreadedlocalsearchdecider)  
8. [Lifecycle: phaseStarted / decideNextStep / phaseEnded](#lifecycle-phasestarted--decidenextstep--phaseended)  
9. [Configuration](#configuration)  
10. [Testing checklist](#testing-checklist)  

---

## Goal and non-goals

### Goal
Speed up local search by evaluating candidate moves in parallel while keeping:
- **Correctness** (no cross-thread corruption of score director state),
- **Consistent step semantics** (only the solver thread picks/apply the step),
- **Deterministic behavior** (results are consumed in **moveIndex** order).

### Non-goals
- Workers **do not** select the best move.
- Workers **do not** modify the solver thread’s working solution.
- Workers do not do “distributed search”; this is within one JVM process.

---

## Architecture at a glance

### Actors
- **Solver thread (main)**: iterates moveSelector, sends move-evaluation requests, consumes ordered results,
  runs acceptor/forager, picks the best move, applies it on the main score director, and then broadcasts
  the step to workers to keep them synchronized.
- **Move threads (workers)**: evaluate moves using **child thread score directors** and return scores.

### Communication
- `operationQueue` (BlockingQueue): **commands** from solver thread to workers.
- `resultQueue` (OrderByMoveIndexBlockingQueue): **results** from workers to solver thread, always returned
  in **moveIndex order**.
- `moveThreadBarrier` (CyclicBarrier): worker-only barrier used to synchronize:
  - after Setup (all workers ready),
  - after ApplyStep (all workers have applied the same step).

### High-level flow

```
Solver thread:
  Setup workers
  For each step:
    startNextStep(stepIndex)
    enqueue MoveEvaluationOperations up to buffer size
    repeatedly:
      take result in moveIndex order
      feed acceptor+forager
      enqueue more evaluations as capacity frees
    choose best move
    apply best move on solver thread
    broadcast ApplyStepOperation(stepIndex+1, step, score) to all workers
  Destroy workers
```

---

## Core invariants

1. **Single writer of the working solution**: only the solver thread applies accepted steps to the solver’s
   working solution/score director.
2. **Workers stay in lockstep**: workers apply the **same accepted step** after each iteration via `ApplyStepOperation`.
3. **Ordered consumption**: solver thread consumes move results strictly in **moveIndex** order (even if workers finish out of order).
4. **Step indexing contract**:
   - Workers track an internal `stepIndex` state that starts at **0** at setup (“before step 0 is applied”).
   - When solver thread selects a move for `stepScope.getStepIndex() == k`, it broadcasts
     `ApplyStepOperation(stepIndex = k + 1, step = chosenMove, score = stepScore)`.
5. **Deadlock safety**:
   - Workers block on `moveThreadBarrier.await()` during setup and step application; this prevents one worker
     from “stealing” multiple Setup/ApplyStep operations.
   - **Do not clear `operationQueue` during shutdown** (see [phaseEnded](#phaseended-important-do-not-clear-operationqueue)).

---

## Data structures and operations

### MoveThreadOperation and subclasses (commands)

OptaPlanner uses a **shared** `operationQueue` and differentiates by operation type.

```java
// Marker base type used in the shared operation queue.
public abstract class MoveThreadOperation<Solution_> {
    @Override
    public String toString() { return getClass().getSimpleName(); }
}

// Sent once per worker during phaseStarted(): initialize the worker's child score director.
public final class SetupOperation<Solution_, Score_ extends Score<Score_>>
        extends MoveThreadOperation<Solution_> {

    private final InnerScoreDirector<Solution_, Score_> scoreDirector;

    public SetupOperation(InnerScoreDirector<Solution_, Score_> scoreDirector) {
        this.scoreDirector = scoreDirector;
    }

    public InnerScoreDirector<Solution_, Score_> getScoreDirector() { return scoreDirector; }
}

// Sent for every candidate move to be evaluated during a step.
public final class MoveEvaluationOperation<Solution_> extends MoveThreadOperation<Solution_> {

    private final int stepIndex;
    private final int moveIndex;
    private final Move<Solution_> move;

    public MoveEvaluationOperation(int stepIndex, int moveIndex, Move<Solution_> move) {
        this.stepIndex = stepIndex;
        this.moveIndex = moveIndex;
        this.move = move;
    }

    public int getStepIndex() { return stepIndex; }
    public int getMoveIndex() { return moveIndex; }
    public Move<Solution_> getMove() { return move; }
}

// Sent moveThreadCount times after solver thread has picked the step.
// Note: stepIndex in operation is solverStepIndex + 1.
public final class ApplyStepOperation<Solution_, Score_ extends Score<Score_>>
        extends MoveThreadOperation<Solution_> {

    private final int stepIndex;
    private final Move<Solution_> step;
    private final Score_ score;

    public ApplyStepOperation(int stepIndex, Move<Solution_> step, Score_ score) {
        this.stepIndex = stepIndex;
        this.step = step;
        this.score = score;
    }

    public int getStepIndex() { return stepIndex; }
    public Move<Solution_> getStep() { return step; }
    public Score_ getScore() { return score; }
}

// Sent moveThreadCount times during phaseEnded() to request worker termination.
public final class DestroyOperation<Solution_> extends MoveThreadOperation<Solution_> { }
```

### MoveResult (worker → solver)

Results are put into `resultQueue`. A result can represent:
- a **doable evaluated move** with a score,
- a **not-doable move** (score absent),
- an **exception** thrown by a worker.

```java
public final class MoveResult<Solution_> {
    private final int moveThreadIndex;
    private final int stepIndex;
    private final int moveIndex;
    private final Move<Solution_> move;
    private final boolean doable;
    private final Score<?> score;
    private final Throwable thrownException;

    // Normal (doable) result
    public MoveResult(int moveThreadIndex, int stepIndex, int moveIndex,
                      Move<Solution_> move, boolean doable, Score<?> score) {
        this.moveThreadIndex = moveThreadIndex;
        this.stepIndex = stepIndex;
        this.moveIndex = moveIndex;
        this.move = move;
        this.doable = doable;
        this.score = score;
        this.thrownException = null;
    }

    // Exception result
    public MoveResult(int moveThreadIndex, Throwable thrownException) {
        this.moveThreadIndex = moveThreadIndex;
        this.stepIndex = Integer.MIN_VALUE;
        this.moveIndex = Integer.MIN_VALUE;
        this.move = null;
        this.doable = false;
        this.score = null;
        this.thrownException = thrownException;
    }

    public boolean hasThrownException() { return thrownException != null; }
    public Throwable getThrownException() { return thrownException; }

    public int getMoveThreadIndex() { return moveThreadIndex; }
    public int getStepIndex() { return stepIndex; }
    public int getMoveIndex() { return moveIndex; }
    public Move<Solution_> getMove() { return move; }
    public boolean isDoable() { return doable; }
    public Score<?> getScore() { return score; }
}
```

---

## OrderByMoveIndexBlockingQueue

### Why it exists
Workers complete evaluations out-of-order. The solver thread must process results in the same order moves were
**selected** to preserve deterministic semantics (and to keep integration simple).

### Responsibilities
- Accept move results from any worker.
- Discard stale results from old steps.
- Return results to the solver thread **strictly in increasing moveIndex order** for the current step.
- Propagate worker exceptions immediately.

### Reference implementation (faithful structure)

```java
public final class OrderByMoveIndexBlockingQueue<Solution_> {

    private final BlockingQueue<MoveResult<Solution_>> innerQueue;
    private final Map<Integer, MoveResult<Solution_>> backlog;

    // Current step filter; any result with different stepIndex is stale/ignored (except exceptions).
    private int filterStepIndex = Integer.MIN_VALUE;

    // Next expected moveIndex to return via take()
    private int nextMoveIndex = Integer.MIN_VALUE;

    public OrderByMoveIndexBlockingQueue(int capacity) {
        this.innerQueue = new ArrayBlockingQueue<>(capacity);
        this.backlog = new HashMap<>(capacity);
    }

    public void startNextStep(int stepIndex) {
        synchronized (this) {
            if (filterStepIndex >= stepIndex) {
                throw new IllegalStateException("The old filterStepIndex (" + filterStepIndex
                        + ") must be less than the stepIndex (" + stepIndex + ").");
            }
            filterStepIndex = stepIndex;

            // IMPORTANT:
            // If the solver thread ended the previous step early, it may not have drained all results.
            // An exception may still be sitting in innerQueue; we must relay it BEFORE clearing the queue.
            MoveResult<Solution_> exceptionResult = innerQueue.stream()
                    .filter(MoveResult::hasThrownException)
                    .findFirst()
                    .orElse(null);
            if (exceptionResult != null) {
                throw new IllegalStateException("The move thread with moveThreadIndex ("
                        + exceptionResult.getMoveThreadIndex() + ") has thrown an exception."
                        + " Relayed here in the parent thread.",
                        exceptionResult.getThrownException());
            }

            innerQueue.clear();
        }
        nextMoveIndex = 0;
        backlog.clear();
    }

    public void addMove(int moveThreadIndex, int stepIndex, int moveIndex,
                        Move<Solution_> move, Score<?> score) {
        MoveResult<Solution_> result =
                new MoveResult<>(moveThreadIndex, stepIndex, moveIndex, move, true, score);
        synchronized (this) {
            if (result.getStepIndex() != filterStepIndex) {
                return; // Discard element from previous step
            }
            innerQueue.add(result);
        }
    }

    public void addNotDoable(int moveThreadIndex, int stepIndex, int moveIndex,
                             Move<Solution_> move) {
        MoveResult<Solution_> result =
                new MoveResult<>(moveThreadIndex, stepIndex, moveIndex, move, false, null);
        synchronized (this) {
            if (result.getStepIndex() != filterStepIndex) {
                return; // Discard element from previous step
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
        final int moveIndex = nextMoveIndex++;
        // 1) backlog first
        MoveResult<Solution_> cached = backlog.remove(moveIndex);
        if (cached != null) {
            return cached;
        }

        // 2) wait until we get the expected moveIndex
        while (true) {
            MoveResult<Solution_> result = innerQueue.take();

            if (result.hasThrownException()) {
                throw new IllegalStateException(
                        "The move thread with moveThreadIndex (" + result.getMoveThreadIndex()
                                + ") has thrown an exception.",
                        result.getThrownException());
            }

            if (result.getStepIndex() != filterStepIndex) {
                // stale/out-of-step result → discard
                continue;
            }

            if (result.getMoveIndex() == moveIndex) {
                return result;
            }

            // future moveIndex: store and keep waiting
            backlog.put(result.getMoveIndex(), result);
        }
    }
}
```

> Note: It is fine to clear the **result** queue on step transitions because results are strictly step-scoped.

---

## MoveThreadRunner

### Responsibilities
- Hold a `scoreDirector` created via `createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD)`.
- Process operations from `operationQueue`.
- Evaluate moves and push results to `resultQueue`.
- Apply accepted steps and synchronize via `moveThreadBarrier`.
- Fail-fast: if any worker throws, propagate to solver thread via `addExceptionThrown()`.

### Key behavior (pseudocode)

```java
public final class MoveThreadRunner<Solution_, Score_ extends Score<Score_>> implements Runnable {

    private final int moveThreadIndex;
    private final boolean evaluateDoable;

    private final BlockingQueue<MoveThreadOperation<Solution_>> operationQueue;
    private final OrderByMoveIndexBlockingQueue<Solution_> resultQueue;
    private final CyclicBarrier moveThreadBarrier;

    // Assert flags depend on environment mode; used to validate scores and shadow variables.
    private final boolean assertMoveScoreFromScratch;
    private final boolean assertExpectedUndoMoveScore;
    private final boolean assertStepScoreFromScratch;
    private final boolean assertExpectedStepScore;
    private final boolean assertShadowVariablesAreNotStaleAfterStep;

    private InnerScoreDirector<Solution_, Score_> scoreDirector;
    private int stepIndex;          // worker internal stepIndex, starts at 0
    private Score_ lastStepScore;

    private long calculationCount;

    @Override
    public void run() {
        try {
            while (true) {
                MoveThreadOperation<Solution_> op = operationQueue.take();

                if (op instanceof SetupOperation<?, ?> setupOp) {
                    @SuppressWarnings("unchecked")
                    SetupOperation<Solution_, Score_> s = (SetupOperation<Solution_, Score_>) setupOp;

                    // Create child score director for this worker
                    scoreDirector = s.getScoreDirector()
                            .createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);

                    stepIndex = 0;
                    lastStepScore = scoreDirector.calculateScore();

                    awaitBarrierOrStop();
                    continue;
                }

                if (op instanceof DestroyOperation<?>) {
                    calculationCount = scoreDirector == null ? 0L : scoreDirector.getCalculationCount();
                    break;
                }

                if (op instanceof ApplyStepOperation<?, ?> applyOp) {
                    @SuppressWarnings("unchecked")
                    ApplyStepOperation<Solution_, Score_> a = (ApplyStepOperation<Solution_, Score_>) applyOp;

                    if (stepIndex + 1 != a.getStepIndex()) {
                        throw new IllegalStateException("Worker stepIndex " + stepIndex +
                                " not followed by operation stepIndex " + a.getStepIndex());
                    }

                    stepIndex = a.getStepIndex();

                    Move<Solution_> rebasedStep = a.getStep().rebase(scoreDirector);
                    rebasedStep.doMoveOnly(scoreDirector);

                    predictWorkingStepScore(rebasedStep, a.getScore());
                    lastStepScore = a.getScore();

                    awaitBarrierOrStop();
                    continue;
                }

                if (op instanceof MoveEvaluationOperation<?> evalOp) {
                    @SuppressWarnings("unchecked")
                    MoveEvaluationOperation<Solution_> e = (MoveEvaluationOperation<Solution_>) evalOp;

                    if (stepIndex != e.getStepIndex()) {
                        throw new IllegalStateException("Worker stepIndex " + stepIndex +
                                " differs from evaluation stepIndex " + e.getStepIndex());
                    }

                    int moveIndex = e.getMoveIndex();
                    Move<Solution_> rebasedMove = e.getMove().rebase(scoreDirector);

                    if (evaluateDoable && !rebasedMove.isMoveDoable(scoreDirector)) {
                        resultQueue.addNotDoable(moveThreadIndex, stepIndex, moveIndex, rebasedMove);
                        continue;
                    }

                    // Evaluate the move in isolation and undo it inside the score director.
                    Score_ moveScore = scoreDirector.doAndProcessMove(rebasedMove);

                    // Optional extra assertions depending on environment mode:
                    predictWorkingMoveScore(rebasedMove, moveScore);

                    resultQueue.addMove(moveThreadIndex, stepIndex, moveIndex, rebasedMove, moveScore);
                    continue;
                }

                throw new IllegalStateException("Unknown operation " + op);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            // Surface any worker failure to the solver thread immediately.
            resultQueue.addExceptionThrown(moveThreadIndex, t);
        } finally {
            if (scoreDirector != null) {
                // Ensure any resources are released (implementation-specific).
                scoreDirector.close();
            }
        }
    }

    private void awaitBarrierOrStop() throws InterruptedException {
        try {
            moveThreadBarrier.await();
        } catch (BrokenBarrierException bbe) {
            // Barrier broken → solver is shutting down; exit loop by interrupting.
            Thread.currentThread().interrupt();
        }
    }

    private void predictWorkingMoveScore(Move<Solution_> move, Score_ expectedScore) {
        // Implementation is environment-mode dependent. Typical behaviors:
        // - assertMoveScoreFromScratch: recompute score and compare
        // - assertExpectedUndoMoveScore: ensure undo returns to lastStepScore
    }

    private void predictWorkingStepScore(Move<Solution_> step, Score_ expectedStepScore) {
        // Implementation is environment-mode dependent. Typical behaviors:
        // - assertStepScoreFromScratch: recompute score and compare with expectedStepScore
        // - assertExpectedStepScore: ensure expected score matches internal prediction
        // - assertShadowVariablesAreNotStaleAfterStep: validate shadow variables after step
    }

    public long getCalculationCount() { return calculationCount; }
}
```

> Important: the **barrier** is what prevents one worker from draining multiple Setup/ApplyStep operations:
> once it consumes one such operation, it blocks until all workers reach the same synchronization point.

---

## MultiThreadedLocalSearchDecider

### Responsibilities
- Create/own worker infrastructure (queues, barrier, executor, runners).
- For each step:
  - reset `resultQueue` to current step,
  - enqueue move evaluations up to buffer,
  - consume ordered results and feed acceptor/forager,
  - choose the best move,
  - apply best move on solver thread,
  - broadcast ApplyStepOperation to workers.

### Key fields

```java
public final class MultiThreadedLocalSearchDecider<Solution_> extends LocalSearchDecider<Solution_> {

    private final ThreadFactory threadFactory;
    private final int moveThreadCount;
    private final int selectedMoveBufferSize;

    private BlockingQueue<MoveThreadOperation<Solution_>> operationQueue;
    private OrderByMoveIndexBlockingQueue<Solution_> resultQueue;
    private CyclicBarrier moveThreadBarrier;
    private ExecutorService executor;
    private List<MoveThreadRunner<Solution_, ?>> moveThreadRunnerList;

    // Assert flags are driven by environment mode:
    private boolean assertMoveScoreFromScratch;
    private boolean assertExpectedUndoMoveScore;
    private boolean assertStepScoreFromScratch;
    private boolean assertExpectedStepScore;
    private boolean assertShadowVariablesAreNotStaleAfterStep;

    // ...
}
```

---

## Lifecycle: phaseStarted / decideNextStep / phaseEnded

### phaseStarted
1. Allocate queues and barrier.
2. Create `ExecutorService` with exactly `moveThreadCount` threads (validate maximumPoolSize).
3. Create and submit `MoveThreadRunner` instances.
4. Enqueue one `SetupOperation` per worker.

Capacity guidance:
- `selectedMoveBufferSize = moveThreadCount * moveThreadBufferSize`
- `operationQueue.capacity = selectedMoveBufferSize + 2 * moveThreadCount`
  - `selectedMoveBufferSize`: move evaluations in circulation
  - `moveThreadCount`: **one control operation per worker** (Setup at phase start OR ApplyStep during steps)
  - `moveThreadCount`: Destroy operations at shutdown
- `resultQueue.capacity = selectedMoveBufferSize + moveThreadCount`
  - plus slack for “not-doable/exception” results and ordering backlog.

### decideNextStep
At the start of each step:
- `int stepIndex = stepScope.getStepIndex();`
- `resultQueue.startNextStep(stepIndex);`

Then:
1. Maintain a rolling buffer `movesInPlay` up to `selectedMoveBufferSize`.
2. Iterate `moveSelector` and enqueue `MoveEvaluationOperation(stepIndex, moveIndex, move)` while there is capacity.
3. Repeatedly `resultQueue.take()` (ordered by moveIndex):
   - rebase move on the solver thread’s score director (or as required by your architecture),
   - if not-doable → feed “reject/do nothing” to acceptor/forager,
   - if scored → build `LocalSearchMoveScope`, pass through acceptor and forager.
4. Continue until:
   - no more moves from selector AND `movesInPlay == 0`, or
   - your forager/termination ends the step early (implementation-specific).

After best move chosen:
- Apply it on solver thread (the standard LocalSearchDecider behavior).
- Broadcast `ApplyStepOperation(stepIndex + 1, stepScope.getStep(), stepScope.getScore())`
  **moveThreadCount times** into `operationQueue`.

### phaseEnded
- Enqueue one shared `DestroyOperation` instance **moveThreadCount times**.
- Shutdown executor and await termination (OptaPlanner uses a helper similar to `ThreadUtils.shutdownAwaitOrKill`).
- Aggregate `calculationCount` from all move thread runners into `phaseScope`.
- Null out references to help GC.

#### phaseEnded IMPORTANT: do not clear operationQueue
**Do not** call `operationQueue.clear()` during shutdown.

Reason: clearing the queue can deadlock the barrier in a timing window:
- all **MoveEvaluationOperation** instances have been cleared,
- but the next **ApplyStepOperation** batch hasn’t yet been enqueued,
- workers are waiting at the `moveThreadBarrier` for the step-application synchronization point.

Keeping the queue intact avoids breaking the required “each worker receives its DestroyOperation and exits” path.

---

## Configuration

### XML
```xml
<solver>
  <moveThreadCount>AUTO</moveThreadCount>

  <!-- Optional: per-thread buffer (default: 10). Total selectedMoveBufferSize = moveThreadCount * moveThreadBufferSize -->
  <moveThreadBufferSize>20</moveThreadBufferSize>

  <!-- Optional: custom thread factory -->
  <threadFactoryClass>com.example.CustomThreadFactory</threadFactoryClass>
</solver>
```

### Behavior and tuning
- Increase `moveThreadCount` until CPU saturates; too many threads can hurt due to contention and GC.
- Increase `moveThreadBufferSize` when workers stall (solver thread not feeding enough moves), but be mindful of memory.
- Determinism relies on ordered result consumption; avoid “randomized consumption” shortcuts.

### EnvironmentMode → assert flags (typical mapping)
- `NON_INTRUSIVE_FULL_ASSERT`: enable from-scratch score asserts (move and/or step).
- `INTRUSIVE_FAST_ASSERT`: enable expected-score asserts and shadow-variable staleness checks.

---

## Testing checklist

1. **OrderByMoveIndexBlockingQueue**
   - out-of-order insertions return in order
   - step transitions discard stale results
   - exception results fail-fast

2. **Worker sync**
   - barrier is hit exactly once per Setup and per ApplyStep
   - no worker can execute two ApplyStep operations per step
   - workers exit cleanly on DestroyOperation

3. **Deadlock regression**
   - ensure shutdown does not clear `operationQueue`
   - ensure barrier-break scenarios (interruptions) do not hang

4. **Correctness**
   - compare single-thread vs multi-thread: same accepted steps given same seed/config (where applicable)
   - verify score director state consistency after many steps

5. **Performance sanity**
   - confirm evaluation throughput scales with threads until saturation
   - confirm memory usage grows with buffer size as expected

---

## Minimal integration note

The feature is typically enabled by the phase factory/buildDecider logic:

- if `moveThreadCount == null` → build normal (single-thread) `LocalSearchDecider`
- else → build `MultiThreadedLocalSearchDecider` with:
  - `selectedMoveBufferSize = moveThreadCount * moveThreadBufferSize`
  - `threadFactory = configPolicy.buildThreadFactory(ChildThreadType.MOVE_THREAD)`
  - assert flags derived from `EnvironmentMode`
