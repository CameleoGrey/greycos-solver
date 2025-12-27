# Greycos Multithread Search Implementation vs. Guides - Detailed Comparison

**Document Version**: 1.0  
**Date**: 2024  
**Purpose**: Precise comparison of current greycos multithread search implementation against OptaPlanner guides

---

## Executive Summary

The current greycos multithread search implementation has **significant deviations** from both OptaPlanner guides, particularly in the critical `OrderByMoveIndexBlockingQueue` component. While the high-level architecture is correct, the ordering mechanism is fundamentally broken, which will lead to **non-deterministic behavior** and incorrect results.

### Critical Issues (Must Fix)
1. **OrderByMoveIndexBlockingQueue missing ordering logic** - Returns results in arrival order, not moveIndex order
2. **Missing backlog mechanism** - Cannot handle out-of-order results
3. **Missing step filtering** - No validation of stepIndex in result consumption
4. **Missing exception propagation in take()** - Exceptions not checked before returning results
5. **operationQueue.clear() called incorrectly** - Violates guide's deadlock prevention rule

### Additional Deviations
- Move adapters used throughout (new API vs legacy API)
- Optional monitoring features added (MemoryMonitor, PerformanceMetrics)
- Error recovery mechanism added (fallback to single-threaded)
- Different method names and signatures in some places

---

## Component-by-Component Comparison

### 1. OrderByMoveIndexBlockingQueue

#### Current Implementation (Greycos)

```java
public class OrderByMoveIndexBlockingQueue<Solution_> {
  private final BlockingQueue<MoveResult<Solution_>> queue;
  private final int moveThreadCount;
  private final AtomicInteger nextStepIndex = new AtomicInteger(-1);
  private final AtomicInteger nextMoveIndex = new AtomicInteger(-1);
  private final Object queueLock = new Object();

  public OrderByMoveIndexBlockingQueue(int capacity) {
    this.queue = new ArrayBlockingQueue<>(capacity);
    this.moveThreadCount = 0;
  }

  public void startNextStep(int stepIndex) {
    synchronized (queueLock) {
      nextStepIndex.set(stepIndex);
      nextMoveIndex.set(0);
    }
  }

  public void addMove(int moveThreadIndex, int stepIndex, int moveIndex, 
                     Move<Solution_> move, Score<?> score) {
    synchronized (queueLock) {
      queue.add(new MoveResult<>(moveThreadIndex, stepIndex, moveIndex, move, score));
    }
  }

  public MoveResult<Solution_> take() throws InterruptedException {
    return queue.take();  // ❌ NO ORDERING LOGIC!
  }
}
```

#### Guide 1 (MULTITHREAD_SEARCH_IMPLEMENTATION.md)

```java
public class OrderByMoveIndexBlockingQueue<Solution_> {
    private final BlockingQueue<MoveResult<Solution_>> innerQueue;
    private final Map<Integer, MoveResult<Solution_>> backlog;  // ✅ Backlog map
    
    private int filterStepIndex = Integer.MIN_VALUE;
    private int nextMoveIndex = Integer.MIN_VALUE;
    
    public OrderByMoveIndexBlockingQueue(int capacity) {
        innerQueue = new ArrayBlockingQueue<>(capacity);
        backlog = new HashMap<>(capacity);  // ✅ Initialize backlog
    }
    
    public void startNextStep(int stepIndex) {
        synchronized (this) {
            if (filterStepIndex >= stepIndex) {
                throw new IllegalStateException(...);
            }
            filterStepIndex = stepIndex;
            
            // ✅ Check for exceptions before clearing
            MoveResult<Solution_> exceptionResult = innerQueue.stream()
                    .filter(MoveResult::hasThrownException)
                    .findFirst()
                    .orElse(null);
            if (exceptionResult != null) {
                throw new IllegalStateException(..., exceptionResult.getThrowable());
            }
            
            innerQueue.clear();  // ✅ Clear inner queue
        }
        nextMoveIndex = 0;
        backlog.clear();  // ✅ Clear backlog
    }
    
    public void addMove(int moveThreadIndex, int stepIndex, int moveIndex,
            Move<Solution_> move, Score score) {
        MoveResult<Solution_> result = new MoveResult<>(
                moveThreadIndex, stepIndex, moveIndex, move, true, score);
        synchronized (this) {
            if (result.getStepIndex() != filterStepIndex) {
                return;  // ✅ Filter stale results
            }
            innerQueue.add(result);
        }
    }
    
    public MoveResult<Solution_> take() throws InterruptedException {
        int moveIndex = nextMoveIndex++;
        
        // ✅ Check backlog first
        if (!backlog.isEmpty()) {
            MoveResult<Solution_> result = backlog.remove(moveIndex);
            if (result != null) {
                return result;
            }
        }
        
        // ✅ Wait for next result with ordering logic
        while (true) {
            MoveResult<Solution_> result = innerQueue.take();
            
            if (result.hasThrownException()) {
                throw new IllegalStateException(..., result.getThrowable());
            }
            
            if (result.getMoveIndex() == moveIndex) {
                return result;  // ✅ Expected order
            } else {
                backlog.put(result.getMoveIndex(), result);  // ✅ Store for later
            }
        }
    }
}
```

#### Guide 2 (MULTITHREAD_SEARCH_IMPLEMENTATION_TRUE.md)

