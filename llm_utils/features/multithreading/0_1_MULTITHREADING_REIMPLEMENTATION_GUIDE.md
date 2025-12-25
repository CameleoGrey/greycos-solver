# OptaPlanner Multithreading Reimplementation Guide

This guide provides detailed instructions for reimplementing multithreading support in OptaPlanner when you have a fork that only supports single-threaded solving.

## Overview

OptaPlanner supports two types of multithreading:
1. **Move Thread Multithreading**: Parallel evaluation of moves during local search and construction heuristics
2. **Partition Thread Multithreading**: Parallel solving of solution partitions

## Key Components to Implement

### 1. Thread Configuration Infrastructure

#### A. ChildThreadType Enum
Create the thread type enumeration:

```java
package org.optaplanner.core.impl.solver.thread;

public enum ChildThreadType {
    /**
     * Used by PartitionedSearchPhase.
     */
    PART_THREAD,
    /**
     * Used by multithreaded incremental solving.
     */
    MOVE_THREAD;
}
```

#### B. Thread Factory Support
Add thread factory configuration to `SolverConfig`:

```java
// In SolverConfig.java, add these fields:
protected Class<? extends ThreadFactory> threadFactoryClass = null;

// Add getters and setters:
public Class<? extends ThreadFactory> getThreadFactoryClass() {
    return threadFactoryClass;
}

public void setThreadFactoryClass(Class<? extends ThreadFactory> threadFactoryClass) {
    this.threadFactoryClass = threadFactoryClass;
}

// Add with method:
public SolverConfig withThreadFactoryClass(Class<? extends ThreadFactory> threadFactoryClass) {
    this.threadFactoryClass = threadFactoryClass;
    return this;
}
```

#### C. Default Thread Factory
Create a default thread factory:

```java
package org.optaplanner.core.impl.solver.thread;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultSolverThreadFactory implements ThreadFactory {

    private static final AtomicInteger poolNumber = new AtomicInteger(1);

    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    public DefaultSolverThreadFactory() {
        this("Solver");
    }

    public DefaultSolverThreadFactory(String threadPrefix) {
        SecurityManager s = System.getSecurityManager();
        group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        namePrefix = "OptaPool-" + poolNumber.getAndIncrement() + "-" + threadPrefix + "-";
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(group, r,
                namePrefix + threadNumber.getAndIncrement(),
                0);
        if (t.isDaemon()) {
            t.setDaemon(false);
        }
        if (t.getPriority() != Thread.NORM_PRIORITY) {
            t.setPriority(Thread.NORM_PRIORITY);
        }
        return t;
    }
}
```

### 2. Move Thread Count Resolution

#### A. Move Thread Count Constants
Add constants to `SolverConfig`:

```java
public static final String MOVE_THREAD_COUNT_NONE = "NONE";
public static final String MOVE_THREAD_COUNT_AUTO = "AUTO";
```

#### B. Move Thread Count Resolution Logic
Add to `DefaultSolverFactory`:

```java
// Required for testability as final classes cannot be mocked.
protected static class MoveThreadCountResolver {

    protected Integer resolveMoveThreadCount(String moveThreadCount) {
        int availableProcessorCount = getAvailableProcessors();
        Integer resolvedMoveThreadCount;
        if (moveThreadCount == null || moveThreadCount.equals(SolverConfig.MOVE_THREAD_COUNT_NONE)) {
            return null;
        } else if (moveThreadCount.equals(SolverConfig.MOVE_THREAD_COUNT_AUTO)) {
            // Leave one for the Operating System and 1 for the solver thread, take the rest
            resolvedMoveThreadCount = (availableProcessorCount - 2);
            // A moveThreadCount beyond 4 is currently typically slower
            if (resolvedMoveThreadCount > 4) {
                resolvedMoveThreadCount = 4;
            }
            if (resolvedMoveThreadCount <= 1) {
                // Fall back to single threaded solving with no move threads.
                return null;
            }
        } else {
            resolvedMoveThreadCount = ConfigUtils.resolvePoolSize("moveThreadCount", moveThreadCount,
                    SolverConfig.MOVE_THREAD_COUNT_NONE, SolverConfig.MOVE_THREAD_COUNT_AUTO);
        }
        if (resolvedMoveThreadCount < 1) {
            throw new IllegalArgumentException("The moveThreadCount (" + moveThreadCount
                    + ") resulted in a resolvedMoveThreadCount (" + resolvedMoveThreadCount
                    + ") that is lower than 1.");
        }
        if (resolvedMoveThreadCount > availableProcessorCount) {
            LOGGER.warn("The resolvedMoveThreadCount ({}) is higher "
                    + "than the availableProcessorCount ({}), which is counter-efficient.",
                    resolvedMoveThreadCount, availableProcessorCount);
        }
        return resolvedMoveThreadCount;
    }

    protected int getAvailableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }
}
```