```java
public final class OrderByMoveIndexBlockingQueue<Solution_> {
    private final BlockingQueue<MoveResult<Solution_>> innerQueue;
    private final Map<Integer, MoveResult<Solution_>> backlog;  // ✅ Backlog
    
    private int filterStepIndex = Integer.MIN_VALUE;
    private int nextMoveIndex = Integer.MIN_VALUE;
    
    public OrderByMoveIndexBlockingQueue(int capacity) {
        this.innerQueue = new ArrayBlockingQueue<>(capacity);
        this.backlog = new HashMap<>(capacity);
    }
    
    public void startNextStep(int stepIndex) {
        synchronized (this) {
            if (filterStepIndex >= stepIndex) {
                throw new IllegalStateException(...);
            }
            filterStepIndex = stepIndex;

            // ✅ Exception check before clear
            MoveResult<Solution_> exceptionResult = innerQueue.stream()
                    .filter(MoveResult::hasThrownException)
                    .findFirst()
                    .orElse(null);
            if (exceptionResult != null) {
                throw new IllegalStateException(..., exceptionResult.getThrownException());
            }

            innerQueue.clear();
        }
        nextMoveIndex = 0;
        backlog.clear();
    }
    
    public MoveResult<Solution_> take() throws InterruptedException {
        final int moveIndex = nextMoveIndex++;
        
        // ✅ Check backlog first
        MoveResult<Solution_> cached = backlog.remove(moveIndex);
        if (cached != null) {
            return cached;
        }
        
        // ✅ Wait for expected moveIndex
        while (true) {
            MoveResult<Solution_> result = innerQueue.take();

            if (result.hasThrownException()) {
                throw new IllegalStateException(..., result.getThrownException());
            }

            if (result.getStepIndex() != filterStepIndex) {
                continue;  // ✅ Discard stale results
            }

            if (result.getMoveIndex() == moveIndex) {
                return result;
            }

            // ✅ Store future results in backlog
            backlog.put(result.getMoveIndex(), result);
        }
    }
}
```

#### Comparison Summary

| Feature | Greycos | Guide 1 | Guide 2 | Status |
|---------|----------|---------|---------|--------|
| **Backlog Map** | ❌ Missing | ✅ HashMap | ✅ HashMap | **CRITICAL** |
| **Ordering Logic in take()** | ❌ None | ✅ Full | ✅ Full | **CRITICAL** |
| **Step Index Filtering** | ❌ Missing | ✅ In addMove | ✅ In take | **CRITICAL** |
| **Exception Check in startNextStep** | ❌ Missing | ✅ Yes | ✅ Yes | **CRITICAL** |
| **Exception Check in take()** | ❌ Missing | ✅ Yes | ✅ Yes | **CRITICAL** |
| **Backlog Clear on Step** | ❌ N/A | ✅ Yes | ✅ Yes | **CRITICAL** |
| **Step Index Validation** | ⚠️ Only stores | ✅ Validates | ✅ Validates | Issue |
| **Inner Queue Type** | ✅ ArrayBlockingQueue | ✅ ArrayBlockingQueue | ✅ ArrayBlockingQueue | OK |
| **Synchronization** | ✅ queueLock | ✅ synchronized(this) | ✅ synchronized(this) | OK |

**Impact**: The current implementation will return results in arbitrary order, breaking the deterministic contract. Move 5 might be processed before Move 2, leading to incorrect foraging behavior.

---

### 2. MoveResult

#### Current Implementation (Greycos)

```java
public static class MoveResult<Solution_> {
    private final int moveThreadIndex;
    private final int stepIndex;
    private final int moveIndex;
    private final Move<Solution_> move;
    private final boolean moveDoable;
    private final Score<?> score;
    private final Throwable throwable;

    // Constructor for doable move
    public MoveResult(int moveThreadIndex, int stepIndex, int moveIndex, 
                   Move<Solution_> move, Score<?> score) {
        this.moveThreadIndex = moveThreadIndex;
        this.stepIndex = stepIndex;
        this.moveIndex = moveIndex;
        this.move = move;
        this.moveDoable = true;
        this.score = score;
        this.throwable = null;
    }

    // Constructor for not doable move
    public MoveResult(int moveThreadIndex, int stepIndex, int moveIndex, 
                   Move<Solution_> move) {
        this.moveThreadIndex = moveThreadIndex;
        this.stepIndex = stepIndex;
        this.moveIndex = moveIndex;
        this.move = move;
        this.moveDoable = false;
        this.score = null;
        this.throwable = null;
    }

    // Constructor for exception
    public MoveResult(int moveThreadIndex, Throwable throwable) {
        this.moveThreadIndex = moveThreadIndex;
        this.stepIndex = -1;
        this.moveIndex = -1;
        this.move = null;
        this.moveDoable = false;
        this.score = null;
        this.throwable = throwable;
    }

    public boolean isMoveDoable() {
        return moveDoable;
    }

    public Throwable getThrowable() {
        return throwable;
    }
}
```

#### Guide 1

```java
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
    
    public boolean isMoveDoable() {
        return moveDoable;
    }
    
    private Throwable getThrowable() {
        return throwable;
    }
}
```

#### Guide 2

```java
public final class MoveResult<Solution_> {
    private final int moveThreadIndex;
    private final int stepIndex;
    private final int moveIndex;
    private final Move<Solution_> move;
    private final boolean doable;
    private final Score<?> score;
    private final Throwable thrownException;

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
    public boolean isDoable() { return doable; }
}
```

#### Comparison Summary

| Feature | Greycos | Guide 1 | Guide 2 | Status |
|---------|----------|---------|---------|--------|
| **hasThrownException() method** | ❌ Missing | ✅ Private | ✅ Public | **CRITICAL** |
| **Constructor with boolean** | ❌ Separate constructors | ✅ Single + boolean | ✅ Single + boolean | Minor |
| **Exception index values** | ✅ -1 | ✅ -1 | ⚠️ MIN_VALUE | Minor |
| **Method name** | isMoveDoable() | isMoveDoable() | isDoable() | Minor |
| **Throwable field name** | throwable | throwable | thrownException | Minor |

**Impact**: Missing `hasThrownException()` method breaks the exception checking logic in both guides.

---

### 3. MultiThreadedLocalSearchDecider

#### Current Implementation (Greycos)

```java
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
    
    // ✅ Error recovery state (NEW FEATURE)
    protected volatile boolean fallbackToSingleThreaded = false;
    protected volatile int consecutiveFailures = 0;
    protected static final int MAX_CONSECUTIVE_FAILURES = 3;

    @Override
    public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
        super.phaseStarted(phaseScope);
        
        // ✅ Reset error recovery state
        fallbackToSingleThreaded = false;
        consecutiveFailures = 0;
        
        operationQueue = new ArrayBlockingQueue<>(
            selectedMoveBufferSize + moveThreadCount + moveThreadCount);
        resultQueue = new OrderByMoveIndexBlockingQueue<>(
            selectedMoveBufferSize + moveThreadCount);
        moveThreadBarrier = new CyclicBarrier(moveThreadCount);
        
        InnerScoreDirector<Solution_, ?> scoreDirector = phaseScope.getScoreDirector();
        executor = createThreadPoolExecutor();
        moveThreadRunnerList = new ArrayList<>(moveThreadCount);
        
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
        
        DestroyOperation<Solution_> destroyOperation = new DestroyOperation<>();
        for (int i = 0; i < moveThreadCount; i++) {
            operationQueue.add(destroyOperation);
        }
        
        shutdownMoveThreads();
        
        long childThreadsScoreCalculationCount = 0;
        for (MoveThreadRunner<Solution_, ?> moveThreadRunner : moveThreadRunnerList) {
            childThreadsScoreCalculationCount += moveThreadRunner.getCalculationCount();
        }
        phaseScope.addChildThreadsScoreCalculationCount(childThreadsScoreCalculationCount);
        
        operationQueue = null;
        resultQueue = null;
        moveThreadRunnerList = null;
    }

    @Override
    public void decideNextStep(LocalSearchStepScope<Solution_> stepScope) {
        // ✅ Error recovery fallback
        if (fallbackToSingleThreaded) {
            LOGGER.debug("{}            Falling back to single-threaded mode", logIndentation);
            super.decideNextStep(stepScope);
            return;
        }
        
        int stepIndex = stepScope.getStepIndex();
        resultQueue.startNextStep(stepIndex);
        
        int selectMoveIndex = 0;
        int movesInPlay = 0;
        Iterator<?> moveIterator = moveRepository.iterator();  // ⚠️ Uses MoveRepository
        
        do {
            boolean hasNextMove = moveIterator.hasNext();
            if (movesInPlay > 0 && (selectMoveIndex >= selectedMoveBufferSize || !hasNextMove)) {
                if (forageResult(stepScope, stepIndex)) {
                    break;
                }
                movesInPlay--;
            }
            if (hasNextMove) {
                var move = moveIterator.next();
                // ⚠️ Uses MoveAdapters.toLegacyMove
                @SuppressWarnings("unchecked")
                var legacyMove = MoveAdapters.toLegacyMove(
                    (ai.greycos.solver.core.preview.api.move.Move<Solution_>) move);
                operationQueue.add(new MoveEvaluationOperation<>(stepIndex, selectMoveIndex, legacyMove));
                selectMoveIndex++;
                movesInPlay++;
            }
        } while (movesInPlay > 0);
        
        // ❌ CRITICAL: Clears operationQueue - violates guide rule!
        operationQueue.clear();
        
        pickMove(stepScope);
        
        if (stepScope.getStep() != null) {
            InnerScoreDirector<Solution_, ?> scoreDirector = stepScope.getScoreDirector();
            var legacyStep = MoveAdapters.toLegacyMove(stepScope.getStep());
            var stepOperation = new ApplyStepOperation<>(
                stepIndex + 1, legacyStep, stepScope.getScore().raw());
            
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
        
        // ✅ Error recovery logic
        if (result.getThrowable() != null) {
            consecutiveFailures++;
            LOGGER.error("{}            Move thread ({}) threw exception: {}",
                logIndentation, result.getMoveThreadIndex(), result.getThrowable());
            
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                LOGGER.warn("{}            Too many consecutive failures ({}), "
                    + "falling back to single-threaded mode",
                    logIndentation, consecutiveFailures);
                fallbackToSingleThreaded = true;
                return true;
            }
            return false;
        }
        
        consecutiveFailures = 0;
        
        if (stepIndex != result.getStepIndex()) {
            throw new IllegalStateException(
                "Impossible situation: the solverThread's stepIndex ("
                + stepIndex + ") differs from the result's stepIndex ("
                + result.getStepIndex() + ").");
        }
        
        // ⚠️ Uses rebase with getMoveDirector()
        var foragingMove = result.getMove().rebase(
            stepScope.getScoreDirector().getMoveDirector());
        int foragingMoveIndex = result.getMoveIndex();
        
        LocalSearchMoveScope<Solution_> moveScope = new LocalSearchMoveScope<>(
            stepScope, foragingMoveIndex, foragingMove);
        
        if (!result.isMoveDoable()) {
            LOGGER.trace("{}        Move index ({}) not doable, ignoring move ({}).",
                logIndentation, foragingMoveIndex, foragingMove);
        } else {
            @SuppressWarnings("unchecked")
            var score = (ai.greycos.solver.core.api.score.Score<?>) result.getScore();
            moveScope.setScore(InnerScore.fullyAssigned((ai.greycos.solver.core.api.score.Score) score));
            moveScope.getScoreDirector().incrementCalculationCount();
            boolean accepted = acceptor.isAccepted(moveScope);
            moveScope.setAccepted(accepted);
            LOGGER.trace("{}        Move index ({}), score ({}), accepted ({}), move ({}).",
                logIndentation, foragingMoveIndex, moveScope.getScore().raw(),
                moveScope.getAccepted(), foragingMove);
            forager.addMove(moveScope);
            if (forager.isQuitEarly()) {
                return true;
            }
        }
        
        stepScope.getPhaseScope().getSolverScope().checkYielding();
        return termination.isPhaseTerminated(stepScope.getPhaseScope());
    }
}
```