#### C. HeuristicConfigPolicy Updates
Add move thread support to `HeuristicConfigPolicy`:

```java
// Add fields:
private final Integer moveThreadCount;
private final Integer moveThreadBufferSize;

// Add to Builder constructor:
public Builder(EnvironmentMode environmentMode, Integer moveThreadCount, Integer moveThreadBufferSize,
        Class<? extends ThreadFactory> threadFactoryClass, InitializingScoreTrend initializingScoreTrend,
        SolutionDescriptor<Solution_> solutionDescriptor, ClassInstanceCache classInstanceCache) {
    // ... existing code ...
    this.moveThreadCount = moveThreadCount;
    this.moveThreadBufferSize = moveThreadBufferSize;
    // ... rest of constructor ...
}

// Add getters:
public Integer getMoveThreadCount() {
    return moveThreadCount;
}

public Integer getMoveThreadBufferSize() {
    return moveThreadBufferSize;
}

// Add thread factory builder method:
public ThreadFactory buildThreadFactory(ChildThreadType childThreadType) {
    if (threadFactoryClass != null) {
        return ConfigUtils.newInstance(this::toString, "threadFactoryClass", threadFactoryClass);
    } else {
        String threadPrefix;
        switch (childThreadType) {
            case MOVE_THREAD:
                threadPrefix = "MoveThread";
                break;
            case PART_THREAD:
                threadPrefix = "PartThread";
                break;
            default:
                throw new IllegalStateException("Unsupported childThreadType (" + childThreadType + ").");
        }
        return new DefaultSolverThreadFactory(threadPrefix);
    }
}
```

### 3. Move Thread Infrastructure

#### A. Move Thread Operations
Create the base operation classes:

```java
package org.optaplanner.core.impl.heuristic.thread;

public abstract class MoveThreadOperation<Solution_> {
    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
```

```java
package org.optaplanner.core.impl.heuristic.thread;

public class SetupOperation<Solution_, Score_ extends Score<Score_>> extends MoveThreadOperation<Solution_> {
    private final InnerScoreDirector<Solution_, Score_> scoreDirector;

    public SetupOperation(InnerScoreDirector<Solution_, Score_> scoreDirector) {
        this.scoreDirector = scoreDirector;
    }

    public InnerScoreDirector<Solution_, Score_> getScoreDirector() {
        return scoreDirector;
    }
}
```

```java
package org.optaplanner.core.impl.heuristic.thread;

public class DestroyOperation<Solution_> extends MoveThreadOperation<Solution_> {
    // Empty implementation
}
```

```java
package org.optaplanner.core.impl.heuristic.thread;

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

```java
package org.optaplanner.core.impl.heuristic.thread;