#### Guide 1

```java
public class MultiThreadedLocalSearchDecider<Solution_> extends LocalSearchDecider<Solution_> {
    protected final ThreadFactory threadFactory;
    protected final int moveThreadCount;
    protected final int selectedMoveBufferSize;
    
    protected BlockingQueue<MoveThreadOperation<Solution_>> operationQueue;
    protected OrderByMoveIndexBlockingQueue<Solution_> resultQueue;
    protected CyclicBarrier moveThreadBarrier;
    protected ExecutorService executor;
    protected List<MoveThreadRunner<Solution_, ?>> moveThreadRunnerList;
    
    @Override
    public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
        super.phaseStarted(phaseScope);
        
        operationQueue = new ArrayBlockingQueue<>(
            selectedMoveBufferSize + moveThreadCount + moveThreadCount);
        resultQueue = new OrderByMoveIndexBlockingQueue<>(
            selectedMoveBufferSize + moveThreadCount);
        moveThreadBarrier = new CyclicBarrier(moveThreadCount);
        
        InnerScoreDirector<Solution_, ?> scoreDirector = phaseScope.getScoreDirector();
        executor = createThreadPoolExecutor();
        moveThreadRunnerList = new ArrayList<>(moveThreadCount);
        
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
        
        DestroyOperation<Solution_> destroyOperation = new DestroyOperation<>();
        for (int i = 0; i < moveThreadCount; i++) {
            operationQueue.add(destroyOperation);
        }
        shutdownMoveThreads();
        
        long childThreadsScoreCalculationCount = 0;
        for (MoveThreadRunner<Solution_, ?> moveThreadRunner : moveThreadRunnerList) {
            childThreadsScoreCalculationCount += moveThreadRunner.getCalculationCount();
        }
        phaseScope.addChildThreadsScoreCalculationCount(childThreadsScoreCalculationCount);
        
        operationQueue = null;
        resultQueue = null;
        moveThreadRunnerList = null;
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
            
            if (movesInPlay > 0 && (selectMoveIndex >= selectedMoveBufferSize || !hasNextMove)) {
                if (forageResult(stepScope, stepIndex)) {
                    break;
                }
                movesInPlay--;
            }
            
            if (hasNextMove) {
                Move<Solution_> move = moveIterator.next();
                operationQueue.add(new MoveEvaluationOperation<>(
                    stepIndex, selectMoveIndex, move));
                selectMoveIndex++;
                movesInPlay++;
            }
        } while (movesInPlay > 0);
        
        // ✅ No operationQueue.clear() here!
        
        pickMove(stepScope);
        
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
            throw new IllegalStateException(
                "Impossible situation: the solverThread's stepIndex (" 
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
}
```

#### Guide 2

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
    
    private boolean assertMoveScoreFromScratch;
    private boolean assertExpectedUndoMoveScore;
    private boolean assertStepScoreFromScratch;
    private boolean assertExpectedStepScore;
    private boolean assertShadowVariablesAreNotStaleAfterStep;
    
    @Override
    public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
        // Allocate queues and barrier
        operationQueue = new ArrayBlockingQueue<>(
            selectedMoveBufferSize + 2 * moveThreadCount);
        resultQueue = new OrderByMoveIndexBlockingQueue<>(
            selectedMoveBufferSize + moveThreadCount);
        moveThreadBarrier = new CyclicBarrier(moveThreadCount);
        
        // Create ExecutorService with exactly moveThreadCount threads
        executor = createThreadPoolExecutor();
        moveThreadRunnerList = new ArrayList<>(moveThreadCount);
        
        // Create and submit MoveThreadRunner instances
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
    public void decideNextStep(LocalSearchStepScope<Solution_> stepScope) {
        int stepIndex = stepScope.getStepIndex();
        resultQueue.startNextStep(stepIndex);
        
        int selectMoveIndex = 0;
        int movesInPlay = 0;
        Iterator<Move<Solution_>> moveIterator = moveSelector.iterator();
        
        do {
            boolean hasNextMove = moveIterator.hasNext();
            
            if (movesInPlay > 0 && (selectMoveIndex >= selectedMoveBufferSize || !hasNextMove)) {
                if (forageResult(stepScope, stepIndex)) {
                    break;
                }
                movesInPlay--;
            }
            
            if (hasNextMove) {
                Move<Solution_> move = moveIterator.next();
                operationQueue.add(new MoveEvaluationOperation<>(
                    stepIndex, selectMoveIndex, move));
                selectMoveIndex++;
                movesInPlay++;
            }
        } while (movesInPlay > 0);
        
        pickMove(stepScope);
        
        if (stepScope.getStep() != null) {
            ApplyStepOperation<Solution_, ?> stepOperation =
                new ApplyStepOperation<>(stepIndex + 1, 
                    stepScope.getStep(), stepScope.getScore());
            for (int i = 0; i < moveThreadCount; i++) {
                operationQueue.add(stepOperation);
            }
        }
    }
    
    @Override
    public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
        // Enqueue one shared DestroyOperation instance moveThreadCount times
        DestroyOperation<Solution_> destroyOperation = new DestroyOperation<>();
        for (int i = 0; i < moveThreadCount; i++) {
            operationQueue.add(destroyOperation);
        }
        
        shutdownMoveThreads();
        
        // Aggregate calculationCount from all move thread runners into phaseScope
        long calculationCount = 0;
        for (MoveThreadRunner<Solution_, ?> runner : moveThreadRunnerList) {
            calculationCount += runner.getCalculationCount();
        }
        phaseScope.addChildThreadsScoreCalculationCount(calculationCount);
        
        // Null out references
        operationQueue = null;
        resultQueue = null;
        moveThreadRunnerList = null;
    }
}
```

#### Comparison Summary

| Feature | Greycos | Guide 1 | Guide 2 | Status |
|---------|----------|---------|---------|--------|
| **operationQueue.clear() in decideNextStep** | ❌ **YES (line 199)** | ✅ NO | ✅ NO | **CRITICAL** |
| **Error Recovery Mechanism** | ✅ Added | ❌ None | ❌ None | Enhancement |
| **MoveRepository vs MoveSelector** | ⚠️ MoveRepository | ✅ MoveSelector | ✅ MoveSelector | API Diff |
| **MoveAdapters usage** | ⚠️ Yes (new API) | ❌ No | ❌ No | API Diff |
| **rebase() signature** | ⚠️ .rebase(getMoveDirector()) | ✅ .rebase(scoreDirector) | ✅ .rebase(scoreDirector) | API Diff |
| **Score wrapping** | ⚠️ InnerScore.fullyAssigned() | ✅ Direct | ✅ Direct | API Diff |
| **Queue capacity calculation** | ✅ Correct | ✅ Correct | ✅ Correct | OK |
| **Barrier usage** | ✅ Correct | ✅ Correct | ✅ Correct | OK |
| **Setup operation sending** | ✅ Correct | ✅ Correct | ✅ Correct | OK |
| **Destroy operation sending** | ✅ Correct | ✅ Correct | ✅ Correct | OK |
| **ApplyStep broadcasting** | ✅ Correct | ✅ Correct | ✅ Correct | OK |
| **Calculation count aggregation** | ✅ Correct | ✅ Correct | ✅ Correct | OK |

**Critical Issue**: Line 199 in Greycos implementation calls `operationQueue.clear()` which violates both guides' explicit warnings about deadlock prevention.

---

### 4. MoveThreadRunner

#### Current Implementation (Greycos)

```java
public class MoveThreadRunner<Solution_, Score_ extends Score<Score_>> implements Runnable {
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
    
    // ✅ Optional monitoring components (NEW FEATURE)
    private final MemoryMonitor memoryMonitor;
    private final PerformanceMetrics performanceMetrics;
    private final boolean enableMemoryMonitoring;
    private final boolean enablePerformanceMetrics;

    @Override
    public void run() {
        try {
            int stepIndex = -1;
            Score_ lastStepScore = null;
            while (true) {
                MoveThreadOperation<Solution_> operation;
                long operationStartTime = System.nanoTime();
                try {
                    operation = operationQueue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                // ✅ Memory pressure check
                if (enableMemoryMonitoring && memoryMonitor != null) {
                    MemoryMonitor.MemoryPressureLevel pressure = memoryMonitor.checkMemoryUsage();
                    if (pressure == MemoryMonitor.MemoryPressureLevel.CRITICAL
                        || pressure == MemoryMonitor.MemoryPressureLevel.EMERGENCY) {
                        LOGGER.warn("{}            Move thread ({}) detected high memory pressure ({}), "
                            + "consider reducing moveThreadCount or moveThreadBufferSize.",
                            logIndentation, moveThreadIndex, pressure);
                    }
                }
                
                if (operation instanceof SetupOperation) {
                    SetupOperation<Solution_, Score_> setupOperation =
                        (SetupOperation<Solution_, Score_>) operation;
                    try {
                        var parentScoreDirector = setupOperation.getScoreDirector();
                        scoreDirector = parentScoreDirector.createChildThreadScoreDirector(
                            ChildThreadType.MOVE_THREAD);
                        stepIndex = 0;
                        lastStepScore = scoreDirector.calculateScore().raw();
                        try {
                            moveThreadBarrier.await();
                        } catch (InterruptedException | java.util.concurrent.BrokenBarrierException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    } catch (RuntimeException | Error throwable) {
                        // ✅ Close score director if setup fails
                        if (scoreDirector != null) {
                            try {
                                scoreDirector.close();
                            } catch (Exception e) {
                                LOGGER.warn("{}            Move thread ({}) failed to close score director during setup.",
                                    logIndentation, moveThreadIndex, e);
                            }
                        }
                        throw throwable;
                    }
                } else if (operation instanceof DestroyOperation) {
                    calculationCount.set(scoreDirector.getCalculationCount());
                    break;
                } else if (operation instanceof ApplyStepOperation) {
                    ApplyStepOperation<Solution_, Score_> applyStepOperation =
                        (ApplyStepOperation<Solution_, Score_>) operation;
                    if (stepIndex + 1 != applyStepOperation.getStepIndex()) {
                        throw new IllegalStateException(
                            "Impossible situation: moveThread's stepIndex ("
                            + stepIndex + ") is not followed by operation's stepIndex ("
                            + applyStepOperation.getStepIndex() + ").");
                    }
                    stepIndex = applyStepOperation.getStepIndex();
                    Move<Solution_> step = MoveAdapters.toLegacyMove(
                        applyStepOperation.getStep()).rebase(scoreDirector);
                    Score_ score = applyStepOperation.getScore();
                    step.doMoveOnly(scoreDirector);
                    predictWorkingStepScore(step, InnerScore.fullyAssigned(score));
                    lastStepScore = score;
                    try {
                        moveThreadBarrier.await();
                    } catch (InterruptedException | java.util.concurrent.BrokenBarrierException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else if (operation instanceof MoveEvaluationOperation) {
                    MoveEvaluationOperation<Solution_> moveEvaluationOperation =
                        (MoveEvaluationOperation<Solution_>) operation;
                    int moveIndex = moveEvaluationOperation.getMoveIndex();
                    if (stepIndex != moveEvaluationOperation.getStepIndex()) {
                        throw new IllegalStateException(
                            "Impossible situation: moveThread's stepIndex ("
                            + stepIndex + ") differs from operation's stepIndex ("
                            + moveEvaluationOperation.getStepIndex() + ") with moveIndex ("
                            + moveIndex + ").");
                    }
                    Move<Solution_> originalMove = moveEvaluationOperation.getMove();
                    if (originalMove == null) {
                        throw new NullPointerException(
                            "Move cannot be null in MoveEvaluationOperation");
                    }
                    Move<Solution_> move = MoveAdapters.toLegacyMove(originalMove).rebase(scoreDirector);
                    
                    long evaluationStartTime = System.nanoTime();
                    boolean accepted = false;
                    
                    try {
                        if (evaluateDoable && !move.isMoveDoable(scoreDirector)) {
                            resultQueue.addUndoableMove(moveThreadIndex, stepIndex, moveIndex, move);
                        } else {
                            // ⚠️ Uses executeTemporaryMove instead of doAndProcessMove
                            var score = scoreDirector.executeTemporaryMove(move, assertMoveScoreFromScratch);
                            if (score == null) {
                                // Fallback to a default score if mock returns null
                                score = scoreDirector.calculateScore();
                            }
                            if (assertExpectedUndoMoveScore) {
                                // Note: assertExpectedUndoMoveScore is not applicable in move threads
                                // as they don't have access to proper lifecycle context
                            }
                            resultQueue.addMove(moveThreadIndex, stepIndex, moveIndex, move, score.raw());
                            accepted = true;
                        }
                    } finally {
                        long evaluationTime = System.nanoTime() - evaluationStartTime;
                        
                        // ✅ Record performance metrics if enabled
                        if (enablePerformanceMetrics && performanceMetrics != null) {
                            performanceMetrics.recordMoveEvaluation(
                                moveThreadIndex, evaluationTime, accepted);
                        }
                    }
                } else {
                    throw new IllegalStateException("Unknown operation (" + operation + ").");
                }
            }
        } catch (RuntimeException | Error throwable) {
            LOGGER.error("{}            Move thread ({}) exception that will be propagated to the solver thread.",
                logIndentation, moveThreadIndex, throwable);
            resultQueue.addExceptionThrown(moveThreadIndex, throwable);
        } finally {
            // ✅ Close child score director
            if (scoreDirector != null) {
                try {
                    scoreDirector.close();
                } catch (Exception e) {
                    LOGGER.warn("{}            Move thread ({}) failed to close score director.",
                        logIndentation, moveThreadIndex, e);
                }
            }
        }
    }
    
    protected void predictWorkingStepScore(Move<Solution_> step, InnerScore<Score_> score) {
        scoreDirector.getSolutionDescriptor().setScore(
            scoreDirector.getWorkingSolution(), score.raw());
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
}
```

#### Guide 1

```java
public class MoveThreadRunner<Solution_, Score_ extends Score<Score_>> implements Runnable {
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
                        // ✅ Uses doAndProcessMove
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
}
```

#### Guide 2

```java
public final class MoveThreadRunner<Solution_, Score_ extends Score<Score_>> implements Runnable {
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
    
    private InnerScoreDirector<Solution_, Score_> scoreDirector;
    private int stepIndex;
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
                    
                    // ✅ Uses doAndProcessMove
                    Score_ moveScore = scoreDirector.doAndProcessMove(rebasedMove);
                    
                    predictWorkingMoveScore(rebasedMove, moveScore);
                    
                    resultQueue.addMove(moveThreadIndex, stepIndex, moveIndex, rebasedMove, moveScore);
                    continue;
                }
                
                throw new IllegalStateException("Unknown operation " + op);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            resultQueue.addExceptionThrown(moveThreadIndex, t);
        } finally {
            if (scoreDirector != null) {
                scoreDirector.close();
            }
        }
    }
    
    private void awaitBarrierOrStop() throws InterruptedException {
        try {
            moveThreadBarrier.await();
        } catch (BrokenBarrierException bbe) {
            Thread.currentThread().interrupt();
        }
    }
}
```

#### Comparison Summary

| Feature | Greycos | Guide 1 | Guide 2 | Status |
|---------|----------|---------|---------|--------|
| **Move evaluation method** | ⚠️ executeTemporaryMove | ✅ doAndProcessMove | ✅ doAndProcessMove | API Diff |
| **MoveAdapters usage** | ⚠️ Yes | ❌ No | ❌ No | API Diff |
| **Null check for move** | ✅ Added | ❌ No | ❌ No | Enhancement |
| **Score null fallback** | ✅ Added | ❌ No | ❌ No | Enhancement |
| **Monitoring support** | ✅ Added | ❌ No | ❌ No | Enhancement |
| **Memory pressure check** | ✅ Added | ❌ No | ❌ No | Enhancement |
| **Performance metrics** | ✅ Added | ❌ No | ❌ No | Enhancement |
| **Setup error handling** | ✅ Enhanced | ⚠️ Basic | ⚠️ Basic | Enhancement |
| **ScoreDirector close in finally** | ✅ With try-catch | ⚠️ Direct | ⚠️ Direct | Enhancement |
| **Barrier await helper** | ❌ Inline | ❌ Inline | ✅ awaitBarrierOrStop() | Minor |
| **assertExpectedUndoMoveScore** | ⚠️ Commented out | ✅ Implemented | ✅ Implemented | Issue |
| **Logging level** | ⚠️ ERROR/WARN | ✅ TRACE | ⚠️ None | Minor |

---

### 5. Operation Classes

#### Current Implementation (Greycos)

All operation classes are present and correctly structured:
- [`MoveThreadOperation`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/MoveThreadOperation.java:1) - ✅ Correct
- [`SetupOperation`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/SetupOperation.java:1) - ✅ Correct
- [`MoveEvaluationOperation`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/MoveEvaluationOperation.java:1) - ✅ Correct
- [`ApplyStepOperation`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/ApplyStepOperation.java:1) - ✅ Correct
- [`DestroyOperation`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/DestroyOperation.java:1) - ✅ Correct

#### Comparison Summary

| Feature | Greycos | Guide 1 | Guide 2 | Status |
|---------|----------|---------|---------|--------|
| **MoveThreadOperation base** | ✅ Correct | ✅ Correct | ✅ Correct | OK |
| **SetupOperation structure** | ✅ Correct | ✅ Correct | ✅ Correct | OK |
| **MoveEvaluationOperation structure** | ✅ Correct | ✅ Correct | ✅ Correct | OK |
| **ApplyStepOperation structure** | ✅ Correct | ✅ Correct | ✅ Correct | OK |
| **DestroyOperation structure** | ✅ Correct | ✅ Correct | ✅ Correct | OK |
| **toString() implementation** | ✅ In base | ✅ In base | ⚠️ Not shown | OK |
| **Generics** | ⚠️ Score_ in some | ⚠️ Score_ in some | ✅ Score_ extends Score | Minor |

---

## Critical Issues Summary

### Issue 1: OrderByMoveIndexBlockingQueue Missing Ordering Logic
**Severity**: CRITICAL  
**Location**: [`OrderByMoveIndexBlockingQueue.take()`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/OrderByMoveIndexBlockingQueue.java:132)

**Problem**: The `take()` method simply returns `queue.take()` without any ordering logic. This means results are returned in arrival order, not in `moveIndex` order.

**Expected Behavior** (from both guides):
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
    
    // Wait for next result with ordering logic
    while (true) {
        MoveResult<Solution_> result = innerQueue.take();
        
        if (result.hasThrownException()) {
            throw new IllegalStateException(..., result.getThrowable());
        }
        
        if (result.getMoveIndex() == moveIndex) {
            return result;  // Expected order
        } else {
            backlog.put(result.getMoveIndex(), result);  // Store for later
        }
    }
}
```

**Impact**: 
- Non-deterministic behavior
- Moves processed out of order
- Incorrect foraging behavior
- Potential deadlock or livelock scenarios

**Fix Required**: Implement full ordering logic with backlog map as shown in both guides.

---

### Issue 2: Missing Backlog Map
**Severity**: CRITICAL  
**Location**: [`OrderByMoveIndexBlockingQueue`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/OrderByMoveIndexBlockingQueue.java:20)

**Problem**: No `backlog` field exists to store out-of-order results.

**Expected**: 
```java
private final Map<Integer, MoveResult<Solution_>> backlog;
```

**Impact**: Cannot handle out-of-order results from worker threads.

---

### Issue 3: Missing Step Index Filtering
**Severity**: CRITICAL  
**Location**: [`OrderByMoveIndexBlockingQueue.addMove()`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/OrderByMoveIndexBlockingQueue.java:112)

**Problem**: No validation that results match the current step index.

**Expected** (from Guide 1):
```java
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
```

**Impact**: Stale results from previous steps may be processed, causing incorrect behavior.

---

### Issue 4: Missing Exception Propagation in startNextStep
**Severity**: CRITICAL  
**Location**: [`OrderByMoveIndexBlockingQueue.startNextStep()`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/OrderByMoveIndexBlockingQueue.java:105)

**Problem**: No check for pending exceptions before clearing the queue.

**Expected** (from both guides):
```java
public void startNextStep(int stepIndex) {
    synchronized (this) {
        if (filterStepIndex >= stepIndex) {
            throw new IllegalStateException(...);
        }
        filterStepIndex = stepIndex;
        
        // Check for exceptions from previous step
        MoveResult<Solution_> exceptionResult = innerQueue.stream()
                .filter(MoveResult::hasThrownException)
                .findFirst()
                .orElse(null);
        if (exceptionResult != null) {
            throw new IllegalStateException(..., exceptionResult.getThrowable());
        }
        
        innerQueue.clear();
    }
    nextMoveIndex = 0;
    backlog.clear();
}
```

**Impact**: Exceptions from worker threads may be silently ignored.

---

### Issue 5: operationQueue.clear() Called Incorrectly
**Severity**: CRITICAL  
**Location**: [`MultiThreadedLocalSearchDecider.decideNextStep()`](core/src/main/java/ai/greycos/solver/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java:199)

**Problem**: Line 199 calls `operationQueue.clear()` which violates both guides' explicit warnings.

**Guide 1 Warning** (line 1150):
```java
// Clear remaining operations
operationQueue.clear();  // ❌ THIS IS WRONG IN GREYCOS
```

**Guide 2 Explicit Rule** (line 581-589):
```java
#### phaseEnded IMPORTANT: do not clear operationQueue
Do not call operationQueue.clear() during shutdown.

Reason: clearing the queue can deadlock the barrier in a timing window:
- all MoveEvaluationOperation instances have been cleared,
- but the next ApplyStepOperation batch hasn't yet been enqueued,
- workers are waiting at the moveThreadBarrier for the step-application synchronization point.

Keeping the queue intact avoids breaking the required "each worker receives its DestroyOperation and exits" path.
```

**Impact**: Potential deadlock when:
- All move evaluations are cleared
- ApplyStepOperation not yet enqueued
- Workers waiting at barrier

**Fix**: Remove line 199 entirely.

---

### Issue 6: Missing hasThrownException() Method
**Severity**: CRITICAL  
**Location**: [`MoveResult`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/OrderByMoveIndexBlockingQueue.java:25)

**Problem**: The `MoveResult` class lacks the `hasThrownException()` method used by both guides.

**Expected**:
```java
private boolean hasThrownException() {
    return throwable != null;
}
```

**Impact**: Cannot check for exceptions in result queue.

---

## Additional Deviations

### API Differences (Expected - Greycos Using New API)

1. **MoveRepository vs MoveSelector**
   - Greycos uses `MoveRepository.iterator()`
   - Guides use `MoveSelector.iterator()`
   - This is an expected API evolution

2. **MoveAdapters.toLegacyMove()**
   - Greycos adapts new Move API to legacy
   - Guides use legacy Move API directly
   - This is an expected API evolution

3. **executeTemporaryMove vs doAndProcessMove**
   - Greycos uses `executeTemporaryMove()`
   - Guides use `doAndProcessMove()`
   - This may be a method name change in new API

4. **rebase() signature**
   - Greycos: `.rebase(getMoveDirector())`
   - Guides: `.rebase(scoreDirector)`
   - This is an expected API evolution

### Enhancements (Greycos-Specific)

1. **Error Recovery Mechanism**
   - Greycos has fallback to single-threaded mode
   - Tracks consecutive failures
   - This is a useful enhancement

2. **Monitoring Support**
   - MemoryMonitor for memory pressure detection
   - PerformanceMetrics for move evaluation timing
   - These are useful enhancements

3. **Null Safety**
   - Null check for move in MoveEvaluationOperation
   - Null score fallback in move evaluation
   - These are good defensive programming practices

4. **Enhanced Error Handling**
   - Try-catch around scoreDirector.close()
   - Better error logging
   - These are good improvements

---

## Recommendations

### Priority 1: Critical Fixes (Must Fix Immediately)

1. **Implement OrderByMoveIndexBlockingQueue ordering logic**
   - Add `backlog` field (HashMap)
   - Implement full ordering in `take()` method
   - Add step index filtering in `addMove()` and `addUndoableMove()`
   - Add exception check in `startNextStep()`

2. **Remove operationQueue.clear() call**
   - Delete line 199 in MultiThreadedLocalSearchDecider
   - This prevents potential deadlocks

3. **Add hasThrownException() method**
   - Add to MoveResult class
   - Make it private (Guide 1) or public (Guide 2)

### Priority 2: High Priority

1. **Verify MoveResult exception handling**
   - Ensure exceptions are properly propagated
   - Test with various failure scenarios

2. **Review executeTemporaryMove vs doAndProcessMove**
   - Confirm they have equivalent behavior
   - Ensure move undo happens correctly

3. **Test with out-of-order results**
   - Verify backlog mechanism works correctly
   - Test with varying move evaluation times

### Priority 3: Medium Priority

1. **Document API differences**
   - Clearly document differences from OptaPlanner guides
   - Explain why changes were made

2. **Review error recovery mechanism**
   - Test fallback to single-threaded mode
   - Ensure it doesn't cause additional issues

3. **Consider removing unused fields**
   - `moveThreadCount` field in OrderByMoveIndexBlockingQueue (set to 0, never used)

### Priority 4: Low Priority

1. **Standardize logging levels**
   - Consider using TRACE for debug info (as in Guide 1)
   - Ensure consistency across components

2. **Review barrier await helper**
   - Consider extracting to helper method (as in Guide 2)
   - Reduces code duplication

---

## Testing Recommendations

### Unit Tests Needed

1. **OrderByMoveIndexBlockingQueue**
   - Test out-of-order result handling
   - Test backlog mechanism
   - Test step index filtering
   - Test exception propagation
   - Test concurrent access

2. **MultiThreadedLocalSearchDecider**
   - Test without operationQueue.clear()
   - Test error recovery mechanism
   - Test with various thread counts
   - Test with varying buffer sizes

3. **MoveThreadRunner**
   - Test all operation types
   - Test barrier synchronization
   - Test error handling
   - Test score director lifecycle

### Integration Tests Needed

1. **End-to-end multithreaded search**
   - Compare results with single-threaded mode
   - Verify deterministic behavior
   - Test with real problem instances

2. **Performance tests**
   - Measure speedup with multiple threads
   - Verify scaling behavior
   - Monitor memory usage

3. **Stress tests**
   - Long-running solves
   - Large problem instances
   - Concurrent solves

---

## Conclusion

The current greycos multithread search implementation has a **solid foundation** but contains **critical issues** in the `OrderByMoveIndexBlockingQueue` component. The missing ordering logic will cause non-deterministic behavior and incorrect results.

### Key Findings

1. **Critical Issues**: 6 (all in OrderByMoveIndexBlockingQueue and MultiThreadedLocalSearchDecider)
2. **API Differences**: Several (expected due to new API)
3. **Enhancements**: 4 (useful additions)
4. **Overall Architecture**: Correct and follows guides

### Next Steps

1. **Immediate**: Fix OrderByMoveIndexBlockingQueue ordering logic
2. **Immediate**: Remove operationQueue.clear() call
3. **Short-term**: Add comprehensive tests
4. **Medium-term**: Document API differences
5. **Long-term**: Consider additional enhancements

---

## Appendix: File References

### Greycos Implementation Files

- [`MultiThreadedLocalSearchDecider.java`](core/src/main/java/ai/greycos/solver/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java:1)
- [`MoveThreadRunner.java`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/MoveThreadRunner.java:1)
- [`OrderByMoveIndexBlockingQueue.java`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/OrderByMoveIndexBlockingQueue.java:1)
- [`MoveThreadOperation.java`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/MoveThreadOperation.java:1)
- [`SetupOperation.java`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/SetupOperation.java:1)
- [`MoveEvaluationOperation.java`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/MoveEvaluationOperation.java:1)
- [`ApplyStepOperation.java`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/ApplyStepOperation.java:1)
- [`DestroyOperation.java`](core/src/main/java/ai/greycos/solver/core/impl/heuristic/thread/DestroyOperation.java:1)

### Guide Files

- [`MULTITHREAD_SEARCH_IMPLEMENTATION.md`](docs/feature_plans/multithreading/MULTITHREAD_SEARCH_IMPLEMENTATION.md:1)
- [`MULTITHREAD_SEARCH_IMPLEMENTATION_TRUE.md`](docs/feature_plans/multithreading/MULTITHREAD_SEARCH_IMPLEMENTATION_TRUE.md:1)

---

**Document End**