public class ApplyStepOperation<Solution_, Score_ extends Score<Score_>> extends MoveThreadOperation<Solution_> {
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

#### B. Move Thread Runner
Create the core move thread runner:

```java
package org.optaplanner.core.impl.heuristic.thread;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;

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
                    SetupOperation<Solution_, Score_> setupOperation = (SetupOperation<Solution_, Score_>) operation;
                    scoreDirector = setupOperation.getScoreDirector()
                            .createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
                    stepIndex = 0;
                    lastStepScore = scoreDirector.calculateScore();
                    try {
                        moveThreadBarrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else if (operation instanceof DestroyOperation) {
                    calculationCount.set(scoreDirector.getCalculationCount());
                    break;
                } else if (operation instanceof ApplyStepOperation) {
                    ApplyStepOperation<Solution_, Score_> applyStepOperation =
                            (ApplyStepOperation<Solution_, Score_>) operation;
                    if (stepIndex + 1 != applyStepOperation.getStepIndex()) {
                        throw new IllegalStateException("Impossible situation: the moveThread's stepIndex (" + stepIndex
                                + ") is not followed by the operation's stepIndex ("
                                + applyStepOperation.getStepIndex() + ").");
                    }
                    stepIndex = applyStepOperation.getStepIndex();
                    Move<Solution_> step = applyStepOperation.getStep().rebase(scoreDirector);
                    Score_ score = applyStepOperation.getScore();
                    step.doMoveOnly(scoreDirector);
                    predictWorkingStepScore(step, score);
                    lastStepScore = score;
                    try {
                        moveThreadBarrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else if (operation instanceof MoveEvaluationOperation) {
                    MoveEvaluationOperation<Solution_> moveEvaluationOperation = (MoveEvaluationOperation<Solution_>) operation;
                    int moveIndex = moveEvaluationOperation.getMoveIndex();
                    if (stepIndex != moveEvaluationOperation.getStepIndex()) {
                        throw new IllegalStateException("Impossible situation: the moveThread's stepIndex ("
                                + stepIndex + ") differs from the operation's stepIndex ("
                                + moveEvaluationOperation.getStepIndex() + ") with moveIndex ("
                                + moveIndex + ").");
                    }
                    Move<Solution_> move = moveEvaluationOperation.getMove().rebase(scoreDirector);
                    if (evaluateDoable && !move.isMoveDoable(scoreDirector)) {
                        resultQueue.addUndoableMove(moveThreadIndex, stepIndex, moveIndex, move);
                    } else {
                        Score<?> score = scoreDirector.doAndProcessMove(move, assertMoveScoreFromScratch);
                        if (assertExpectedUndoMoveScore) {
                            scoreDirector.assertExpectedUndoMoveScore(move, lastStepScore);
                        }
                        resultQueue.addMove(moveThreadIndex, stepIndex, moveIndex, move, score);
                    }
                } else {
                    throw new IllegalStateException("Unknown operation (" + operation + ").");
                }
            }
        } catch (RuntimeException | Error throwable) {
            resultQueue.addExceptionThrown(moveThreadIndex, throwable);
        } finally {
            if (scoreDirector != null) {
                scoreDirector.close();
            }
        }
    }

    protected void predictWorkingStepScore(Move<Solution_> step, Score_ score) {
        scoreDirector.getSolutionDescriptor().setScore(scoreDirector.getWorkingSolution(), score);
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
                    + " because move thread ({})'s destroy wasn't processed soon enough.", logIndentation, moveThreadIndex);
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

#### C. OrderByMoveIndexBlockingQueue
This queue ensures moves are processed in the correct order:

```java
package org.optaplanner.core.impl.heuristic.thread;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class OrderByMoveIndexBlockingQueue<Solution_> {
    
    public static class MoveResult<Solution_> {
        private final int moveThreadIndex;
        private final int stepIndex;
        private final int moveIndex;
        private final Move<Solution_> move;
        private final boolean moveDoable;
        private final Score<?> score;
        private final Throwable throwable;

        public MoveResult(int moveThreadIndex, int stepIndex, int moveIndex, Move<Solution_> move, Score<?> score) {
            this.moveThreadIndex = moveThreadIndex;
            this.stepIndex = stepIndex;
            this.moveIndex = moveIndex;
            this.move = move;
            this.moveDoable = true;
            this.score = score;
            this.throwable = null;
        }

        public MoveResult(int moveThreadIndex, int stepIndex, int moveIndex, Move<Solution_> move) {
            this.moveThreadIndex = moveThreadIndex;
            this.stepIndex = stepIndex;
            this.moveIndex = moveIndex;
            this.move = move;
            this.moveDoable = false;
            this.score = null;
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

        // Getters...
    }

    private final BlockingQueue<MoveResult<Solution_>> queue;
    private final int moveThreadCount;
    private final AtomicInteger nextStepIndex = new AtomicInteger(-1);
    private final AtomicInteger nextMoveIndex = new AtomicInteger(-1);

    public OrderByMoveIndexBlockingQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.moveThreadCount = 0; // Will be set when threads are created
    }

    public void startNextStep(int stepIndex) {
        nextStepIndex.set(stepIndex);
        nextMoveIndex.set(0);
    }

    public void addMove(int moveThreadIndex, int stepIndex, int moveIndex, Move<Solution_> move, Score<?> score) {
        queue.add(new MoveResult<>(moveThreadIndex, stepIndex, moveIndex, move, score));
    }

    public void addUndoableMove(int moveThreadIndex, int stepIndex, int moveIndex, Move<Solution_> move) {
        queue.add(new MoveResult<>(moveThreadIndex, stepIndex, moveIndex, move));
    }

    public void addExceptionThrown(int moveThreadIndex, Throwable throwable) {
        queue.add(new MoveResult<>(moveThreadIndex, throwable));
    }

    public MoveResult<Solution_> take() throws InterruptedException {
        return queue.take();
    }
}
```

### 4. Multithreaded Deciders

#### A. MultiThreadedLocalSearchDecider
Create the multithreaded local search decider:

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

    public MultiThreadedLocalSearchDecider(String logIndentation, Termination<Solution_> termination,
            MoveSelector<Solution_> moveSelector, Acceptor<Solution_> acceptor, LocalSearchForager<Solution_> forager,
            ThreadFactory threadFactory, int moveThreadCount, int selectedMoveBufferSize) {
        super(logIndentation, termination, moveSelector, acceptor, forager);
        this.threadFactory = threadFactory;
        this.moveThreadCount = moveThreadCount;
        this.selectedMoveBufferSize = selectedMoveBufferSize;
    }

    @Override
    public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
        super.phaseStarted(phaseScope);
        operationQueue = new ArrayBlockingQueue<>(selectedMoveBufferSize + moveThreadCount + moveThreadCount);
        resultQueue = new OrderByMoveIndexBlockingQueue<>(selectedMoveBufferSize + moveThreadCount);
        moveThreadBarrier = new CyclicBarrier(moveThreadCount);
        InnerScoreDirector<Solution_, ?> scoreDirector = phaseScope.getScoreDirector();
        executor = createThreadPoolExecutor();
        moveThreadRunnerList = new ArrayList<>(moveThreadCount);
        for (int moveThreadIndex = 0; moveThreadIndex < moveThreadCount; moveThreadIndex++) {
            MoveThreadRunner<Solution_, ?> moveThreadRunner = new MoveThreadRunner<>(
                    logIndentation, moveThreadIndex, true,
                    operationQueue, resultQueue, moveThreadBarrier,
                    assertMoveScoreFromScratch, assertExpectedUndoMoveScore,
                    assertStepScoreFromScratch, assertExpectedStepScore, assertShadowVariablesAreNotStaleAfterStep);
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
    public void solvingError(SolverScope<Solution_> solverScope, Exception exception) {
        super.solvingError(solverScope, exception);
        shutdownMoveThreads();
    }

    protected ExecutorService createThreadPoolExecutor() {
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(moveThreadCount,
                threadFactory);
        if (threadPoolExecutor.getMaximumPoolSize() < moveThreadCount) {
            throw new IllegalStateException(
                    "The threadPoolExecutor's maximumPoolSize (" + threadPoolExecutor.getMaximumPoolSize()
                            + ") is less than the moveThreadCount (" + moveThreadCount + "), this is unsupported.");
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
            if (movesInPlay > 0 && (selectMoveIndex >= selectedMoveBufferSize || !hasNextMove)) {
                if (forageResult(stepScope, stepIndex)) {
                    break;
                }
                movesInPlay--;
            }
            if (hasNextMove) {
                Move<Solution_> move = moveIterator.next();
                operationQueue.add(new MoveEvaluationOperation<>(stepIndex, selectMoveIndex, move));
                selectMoveIndex++;
                movesInPlay++;
            }
        } while (movesInPlay > 0);

        operationQueue.clear();
        pickMove(stepScope);
        if (stepScope.getStep() != null) {
            InnerScoreDirector<Solution_, ?> scoreDirector = stepScope.getScoreDirector();
            ApplyStepOperation<Solution_, ?> stepOperation =
                    new ApplyStepOperation<>(stepIndex + 1, stepScope.getStep(), (Score) stepScope.getScore());
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
            throw new IllegalStateException("Impossible situation: the solverThread's stepIndex (" + stepIndex
                    + ") differs from the result's stepIndex (" + result.getStepIndex() + ").");
        }
        Move<Solution_> foragingMove = result.getMove().rebase(stepScope.getScoreDirector());
        int foragingMoveIndex = result.getMoveIndex();
        LocalSearchMoveScope<Solution_> moveScope = new LocalSearchMoveScope<>(stepScope, foragingMoveIndex, foragingMove);
        if (!result.isMoveDoable()) {
            logger.trace("{}        Move index ({}) not doable, ignoring move ({}).",
                    logIndentation, foragingMoveIndex, foragingMove);
        } else {
            moveScope.setScore(result.getScore());
            moveScope.getScoreDirector().incrementCalculationCount();
            boolean accepted = acceptor.isAccepted(moveScope);
            moveScope.setAccepted(accepted);
            logger.trace("{}        Move index ({}), score ({}), accepted ({}), move ({}).",
                    logIndentation,
                    foragingMoveIndex, moveScope.getScore(), moveScope.getAccepted(),
                    foragingMove);
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
            ThreadUtils.shutdownAwaitOrKill(executor, logIndentation, "Multi-threaded Local Search");
        }
    }
}
```

#### B. MultiThreadedConstructionHeuristicDecider
Create the multithreaded construction heuristic decider:

```java
package org.optaplanner.core.impl.constructionheuristic.decider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class MultiThreadedConstructionHeuristicDecider<Solution_> extends ConstructionHeuristicDecider<Solution_> {

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

    public MultiThreadedConstructionHeuristicDecider(String logIndentation, Termination<Solution_> termination,
            MoveSelector<Solution_> moveSelector, Acceptor<Solution_> acceptor, ConstructionHeuristicForager<Solution_> forager,
            ThreadFactory threadFactory, int moveThreadCount, int selectedMoveBufferSize) {
        super(logIndentation, termination, moveSelector, acceptor, forager);
        this.threadFactory = threadFactory;
        this.moveThreadCount = moveThreadCount;
        this.selectedMoveBufferSize = selectedMoveBufferSize;
    }

    @Override
    public void phaseStarted(ConstructionHeuristicPhaseScope<Solution_> phaseScope) {
        super.phaseStarted(phaseScope);
        operationQueue = new ArrayBlockingQueue<>(selectedMoveBufferSize + moveThreadCount + moveThreadCount);
        resultQueue = new OrderByMoveIndexBlockingQueue<>(selectedMoveBufferSize + moveThreadCount);
        moveThreadBarrier = new CyclicBarrier(moveThreadCount);
        InnerScoreDirector<Solution_, ?> scoreDirector = phaseScope.getScoreDirector();
        executor = createThreadPoolExecutor();
        moveThreadRunnerList = new ArrayList<>(moveThreadCount);
        for (int moveThreadIndex = 0; moveThreadIndex < moveThreadCount; moveThreadIndex++) {
            MoveThreadRunner<Solution_, ?> moveThreadRunner = new MoveThreadRunner<>(
                    logIndentation, moveThreadIndex, true,
                    operationQueue, resultQueue, moveThreadBarrier,
                    assertMoveScoreFromScratch, assertExpectedUndoMoveScore,
                    assertStepScoreFromScratch, assertExpectedStepScore, assertShadowVariablesAreNotStaleAfterStep);
            moveThreadRunnerList.add(moveThreadRunner);
            executor.submit(moveThreadRunner);
            operationQueue.add(new SetupOperation<>(scoreDirector));
        }
    }

    @Override
    public void phaseEnded(ConstructionHeuristicPhaseScope<Solution_> phaseScope) {
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
    public void solvingError(SolverScope<Solution_> solverScope, Exception exception) {
        super.solvingError(solverScope, exception);
        shutdownMoveThreads();
    }

    protected ExecutorService createThreadPoolExecutor() {
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(moveThreadCount,
                threadFactory);
        if (threadPoolExecutor.getMaximumPoolSize() < moveThreadCount) {
            throw new IllegalStateException(
                    "The threadPoolExecutor's maximumPoolSize (" + threadPoolExecutor.getMaximumPoolSize()
                            + ") is less than the moveThreadCount (" + moveThreadCount + "), this is unsupported.");
        }
        return threadPoolExecutor;
    }

    @Override
    public void decideNextStep(ConstructionHeuristicStepScope<Solution_> stepScope) {
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
                operationQueue.add(new MoveEvaluationOperation<>(stepIndex, selectMoveIndex, move));
                selectMoveIndex++;
                movesInPlay++;
            }
        } while (movesInPlay > 0);

        operationQueue.clear();
        pickMove(stepScope);
        if (stepScope.getStep() != null) {
            InnerScoreDirector<Solution_, ?> scoreDirector = stepScope.getScoreDirector();
            ApplyStepOperation<Solution_, ?> stepOperation =
                    new ApplyStepOperation<>(stepIndex + 1, stepScope.getStep(), (Score) stepScope.getScore());
            for (int i = 0; i < moveThreadCount; i++) {
                operationQueue.add(stepOperation);
            }
        }
    }

    private boolean forageResult(ConstructionHeuristicStepScope<Solution_> stepScope, int stepIndex) {
        OrderByMoveIndexBlockingQueue.MoveResult<Solution_> result;
        try {
            result = resultQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
        if (stepIndex != result.getStepIndex()) {
            throw new IllegalStateException("Impossible situation: the solverThread's stepIndex (" + stepIndex
                    + ") differs from the result's stepIndex (" + result.getStepIndex() + ").");
        }
        Move<Solution_> foragingMove = result.getMove().rebase(stepScope.getScoreDirector());
        int foragingMoveIndex = result.getMoveIndex();
        ConstructionHeuristicMoveScope<Solution_> moveScope = new ConstructionHeuristicMoveScope<>(stepScope, foragingMoveIndex, foragingMove);
        if (!result.isMoveDoable()) {
            logger.trace("{}        Move index ({}) not doable, ignoring move ({}).",
                    logIndentation, foragingMoveIndex, foragingMove);
        } else {
            moveScope.setScore(result.getScore());
            moveScope.getScoreDirector().incrementCalculationCount();
            boolean accepted = acceptor.isAccepted(moveScope);
            moveScope.setAccepted(accepted);
            logger.trace("{}        Move index ({}), score ({}), accepted ({}), move ({}).",
                    logIndentation,
                    foragingMoveIndex, moveScope.getScore(), moveScope.getAccepted(),
                    foragingMove);
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
            ThreadUtils.shutdownAwaitOrKill(executor, logIndentation, "Multi-threaded Construction Heuristic");
        }
    }
}
```

### 5. Phase Factory Updates

#### A. Local Search Phase Factory
Update `DefaultLocalSearchPhaseFactory` to support multithreading:

```java
// In DefaultLocalSearchPhaseFactory.buildDecider():
private LocalSearchDecider<Solution_> buildDecider(HeuristicConfigPolicy<Solution_> configPolicy,
        Termination<Solution_> termination) {
    MoveSelector<Solution_> moveSelector = buildMoveSelector(configPolicy);
    Acceptor<Solution_> acceptor = buildAcceptor(configPolicy);
    LocalSearchForager<Solution_> forager = buildForager(configPolicy);
    if (moveSelector.isNeverEnding() && !forager.supportsNeverEndingMoveSelector()) {
        throw new IllegalStateException("The moveSelector (" + moveSelector
                + ") has neverEnding (" + moveSelector.isNeverEnding()
                + "), but the forager (" + forager
                + ") does not support it.\n"
                + "Maybe configure the <forager> with an <acceptedCountLimit>.");
    }
    Integer moveThreadCount = configPolicy.getMoveThreadCount();
    EnvironmentMode environmentMode = configPolicy.getEnvironmentMode();
    LocalSearchDecider<Solution_> decider;
    if (moveThreadCount == null) {
        decider = new LocalSearchDecider<>(configPolicy.getLogIndentation(), termination, moveSelector, acceptor, forager);
    } else {
        Integer moveThreadBufferSize = configPolicy.getMoveThreadBufferSize();
        if (moveThreadBufferSize == null) {
            moveThreadBufferSize = 10;
        }
        ThreadFactory threadFactory = configPolicy.buildThreadFactory(ChildThreadType.MOVE_THREAD);
        int selectedMoveBufferSize = moveThreadCount * moveThreadBufferSize;
        MultiThreadedLocalSearchDecider<Solution_> multiThreadedDecider = new MultiThreadedLocalSearchDecider<>(
                configPolicy.getLogIndentation(), termination, moveSelector, acceptor, forager,
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

#### B. Construction Heuristic Phase Factory
Update `DefaultConstructionHeuristicPhaseFactory` to support multithreading:

```java
// In DefaultConstructionHeuristicPhaseFactory.buildDecider():
private ConstructionHeuristicDecider<Solution_> buildDecider(HeuristicConfigPolicy<Solution_> configPolicy,
        Termination<Solution_> termination) {
    MoveSelector<Solution_> moveSelector = buildMoveSelector(configPolicy);
    Acceptor<Solution_> acceptor = buildAcceptor(configPolicy);
    ConstructionHeuristicForager<Solution_> forager = buildForager(configPolicy);
    if (moveSelector.isNeverEnding() && !forager.supportsNeverEndingMoveSelector()) {
        throw new IllegalStateException("The moveSelector (" + moveSelector
                + ") has neverEnding (" + moveSelector.isNeverEnding()
                + "), but the forager (" + forager
                + ") does not support it.\n"
                + "Maybe configure the <forager> with an <acceptedCountLimit>.");
    }
    Integer moveThreadCount = configPolicy.getMoveThreadCount();
    EnvironmentMode environmentMode = configPolicy.getEnvironmentMode();
    ConstructionHeuristicDecider<Solution_> decider;
    if (moveThreadCount == null) {
        decider = new ConstructionHeuristicDecider<>(configPolicy.getLogIndentation(), termination, moveSelector, acceptor, forager);
    } else {
        Integer moveThreadBufferSize = configPolicy.getMoveThreadBufferSize();
        if (moveThreadBufferSize == null) {
            moveThreadBufferSize = 10;
        }
        ThreadFactory threadFactory = configPolicy.buildThreadFactory(ChildThreadType.MOVE_THREAD);
        int selectedMoveBufferSize = moveThreadCount * moveThreadBufferSize;
        MultiThreadedConstructionHeuristicDecider<Solution_> multiThreadedDecider = new MultiThreadedConstructionHeuristicDecider<>(
                configPolicy.getLogIndentation(), termination, moveSelector, acceptor, forager,
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

### 6. Configuration Usage

To enable multithreading in your solver configuration:

```java
SolverConfig solverConfig = new SolverConfig();
solverConfig.setMoveThreadCount("4"); // Use 4 move threads
// or
solverConfig.setMoveThreadCount("AUTO"); // Auto-detect based on CPU count
// or
solverConfig.setMoveThreadCount("NONE"); // Single-threaded (default)

// Optional: Set thread factory
solverConfig.setThreadFactoryClass(MyCustomThreadFactory.class);

// Optional: Set move thread buffer size
solverConfig.setMoveThreadBufferSize(20);
```

### 7. Testing and Validation

Create test cases to validate the implementation:

```java
@Test
void testMultiThreadedSolving() {
    SolverConfig solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    solverConfig.setMoveThreadCount("2");
    
    TestdataSolution solution = createTestSolution(10, 5);
    solution = PlannerTestUtils.solve(solverConfig, solution);
    
    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
}
```

## Implementation Notes

1. **Thread Safety**: Ensure all shared data structures are thread-safe
2. **Resource Management**: Properly clean up thread pools and resources
3. **Error Handling**: Propagate exceptions from child threads to the main solver thread
4. **Performance**: Monitor and tune the move thread buffer size for optimal performance
5. **Compatibility**: Ensure backward compatibility with existing single-threaded configurations

## Troubleshooting

- **Deadlocks**: Check barrier synchronization and queue management
- **Performance Issues**: Adjust move thread buffer size and thread count
- **Memory Usage**: Monitor memory consumption with many threads
- **Assertion Failures**: Disable assertions in production for better performance

This implementation provides a complete multithreading framework for OptaPlanner that can significantly improve solving performance on multi-core systems.