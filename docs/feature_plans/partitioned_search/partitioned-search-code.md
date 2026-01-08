# Partitioned Search - Source Code Reference

This document contains all source code for the partitioned search implementation in OptaPlanner.

## Table of Contents

- [Core Implementation](#core-implementation)
- [Configuration Classes](#configuration-classes)
- [Scope Classes](#scope-classes)
- [Queue and Events](#queue-and-events)
- [Partitioner Interface](#partitioner-interface)
- [Example Partitioner](#example-partitioner)
- [User Configuration Examples](#user-configuration-examples)

---

## Core Implementation

### PartitionedSearchPhase.java

```java
package org.optaplanner.core.impl.partitionedsearch;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.impl.phase.AbstractPhase;
import org.optaplanner.core.impl.phase.Phase;

/**
 * A {@link PartitionedSearchPhase} is a {@link Phase} which uses a Partition Search algorithm.
 * It splits {@link PlanningSolution} into pieces and solves those separately with other {@link Phase}s.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @see Phase
 * @see AbstractPhase
 * @see DefaultPartitionedSearchPhase
 */
public interface PartitionedSearchPhase<Solution_> extends Phase<Solution_> {

}
```

### DefaultPartitionedSearchPhase.java

```java
package org.optaplanner.core.impl.partitionedsearch;

import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.config.phase.PhaseConfig;
import org.optaplanner.core.impl.heuristic.HeuristicConfigPolicy;
import org.optaplanner.core.impl.heuristic.move.Move;
import org.optaplanner.core.impl.partitionedsearch.event.PartitionedSearchPhaseLifecycleListener;
import org.optaplanner.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import org.optaplanner.core.impl.partitionedsearch.queue.PartitionQueue;
import org.optaplanner.core.impl.partitionedsearch.scope.PartitionChangeMove;
import org.optaplanner.core.impl.partitionedsearch.scope.PartitionedSearchPhaseScope;
import org.optaplanner.core.impl.partitionedsearch.scope.PartitionedSearchStepScope;
import org.optaplanner.core.impl.phase.AbstractPhase;
import org.optaplanner.core.impl.phase.Phase;
import org.optaplanner.core.impl.phase.PhaseFactory;
import org.optaplanner.core.impl.score.director.InnerScoreDirector;
import org.optaplanner.core.impl.solver.recaller.BestSolutionRecaller;
import org.optaplanner.core.impl.solver.recaller.BestSolutionRecallerFactory;
import org.optaplanner.core.impl.solver.scope.SolverScope;
import org.optaplanner.core.impl.solver.termination.ChildThreadPlumbingTermination;
import org.optaplanner.core.impl.solver.termination.OrCompositeTermination;
import org.optaplanner.core.impl.solver.termination.Termination;
import org.optaplanner.core.impl.solver.thread.ChildThreadType;
import org.optaplanner.core.impl.solver.thread.ThreadUtils;

/**
 * Default implementation of {@link PartitionedSearchPhase}.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class DefaultPartitionedSearchPhase<Solution_> extends AbstractPhase<Solution_>
        implements PartitionedSearchPhase<Solution_>, PartitionedSearchPhaseLifecycleListener<Solution_> {

    protected final SolutionPartitioner<Solution_> solutionPartitioner;
    protected final ThreadFactory threadFactory;
    protected final Integer runnablePartThreadLimit;

    protected final List<PhaseConfig> phaseConfigList;
    protected final HeuristicConfigPolicy<Solution_> configPolicy;

    private DefaultPartitionedSearchPhase(Builder<Solution_> builder) {
        super(builder);
        solutionPartitioner = builder.solutionPartitioner;
        threadFactory = builder.threadFactory;
        runnablePartThreadLimit = builder.runnablePartThreadLimit;
        phaseConfigList = builder.phaseConfigList;
        configPolicy = builder.configPolicy;
    }

    @Override
    public String getPhaseTypeString() {
        return "Partitioned Search";
    }

    // ************************************************************************
    // Worker methods
    // ************************************************************************

    @Override
    public void solve(SolverScope<Solution_> solverScope) {
        PartitionedSearchPhaseScope<Solution_> phaseScope = new PartitionedSearchPhaseScope<>(solverScope);
        List<Solution_> partList = solutionPartitioner.splitWorkingSolution(
                solverScope.getScoreDirector(), runnablePartThreadLimit);
        int partCount = partList.size();
        phaseScope.setPartCount(partCount);
        phaseStarted(phaseScope);
        ExecutorService executor = createThreadPoolExecutor(partCount);
        ChildThreadPlumbingTermination<Solution_> childThreadPlumbingTermination =
                new ChildThreadPlumbingTermination<>();
        PartitionQueue<Solution_> partitionQueue = new PartitionQueue<>(partCount);
        Semaphore runnablePartThreadSemaphore = runnablePartThreadLimit == null ? null
                : new Semaphore(runnablePartThreadLimit, true);
        try {
            for (ListIterator<Solution_> it = partList.listIterator(); it.hasNext();) {
                int partIndex = it.nextIndex();
                Solution_ part = it.next();
                PartitionSolver<Solution_> partitionSolver = buildPartitionSolver(
                        childThreadPlumbingTermination, runnablePartThreadSemaphore, solverScope);
                partitionSolver.addEventListener(event -> {
                    InnerScoreDirector<Solution_, ?> childScoreDirector =
                            partitionSolver.solverScope.getScoreDirector();
                    PartitionChangeMove<Solution_> move = PartitionChangeMove.createMove(childScoreDirector, partIndex);
                    InnerScoreDirector<Solution_, ?> parentScoreDirector = solverScope.getScoreDirector();
                    move = move.rebase(parentScoreDirector);
                    partitionQueue.addMove(partIndex, move);
                });
                executor.submit(() -> {
                    try {
                        partitionSolver.solve(part);
                        long partCalculationCount = partitionSolver.getScoreCalculationCount();
                        partitionQueue.addFinish(partIndex, partCalculationCount);
                    } catch (Throwable throwable) {
                        // Any Exception or even Error that happens here (on a partition thread) must be stored
                        // in the partitionQueue in order to be propagated to the solver thread.
                        logger.trace("{}            Part thread ({}) exception that will be propagated to the solver thread.",
                                logIndentation, partIndex, throwable);
                        partitionQueue.addExceptionThrown(partIndex, throwable);
                    }
                });
            }
            for (PartitionChangeMove<Solution_> step : partitionQueue) {
                PartitionedSearchStepScope<Solution_> stepScope = new PartitionedSearchStepScope<>(phaseScope);
                stepStarted(stepScope);
                stepScope.setStep(step);
                if (logger.isDebugEnabled()) {
                    stepScope.setStepString(step.toString());
                }
                doStep(stepScope);
                stepEnded(stepScope);
                phaseScope.setLastCompletedStepScope(stepScope);
            }
            phaseScope.addChildThreadsScoreCalculationCount(partitionQueue.getPartsCalculationCount());
        } finally {
            // In case one of the partition threads threw an Exception, it is propagated here
            // but other partition threads are not aware of the failure and may continue solving for a long time,
            // so we need to ask them to terminate. In case no exception was thrown, this does nothing.
            childThreadPlumbingTermination.terminateChildren();
            ThreadUtils.shutdownAwaitOrKill(executor, logIndentation, "Partitioned Search");
        }
        phaseEnded(phaseScope);
    }

    private ExecutorService createThreadPoolExecutor(int partCount) {
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(partCount, threadFactory);
        if (threadPoolExecutor.getMaximumPoolSize() < partCount) {
            throw new IllegalStateException(
                    "The threadPoolExecutor's maximumPoolSize (" + threadPoolExecutor.getMaximumPoolSize()
                            + ") is less than the partCount (" + partCount + "), so some partitions will starve.\n"
                            + "Normally this is impossible because the threadPoolExecutor should be unbounded."
                            + " Use runnablePartThreadLimit (" + runnablePartThreadLimit
                            + ") instead to avoid CPU hogging and live locks.");
        }
        return threadPoolExecutor;
    }

    public PartitionSolver<Solution_> buildPartitionSolver(
            ChildThreadPlumbingTermination<Solution_> childThreadPlumbingTermination, Semaphore runnablePartThreadSemaphore,
            SolverScope<Solution_> solverScope) {
        BestSolutionRecaller<Solution_> bestSolutionRecaller =
                BestSolutionRecallerFactory.create().buildBestSolutionRecaller(configPolicy.getEnvironmentMode());
        Termination<Solution_> partTermination = new OrCompositeTermination<>(childThreadPlumbingTermination,
                phaseTermination.createChildThreadTermination(solverScope, ChildThreadType.PART_THREAD));
        List<Phase<Solution_>> phaseList =
                PhaseFactory.buildPhases(phaseConfigList, configPolicy, bestSolutionRecaller, partTermination);

        // TODO create PartitionSolverScope alternative to deal with 3 layer terminations
        SolverScope<Solution_> partSolverScope = solverScope.createChildThreadSolverScope(ChildThreadType.PART_THREAD);
        partSolverScope.setRunnableThreadSemaphore(runnablePartThreadSemaphore);
        return new PartitionSolver<>(bestSolutionRecaller, partTermination, phaseList, partSolverScope);
    }

    protected void doStep(PartitionedSearchStepScope<Solution_> stepScope) {
        Move<Solution_> nextStep = stepScope.getStep();
        nextStep.doMoveOnly(stepScope.getScoreDirector());
        calculateWorkingStepScore(stepScope, nextStep);
        solver.getBestSolutionRecaller().processWorkingSolutionDuringStep(stepScope);
    }

    @Override
    public void phaseStarted(PartitionedSearchPhaseScope<Solution_> phaseScope) {
        super.phaseStarted(phaseScope);
    }

    @Override
    public void stepStarted(PartitionedSearchStepScope<Solution_> stepScope) {
        super.stepStarted(stepScope);
    }

    @Override
    public void stepEnded(PartitionedSearchStepScope<Solution_> stepScope) {
        super.stepEnded(stepScope);
        PartitionedSearchPhaseScope<Solution_> phaseScope = stepScope.getPhaseScope();
        if (logger.isDebugEnabled()) {
            logger.debug("{}    PS step ({}), time spent ({}), score ({}), {} best score ({}), picked move ({}).",
                    logIndentation,
                    stepScope.getStepIndex(),
                    phaseScope.calculateSolverTimeMillisSpentUpToNow(),
                    stepScope.getScore(),
                    (stepScope.getBestScoreImproved() ? "new" : "   "), phaseScope.getBestScore(),
                    stepScope.getStepString());
        }
    }

    @Override
    public void phaseEnded(PartitionedSearchPhaseScope<Solution_> phaseScope) {
        super.phaseEnded(phaseScope);
        phaseScope.endingNow();
        logger.info("{}Partitioned Search phase ({}) ended: time spent ({}), best score ({}),"
                + " score calculation speed ({}/sec), step total ({}), partCount ({}), runnablePartThreadLimit ({}).",
                logIndentation,
                phaseIndex,
                phaseScope.calculateSolverTimeMillisSpentUpToNow(),
                phaseScope.getBestScore(),
                phaseScope.getPhaseScoreCalculationSpeed(),
                phaseScope.getNextStepIndex(),
                phaseScope.getPartCount(),
                runnablePartThreadLimit);
    }

    public static class Builder<Solution_> extends AbstractPhase.Builder<Solution_> {

        private final SolutionPartitioner<Solution_> solutionPartitioner;
        private final ThreadFactory threadFactory;
        private final Integer runnablePartThreadLimit;
        private final List<PhaseConfig> phaseConfigList;
        private final HeuristicConfigPolicy<Solution_> configPolicy;

        public Builder(int phaseIndex, String logIndentation, Termination<Solution_> phaseTermination,
                SolutionPartitioner<Solution_> solutionPartitioner, ThreadFactory threadFactory,
                Integer runnablePartThreadLimit, List<PhaseConfig> phaseConfigList,
                HeuristicConfigPolicy<Solution_> configPolicy) {
            super(phaseIndex, logIndentation, phaseTermination);
            this.solutionPartitioner = solutionPartitioner;
            this.threadFactory = threadFactory;
            this.runnablePartThreadLimit = runnablePartThreadLimit;
            this.phaseConfigList = List.copyOf(phaseConfigList);
            this.configPolicy = configPolicy;
        }

        @Override
        public DefaultPartitionedSearchPhase<Solution_> build() {
            return new DefaultPartitionedSearchPhase<>(this);
        }
    }
}
```

### PartitionSolver.java

```java
package org.optaplanner.core.impl.partitionedsearch;

import java.util.List;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.solver.ProblemFactChange;
import org.optaplanner.core.api.solver.change.ProblemChange;
import org.optaplanner.core.impl.phase.Phase;
import org.optaplanner.core.impl.solver.AbstractSolver;
import org.optaplanner.core.impl.solver.recaller.BestSolutionRecaller;
import org.optaplanner.core.impl.solver.scope.SolverScope;
import org.optaplanner.core.impl.solver.termination.Termination;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class PartitionSolver<Solution_> extends AbstractSolver<Solution_> {

    protected final SolverScope<Solution_> solverScope;

    // ************************************************************************
    // Constructors and simple getters/setters
    // ************************************************************************

    public PartitionSolver(BestSolutionRecaller<Solution_> bestSolutionRecaller, Termination<Solution_> termination,
            List<Phase<Solution_>> phaseList, SolverScope<Solution_> solverScope) {
        super(bestSolutionRecaller, termination, phaseList);
        this.solverScope = solverScope;
    }

    // ************************************************************************
    // Complex getters
    // ************************************************************************

    @Override
    public boolean isSolving() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean terminateEarly() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isTerminateEarly() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addProblemFactChange(ProblemFactChange<Solution_> problemFactChange) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addProblemFactChanges(List<ProblemFactChange<Solution_>> problemFactChanges) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addProblemChange(ProblemChange<Solution_> problemChange) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addProblemChanges(List<ProblemChange<Solution_>> problemChangeList) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEveryProblemChangeProcessed() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEveryProblemFactChangeProcessed() {
        throw new UnsupportedOperationException();
    }

    // ************************************************************************
    // Worker methods
    // ************************************************************************

    @Override
    public Solution_ solve(Solution_ problem) {
        solverScope.initializeYielding();
        try {
            solverScope.setBestSolution(problem);
            solvingStarted(solverScope);
            runPhases(solverScope);
            solvingEnded(solverScope);
            return solverScope.getBestSolution();
        } finally {
            solverScope.destroyYielding();
        }
    }

    @Override
    public void solvingStarted(SolverScope<Solution_> solverScope) {
        solverScope.setWorkingSolutionFromBestSolution();
        super.solvingStarted(solverScope);
    }

    @Override
    public void solvingEnded(SolverScope<Solution_> solverScope) {
        super.solvingEnded(solverScope);
        solverScope.getScoreDirector().close();
        // TODO log?
    }

    public long getScoreCalculationCount() {
        return solverScope.getScoreCalculationCount();
    }

}
```

### DefaultPartitionedSearchPhaseFactory.java

```java
package org.optaplanner.core.impl.partitionedsearch;

import static org.optaplanner.core.config.partitionedsearch.PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_AUTO;
import static org.optaplanner.core.config.partitionedsearch.PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_UNLIMITED;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadFactory;

import org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import org.optaplanner.core.config.localsearch.LocalSearchPhaseConfig;
import org.optaplanner.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import org.optaplanner.core.config.phase.PhaseConfig;
import org.optaplanner.core.config.solver.EnvironmentMode;
import org.optaplanner.core.config.util.ConfigUtils;
import org.optaplanner.core.impl.heuristic.HeuristicConfigPolicy;
import org.optaplanner.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import org.optaplanner.core.impl.phase.AbstractPhaseFactory;
import org.optaplanner.core.impl.solver.recaller.BestSolutionRecaller;
import org.optaplanner.core.impl.solver.termination.Termination;
import org.optaplanner.core.impl.solver.thread.ChildThreadType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultPartitionedSearchPhaseFactory<Solution_>
        extends AbstractPhaseFactory<Solution_, PartitionedSearchPhaseConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPartitionedSearchPhaseFactory.class);

    public DefaultPartitionedSearchPhaseFactory(PartitionedSearchPhaseConfig phaseConfig) {
        super(phaseConfig);
    }

    @Override
    public PartitionedSearchPhase<Solution_> buildPhase(int phaseIndex, HeuristicConfigPolicy<Solution_> solverConfigPolicy,
            BestSolutionRecaller<Solution_> bestSolutionRecaller, Termination<Solution_> solverTermination) {
        HeuristicConfigPolicy<Solution_> phaseConfigPolicy = solverConfigPolicy.createPhaseConfigPolicy();
        ThreadFactory threadFactory = solverConfigPolicy.buildThreadFactory(ChildThreadType.PART_THREAD);
        Termination<Solution_> phaseTermination = buildPhaseTermination(phaseConfigPolicy, solverTermination);
        Integer resolvedActiveThreadCount = resolveActiveThreadCount(phaseConfig.getRunnablePartThreadLimit());
        List<PhaseConfig> phaseConfigList_ = phaseConfig.getPhaseConfigList();
        if (ConfigUtils.isEmptyCollection(phaseConfigList_)) {
            phaseConfigList_ = Arrays.asList(new ConstructionHeuristicPhaseConfig(), new LocalSearchPhaseConfig());
        }

        DefaultPartitionedSearchPhase.Builder<Solution_> builder = new DefaultPartitionedSearchPhase.Builder<>(phaseIndex,
                solverConfigPolicy.getLogIndentation(), phaseTermination, buildSolutionPartitioner(), threadFactory,
                resolvedActiveThreadCount, phaseConfigList_,
                phaseConfigPolicy.createChildThreadConfigPolicy(ChildThreadType.PART_THREAD));

        EnvironmentMode environmentMode = phaseConfigPolicy.getEnvironmentMode();
        if (environmentMode.isNonIntrusiveFullAsserted()) {
            builder.setAssertStepScoreFromScratch(true);
        }
        if (environmentMode.isIntrusiveFastAsserted()) {
            builder.setAssertExpectedStepScore(true);
            builder.setAssertShadowVariablesAreNotStaleAfterStep(true);
        }
        return builder.build();
    }

    private SolutionPartitioner<Solution_> buildSolutionPartitioner() {
        if (phaseConfig.getSolutionPartitionerClass() != null) {
            SolutionPartitioner<?> solutionPartitioner =
                    ConfigUtils.newInstance(phaseConfig, "solutionPartitionerClass", phaseConfig.getSolutionPartitionerClass());
            ConfigUtils.applyCustomProperties(solutionPartitioner, "solutionPartitionerClass",
                    phaseConfig.getSolutionPartitionerCustomProperties(), "solutionPartitionerCustomProperties");
            return (SolutionPartitioner<Solution_>) solutionPartitioner;
        } else {
            if (phaseConfig.getSolutionPartitionerCustomProperties() != null) {
                throw new IllegalStateException(
                        "If there is no solutionPartitionerClass (" + phaseConfig.getSolutionPartitionerClass()
                                + "), then there can be no solutionPartitionerCustomProperties ("
                                + phaseConfig.getSolutionPartitionerCustomProperties() + ") either.");
            }
            // TODO Implement generic partitioner
            throw new UnsupportedOperationException();
        }
    }

    protected Integer resolveActiveThreadCount(String runnablePartThreadLimit) {
        int availableProcessorCount = getAvailableProcessors();
        Integer resolvedActiveThreadCount;
        final boolean threadLimitNullOrAuto =
                runnablePartThreadLimit == null || runnablePartThreadLimit.equals(ACTIVE_THREAD_COUNT_AUTO);
        if (threadLimitNullOrAuto) {
            // Leave one for the Operating System and 1 for the solver thread, take the rest
            resolvedActiveThreadCount = Math.max(1, availableProcessorCount - 2);
        } else if (runnablePartThreadLimit.equals(ACTIVE_THREAD_COUNT_UNLIMITED)) {
            resolvedActiveThreadCount = null;
        } else {
            resolvedActiveThreadCount = ConfigUtils.resolvePoolSize("runnablePartThreadLimit",
                    runnablePartThreadLimit, ACTIVE_THREAD_COUNT_AUTO, ACTIVE_THREAD_COUNT_UNLIMITED);
            if (resolvedActiveThreadCount < 1) {
                throw new IllegalArgumentException("The runnablePartThreadLimit (" + runnablePartThreadLimit
                        + ") resulted in a resolvedActiveThreadCount (" + resolvedActiveThreadCount
                        + ") that is lower than 1.");
            }
            if (resolvedActiveThreadCount > availableProcessorCount) {
                LOGGER.debug("The resolvedActiveThreadCount ({}) is higher than "
                        + "the availableProcessorCount ({}), so the JVM will "
                        + "round-robin the CPU instead.", resolvedActiveThreadCount, availableProcessorCount);
            }
        }
        return resolvedActiveThreadCount;
    }

    protected int getAvailableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }
}
```

### PartitionedSearchPhaseLifecycleListener.java

```java
package org.optaplanner.core.impl.partitionedsearch.event;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.impl.partitionedsearch.scope.PartitionedSearchPhaseScope;
import org.optaplanner.core.impl.partitionedsearch.scope.PartitionedSearchStepScope;
import org.optaplanner.core.impl.solver.event.SolverLifecycleListener;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public interface PartitionedSearchPhaseLifecycleListener<Solution_> extends SolverLifecycleListener<Solution_> {

    void phaseStarted(PartitionedSearchPhaseScope<Solution_> phaseScope);

    void stepStarted(PartitionedSearchStepScope<Solution_> stepScope);

    void stepEnded(PartitionedSearchStepScope<Solution_> stepScope);

    void phaseEnded(PartitionedSearchPhaseScope<Solution_> phaseScope);

}
```

---

## Configuration Classes

### PartitionedSearchPhaseConfig.java

```java
package org.optaplanner.core.config.partitionedsearch;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.optaplanner.core.api.score.director.ScoreDirector;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import org.optaplanner.core.config.exhaustivesearch.ExhaustiveSearchPhaseConfig;
import org.optaplanner.core.config.localsearch.LocalSearchPhaseConfig;
import org.optaplanner.core.config.phase.NoChangePhaseConfig;
import org.optaplanner.core.config.phase.PhaseConfig;
import org.optaplanner.core.config.phase.custom.CustomPhaseConfig;
import org.optaplanner.core.config.util.ConfigUtils;
import org.optaplanner.core.impl.io.jaxb.adapter.JaxbCustomPropertiesAdapter;
import org.optaplanner.core.impl.partitionedsearch.partitioner.SolutionPartitioner;

@XmlType(propOrder = {
        "solutionPartitionerClass",
        "solutionPartitionerCustomProperties",
        "runnablePartThreadLimit",
        "phaseConfigList"
})
public class PartitionedSearchPhaseConfig extends PhaseConfig<PartitionedSearchPhaseConfig> {

    public static final String XML_ELEMENT_NAME = "partitionedSearch";
    public static final String ACTIVE_THREAD_COUNT_AUTO = "AUTO";
    public static final String ACTIVE_THREAD_COUNT_UNLIMITED = "UNLIMITED";

    // Warning: all fields are null (and not defaulted) because they can be inherited
    // and also because the input config file should match the output config file

    protected Class<? extends SolutionPartitioner<?>> solutionPartitionerClass = null;
    @XmlJavaTypeAdapter(JaxbCustomPropertiesAdapter.class)
    protected Map<String, String> solutionPartitionerCustomProperties = null;

    protected String runnablePartThreadLimit = null;

    @XmlElements({
            @XmlElement(name = ConstructionHeuristicPhaseConfig.XML_ELEMENT_NAME,
                    type = ConstructionHeuristicPhaseConfig.class),
            @XmlElement(name = CustomPhaseConfig.XML_ELEMENT_NAME, type = CustomPhaseConfig.class),
            @XmlElement(name = ExhaustiveSearchPhaseConfig.XML_ELEMENT_NAME, type = ExhaustiveSearchPhaseConfig.class),
            @XmlElement(name = LocalSearchPhaseConfig.XML_ELEMENT_NAME, type = LocalSearchPhaseConfig.class),
            @XmlElement(name = NoChangePhaseConfig.XML_ELEMENT_NAME, type = NoChangePhaseConfig.class),
            @XmlElement(name = PartitionedSearchPhaseConfig.XML_ELEMENT_NAME, type = PartitionedSearchPhaseConfig.class)
    })
    protected List<PhaseConfig> phaseConfigList = null;

    // ************************************************************************
    // Constructors and simple getters/setters
    // ************************************************************************

    public Class<? extends SolutionPartitioner<?>> getSolutionPartitionerClass() {
        return solutionPartitionerClass;
    }

    public void setSolutionPartitionerClass(Class<? extends SolutionPartitioner<?>> solutionPartitionerClass) {
        this.solutionPartitionerClass = solutionPartitionerClass;
    }

    public Map<String, String> getSolutionPartitionerCustomProperties() {
        return solutionPartitionerCustomProperties;
    }

    public void setSolutionPartitionerCustomProperties(Map<String, String> solutionPartitionerCustomProperties) {
        this.solutionPartitionerCustomProperties = solutionPartitionerCustomProperties;
    }

    /**
     * Similar to a thread pool size, but instead of limiting the number of {@link Thread}s,
     * it limits the number of {@link java.lang.Thread.State#RUNNABLE runnable} {@link Thread}s to avoid consuming all
     * CPU resources (which would starve UI, Servlets and REST threads).
     * <p/>
     * The number of {@link Thread}s is always equal to the number of partitions returned by
     * {@link SolutionPartitioner#splitWorkingSolution(ScoreDirector, Integer)},
     * because otherwise some partitions would never run (especially with {@link Solver#terminateEarly()} asynchronous
     * termination}).
     * If this limit (or {@link Runtime#availableProcessors()}) is lower than the number of partitions,
     * this results in a slower score calculation speed per partition {@link Solver}.
     * <p/>
     * Defaults to {@value #ACTIVE_THREAD_COUNT_AUTO} which consumes the majority
     * but not all of the CPU cores on multi-core machines, to prevent a livelock that hangs other processes
     * (such as your IDE, REST servlets threads or SSH connections) on the machine.
     * <p/>
     * Use {@value #ACTIVE_THREAD_COUNT_UNLIMITED} to give it all CPU cores.
     * This is useful if you're handling the CPU consumption on an OS level.
     *
     * @return null, a number, {@value #ACTIVE_THREAD_COUNT_AUTO} or {@value #ACTIVE_THREAD_COUNT_UNLIMITED}.
     */
    public String getRunnablePartThreadLimit() {
        return runnablePartThreadLimit;
    }

    public void setRunnablePartThreadLimit(String runnablePartThreadLimit) {
        this.runnablePartThreadLimit = runnablePartThreadLimit;
    }

    public List<PhaseConfig> getPhaseConfigList() {
        return phaseConfigList;
    }

    public void setPhaseConfigList(List<PhaseConfig> phaseConfigList) {
        this.phaseConfigList = phaseConfigList;
    }

    // ************************************************************************
    // With methods
    // ************************************************************************

    public PartitionedSearchPhaseConfig withSolutionPartitionerClass(
            Class<? extends SolutionPartitioner<?>> solutionPartitionerClass) {
        this.setSolutionPartitionerClass(solutionPartitionerClass);
        return this;
    }

    public PartitionedSearchPhaseConfig withSolutionPartitionerCustomProperties(
            Map<String, String> solutionPartitionerCustomProperties) {
        this.setSolutionPartitionerCustomProperties(solutionPartitionerCustomProperties);
        return this;
    }

    public PartitionedSearchPhaseConfig withRunnablePartThreadLimit(String runnablePartThreadLimit) {
        this.setRunnablePartThreadLimit(runnablePartThreadLimit);
        return this;
    }

    public PartitionedSearchPhaseConfig withPhaseConfigList(List<PhaseConfig> phaseConfigList) {
        this.setPhaseConfigList(phaseConfigList);
        return this;
    }

    public PartitionedSearchPhaseConfig withPhaseConfigs(PhaseConfig... phaseConfigs) {
        this.setPhaseConfigList(List.of(phaseConfigs));
        return this;
    }

    @Override
    public PartitionedSearchPhaseConfig inherit(PartitionedSearchPhaseConfig inheritedConfig) {
        super.inherit(inheritedConfig);
        solutionPartitionerClass = ConfigUtils.inheritOverwritableProperty(solutionPartitionerClass,
                inheritedConfig.getSolutionPartitionerClass());
        solutionPartitionerCustomProperties = ConfigUtils.inheritMergeableMapProperty(
                solutionPartitionerCustomProperties, inheritedConfig.getSolutionPartitionerCustomProperties());
        runnablePartThreadLimit = ConfigUtils.inheritOverwritableProperty(runnablePartThreadLimit,
                inheritedConfig.getRunnablePartThreadLimit());
        phaseConfigList = ConfigUtils.inheritMergeableListConfig(
                phaseConfigList, inheritedConfig.getPhaseConfigList());
        return this;
    }

    @Override
    public PartitionedSearchPhaseConfig copyConfig() {
        return new PartitionedSearchPhaseConfig().inherit(this);
    }

    @Override
    public void visitReferencedClasses(Consumer<Class<?>> classVisitor) {
        if (getTerminationConfig() != null) {
            getTerminationConfig().visitReferencedClasses(classVisitor);
        }
        classVisitor.accept(solutionPartitionerClass);
        if (phaseConfigList != null) {
            phaseConfigList.forEach(pc -> pc.visitReferencedClasses(classVisitor));
        }
    }

}
```

---

## Scope Classes

### PartitionedSearchPhaseScope.java

```java
package org.optaplanner.core.impl.partitionedsearch.scope;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.impl.phase.scope.AbstractPhaseScope;
import org.optaplanner.core.impl.solver.scope.SolverScope;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class PartitionedSearchPhaseScope<Solution_> extends AbstractPhaseScope<Solution_> {

    private Integer partCount;

    private PartitionedSearchStepScope<Solution_> lastCompletedStepScope;

    public PartitionedSearchPhaseScope(SolverScope<Solution_> solverScope) {
        super(solverScope);
        lastCompletedStepScope = new PartitionedSearchStepScope<>(this, -1);
    }

    public Integer getPartCount() {
        return partCount;
    }

    public void setPartCount(Integer partCount) {
        this.partCount = partCount;
    }

    @Override
    public PartitionedSearchStepScope<Solution_> getLastCompletedStepScope() {
        return lastCompletedStepScope;
    }

    public void setLastCompletedStepScope(PartitionedSearchStepScope<Solution_> lastCompletedStepScope) {
        this.lastCompletedStepScope = lastCompletedStepScope;
    }

    // ************************************************************************
    // Calculated methods
    // ************************************************************************

}
```

### PartitionedSearchStepScope.java

```java
package org.optaplanner.core.impl.partitionedsearch.scope;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.impl.phase.scope.AbstractStepScope;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class PartitionedSearchStepScope<Solution_> extends AbstractStepScope<Solution_> {

    private final PartitionedSearchPhaseScope<Solution_> phaseScope;

    private PartitionChangeMove<Solution_> step = null;
    private String stepString = null;

    public PartitionedSearchStepScope(PartitionedSearchPhaseScope<Solution_> phaseScope) {
        this(phaseScope, phaseScope.getNextStepIndex());
    }

    public PartitionedSearchStepScope(PartitionedSearchPhaseScope<Solution_> phaseScope, int stepIndex) {
        super(stepIndex);
        this.phaseScope = phaseScope;
    }

    @Override
    public PartitionedSearchPhaseScope<Solution_> getPhaseScope() {
        return phaseScope;
    }

    public PartitionChangeMove<Solution_> getStep() {
        return step;
    }

    public void setStep(PartitionChangeMove<Solution_> step) {
        this.step = step;
    }

    /**
     * @return null if logging level is too high
     */
    public String getStepString() {
        return stepString;
    }

    public void setStepString(String stepString) {
        this.stepString = stepString;
    }

    // ************************************************************************
    // Calculated methods
    // ************************************************************************

}
```

### PartitionChangeMove.java

```java
package org.optaplanner.core.impl.partitionedsearch.scope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.ProblemFactCollectionProperty;
import org.optaplanner.core.api.score.director.ScoreDirector;
import org.optaplanner.core.impl.domain.entity.descriptor.EntityDescriptor;
import org.optaplanner.core.impl.domain.solution.descriptor.SolutionDescriptor;
import org.optaplanner.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import org.optaplanner.core.impl.heuristic.move.AbstractMove;
import org.optaplanner.core.impl.score.director.InnerScoreDirector;
import org.optaplanner.core.impl.util.Pair;

/**
 * Applies a new best solution from a partition child solver into the global working solution of the parent solver.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public final class PartitionChangeMove<Solution_> extends AbstractMove<Solution_> {

    public static <Solution_> PartitionChangeMove<Solution_> createMove(InnerScoreDirector<Solution_, ?> scoreDirector,
            int partIndex) {
        SolutionDescriptor<Solution_> solutionDescriptor = scoreDirector.getSolutionDescriptor();
        Solution_ workingSolution = scoreDirector.getWorkingSolution();

        int entityCount = solutionDescriptor.getEntityCount(workingSolution);
        Map<GenuineVariableDescriptor<Solution_>, List<Pair<Object, Object>>> changeMap = new LinkedHashMap<>(
                solutionDescriptor.getEntityDescriptors().size() * 3);
        for (EntityDescriptor<Solution_> entityDescriptor : solutionDescriptor.getEntityDescriptors()) {
            for (GenuineVariableDescriptor<Solution_> variableDescriptor : entityDescriptor
                    .getDeclaredGenuineVariableDescriptors()) {
                changeMap.put(variableDescriptor, new ArrayList<>(entityCount));
            }
        }
        solutionDescriptor.visitAllEntities(workingSolution, entity -> {
            EntityDescriptor<Solution_> entityDescriptor = solutionDescriptor.findEntityDescriptorOrFail(
                    entity.getClass());
            if (entityDescriptor.isMovable(scoreDirector, entity)) {
                for (GenuineVariableDescriptor<Solution_> variableDescriptor : entityDescriptor
                        .getGenuineVariableDescriptorList()) {
                    Object value = variableDescriptor.getValue(entity);
                    changeMap.get(variableDescriptor).add(Pair.of(entity, value));
                }
            }
        });
        return new PartitionChangeMove<>(changeMap, partIndex);
    }

    private final Map<GenuineVariableDescriptor<Solution_>, List<Pair<Object, Object>>> changeMap;
    private final int partIndex;

    public PartitionChangeMove(Map<GenuineVariableDescriptor<Solution_>, List<Pair<Object, Object>>> changeMap,
            int partIndex) {
        this.changeMap = changeMap;
        this.partIndex = partIndex;
    }

    @Override
    protected void doMoveOnGenuineVariables(ScoreDirector<Solution_> scoreDirector) {
        InnerScoreDirector<Solution_, ?> innerScoreDirector = (InnerScoreDirector<Solution_, ?>) scoreDirector;
        for (Map.Entry<GenuineVariableDescriptor<Solution_>, List<Pair<Object, Object>>> entry : changeMap.entrySet()) {
            GenuineVariableDescriptor<Solution_> variableDescriptor = entry.getKey();
            for (Pair<Object, Object> pair : entry.getValue()) {
                Object entity = pair.getKey();
                Object value = pair.getValue();
                innerScoreDirector.changeVariableFacade(variableDescriptor, entity, value);
            }
        }
    }

    @Override
    public boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector) {
        return true;
    }

    @Override
    protected PartitionChangeMove<Solution_> createUndoMove(ScoreDirector<Solution_> scoreDirector) {
        throw new UnsupportedOperationException("Impossible state: undo move should not be called.");
    }

    @Override
    public PartitionChangeMove<Solution_> rebase(ScoreDirector<Solution_> destinationScoreDirector) {
        Map<GenuineVariableDescriptor<Solution_>, List<Pair<Object, Object>>> destinationChangeMap = new LinkedHashMap<>(
                changeMap.size());
        for (Map.Entry<GenuineVariableDescriptor<Solution_>, List<Pair<Object, Object>>> entry : changeMap.entrySet()) {
            GenuineVariableDescriptor<Solution_> variableDescriptor = entry.getKey();
            List<Pair<Object, Object>> originPairList = entry.getValue();
            List<Pair<Object, Object>> destinationPairList = new ArrayList<>(originPairList.size());
            for (Pair<Object, Object> pair : originPairList) {
                Object originEntity = pair.getKey();
                Object destinationEntity = destinationScoreDirector.lookUpWorkingObject(originEntity);
                if (destinationEntity == null && originEntity != null) {
                    throw new IllegalStateException("The destinationEntity (" + destinationEntity
                            + ") cannot be null if the originEntity (" + originEntity + ") is not null.");
                }
                Object originValue = pair.getValue();
                Object destinationValue = destinationScoreDirector.lookUpWorkingObject(originValue);
                if (destinationValue == null && originValue != null) {
                    throw new IllegalStateException("The destinationEntity (" + destinationEntity
                            + ")'s destinationValue (" + destinationValue
                            + ") cannot be null if the originEntity (" + originEntity
                            + ")'s originValue (" + originValue + ") is not null.\n"
                            + "Maybe add the originValue (" + originValue + ") of class (" + originValue.getClass()
                            + ") as a problem fact in the planning solution with a "
                            + ProblemFactCollectionProperty.class.getSimpleName() + " annotation.");
                }
                destinationPairList.add(Pair.of(destinationEntity, destinationValue));
            }
            destinationChangeMap.put(variableDescriptor, destinationPairList);
        }
        return new PartitionChangeMove<>(destinationChangeMap, partIndex);
    }

    @Override
    public Collection<? extends Object> getPlanningEntities() {
        throw new UnsupportedOperationException("Impossible situation: " + PartitionChangeMove.class.getSimpleName()
                + " is only used to communicate between a part thread and the solver thread, it's never used in Tabu Search.");
    }

    @Override
    public Collection<? extends Object> getPlanningValues() {
        throw new UnsupportedOperationException("Impossible situation: " + PartitionChangeMove.class.getSimpleName()
                + " is only used to communicate between a part thread and the solver thread, it's never used in Tabu Search.");
    }

    @Override
    public String toString() {
        int changeCount = changeMap.values().stream().mapToInt(List::size).sum();
        return "part-" + partIndex + " {" + changeCount + " variables changed}";
    }

}
```

---

## Queue and Events

### PartitionQueue.java

```java
package org.optaplanner.core.impl.partitionedsearch.queue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import org.optaplanner.core.impl.partitionedsearch.scope.PartitionChangeMove;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is thread-safe.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class PartitionQueue<Solution_> implements Iterable<PartitionChangeMove<Solution_>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartitionQueue.class);

    private BlockingQueue<PartitionChangedEvent<Solution_>> queue;
    private Map<Integer, PartitionChangedEvent<Solution_>> moveEventMap; // Key is partIndex

    // Only used by producers
    private final Map<Integer, AtomicLong> nextEventIndexMap;

    // Only used by consumer
    private int openPartCount;
    private long partsCalculationCount;
    private final Map<Integer, Long> processedEventIndexMap; // Key is partIndex

    public PartitionQueue(int partCount) {
        // TODO partCount * 100 is pulled from thin air
        queue = new ArrayBlockingQueue<>(partCount * 100);
        moveEventMap = new ConcurrentHashMap<>(partCount);
        Map<Integer, AtomicLong> nextEventIndexMap = new HashMap<>(partCount);
        for (int i = 0; i < partCount; i++) {
            nextEventIndexMap.put(i, new AtomicLong(0));
        }
        this.nextEventIndexMap = Collections.unmodifiableMap(nextEventIndexMap);
        openPartCount = partCount;
        partsCalculationCount = 0L;
        // HashMap because only the consumer thread uses it
        processedEventIndexMap = new HashMap<>(partCount);
        for (int i = 0; i < partCount; i++) {
            processedEventIndexMap.put(i, -1L);
        }
    }

    /**
     * This method is thread-safe.
     * The previous move(s) for this partIndex (if it hasn't been consumed yet), will be skipped during iteration.
     *
     * @param partIndex {@code 0 <= partIndex < partCount}
     * @param move never null
     * @see BlockingQueue#add(Object)
     */
    public void addMove(int partIndex, PartitionChangeMove<Solution_> move) {
        long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
        PartitionChangedEvent<Solution_> event = new PartitionChangedEvent<>(
                partIndex, eventIndex, move);
        moveEventMap.put(event.getPartIndex(), event);
        queue.add(event);
    }

    /**
     * This method is thread-safe.
     * The previous move for this partIndex (that haven't been consumed yet), will still be returned during iteration.
     *
     * @param partIndex {@code 0 <= partIndex < partCount}
     * @param partCalculationCount at least 0
     * @see BlockingQueue#add(Object)
     */
    public void addFinish(int partIndex, long partCalculationCount) {
        long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
        PartitionChangedEvent<Solution_> event = new PartitionChangedEvent<>(
                partIndex, eventIndex, partCalculationCount);
        queue.add(event);
    }

    /**
     * This method is thread-safe.
     * The previous move for this partIndex (if it hasn't been consumed yet), will still be returned during iteration
     * before the iteration throws an exception.
     *
     * @param partIndex {@code 0 <= partIndex < partCount}
     * @param throwable never null
     * @see BlockingQueue#add(Object)
     */
    public void addExceptionThrown(int partIndex, Throwable throwable) {
        long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
        PartitionChangedEvent<Solution_> event = new PartitionChangedEvent<>(
                partIndex, eventIndex, throwable);
        queue.add(event);
    }

    @Override
    public Iterator<PartitionChangeMove<Solution_>> iterator() {
        // TODO Currently doesn't support being called twice on the same instance
        return new PartitionQueueIterator();
    }

    private class PartitionQueueIterator extends UpcomingSelectionIterator<PartitionChangeMove<Solution_>> {

        @Override
        protected PartitionChangeMove<Solution_> createUpcomingSelection() {
            while (true) {
                PartitionChangedEvent<Solution_> triggerEvent;
                try {
                    triggerEvent = queue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Solver thread was interrupted in Partitioned Search.", e);
                }
                switch (triggerEvent.getType()) {
                    case MOVE:
                        int partIndex = triggerEvent.getPartIndex();
                        long processedEventIndex = processedEventIndexMap.get(partIndex);
                        if (triggerEvent.getEventIndex() <= processedEventIndex) {
                            // Skip this one because it or a better version was already processed
                            LOGGER.trace("    Skipped event of partIndex ({}).", partIndex);
                            continue;
                        }
                        PartitionChangedEvent<Solution_> latestMoveEvent = moveEventMap.get(partIndex);
                        processedEventIndexMap.put(partIndex, latestMoveEvent.getEventIndex());
                        return latestMoveEvent.getMove();
                    case FINISHED:
                        openPartCount--;
                        partsCalculationCount += triggerEvent.getPartCalculationCount();
                        if (openPartCount <= 0) {
                            return noUpcomingSelection();
                        } else {
                            continue;
                        }
                    case EXCEPTION_THROWN:
                        throw new IllegalStateException("The partition child thread with partIndex ("
                                + triggerEvent.getPartIndex() + ") has thrown an exception."
                                + " Relayed here in the parent thread.",
                                triggerEvent.getThrowable());
                    default:
                        throw new IllegalStateException("The partitionChangedEventType ("
                                + triggerEvent.getType() + ") is not implemented.");
                }
            }
        }

    }

    public long getPartsCalculationCount() {
        return partsCalculationCount;
    }

}
```

### PartitionChangedEvent.java

```java
package org.optaplanner.core.impl.partitionedsearch.queue;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.impl.partitionedsearch.scope.PartitionChangeMove;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public final class PartitionChangedEvent<Solution_> {

    private final int partIndex;
    private final long eventIndex;
    private final PartitionChangedEventType type;
    private final PartitionChangeMove<Solution_> move;
    private final Long partCalculationCount;
    private final Throwable throwable;

    public PartitionChangedEvent(int partIndex, long eventIndex, long partCalculationCount) {
        this.partIndex = partIndex;
        this.eventIndex = eventIndex;
        this.type = PartitionChangedEventType.FINISHED;
        move = null;
        this.partCalculationCount = partCalculationCount;
        throwable = null;
    }

    public PartitionChangedEvent(int partIndex, long eventIndex, PartitionChangeMove<Solution_> move) {
        this.partIndex = partIndex;
        this.eventIndex = eventIndex;
        type = PartitionChangedEventType.MOVE;
        this.move = move;
        partCalculationCount = null;
        throwable = null;
    }

    public PartitionChangedEvent(int partIndex, long eventIndex, Throwable throwable) {
        this.partIndex = partIndex;
        this.eventIndex = eventIndex;
        type = PartitionChangedEventType.EXCEPTION_THROWN;
        move = null;
        partCalculationCount = null;
        this.throwable = throwable;
    }

    public int getPartIndex() {
        return partIndex;
    }

    public Long getEventIndex() {
        return eventIndex;
    }

    public PartitionChangedEventType getType() {
        return type;
    }

    public PartitionChangeMove<Solution_> getMove() {
        return move;
    }

    public Long getPartCalculationCount() {
        return partCalculationCount;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public enum PartitionChangedEventType {
        MOVE,
        FINISHED,
        EXCEPTION_THROWN;
    }

}
```

---

## Partitioner Interface

### SolutionPartitioner.java

```java
package org.optaplanner.core.impl.partitionedsearch.partitioner;

import java.util.List;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.cloner.SolutionCloner;
import org.optaplanner.core.api.score.director.ScoreDirector;

/**
 * Splits one {@link PlanningSolution solution} into multiple partitions.
 * The partitions are solved and merged based on {@link PlanningSolution#lookUpStrategyType()}.
 * <p>
 * To add custom properties, configure custom properties and add public setters for them.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public interface SolutionPartitioner<Solution_> {

    /**
     * Returns a list of partition cloned {@link PlanningSolution solutions}
     * for which each {@link PlanningEntity planning entity}
     * is partition cloned into exactly 1 of those partitions.
     * Problem facts can be in multiple partitions (with or without cloning).
     * <p>
     * Any class that is {@link SolutionCloner solution cloned} must also be partitioned cloned.
     * A class can be partition cloned without being solution cloned.
     *
     * @param scoreDirector never null, {@link ScoreDirector}
     *        which has {@link ScoreDirector#getWorkingSolution()} that needs to be split up
     * @param runnablePartThreadLimit null if unlimited, never negative
     * @return never null, {@link List#size()} of at least 1.
     */
    List<Solution_> splitWorkingSolution(ScoreDirector<Solution_> scoreDirector, Integer runnablePartThreadLimit);

}
```

---

## Example Partitioner

### TestdataSolutionPartitioner.java

```java
package org.optaplanner.core.impl.partitionedsearch;

import java.util.ArrayList;
import java.util.List;

import org.optaplanner.core.api.score.director.ScoreDirector;
import org.optaplanner.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import org.optaplanner.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import org.optaplanner.core.impl.testdata.domain.TestdataEntity;
import org.optaplanner.core.impl.testdata.domain.TestdataSolution;

public class TestdataSolutionPartitioner implements SolutionPartitioner<TestdataSolution> {

    /**
     * {@link PartitionedSearchPhaseConfig#getSolutionPartitionerCustomProperties()} Custom property}.
     */
    private int partSize = 1;

    public void setPartSize(int partSize) {
        this.partSize = partSize;
    }

    @Override
    public List<TestdataSolution> splitWorkingSolution(ScoreDirector<TestdataSolution> scoreDirector,
            Integer runnablePartThreadLimit) {
        TestdataSolution workingSolution = scoreDirector.getWorkingSolution();
        List<TestdataEntity> allEntities = workingSolution.getEntityList();
        if (allEntities.size() % partSize > 0) {
            throw new IllegalStateException("This partitioner can only make equally sized partitions."
                    + " This is impossible because the number of allEntities (" + allEntities.size()
                    + ") is not divisible by partSize (" + partSize + ").");
        }
        List<TestdataSolution> partitions = new ArrayList<>();
        for (int i = 0; i < allEntities.size() / partSize; i++) {
            List<TestdataEntity> partitionEntities = new ArrayList<>(allEntities.subList(i * partSize, (i + 1) * partSize));
            TestdataSolution partition = new TestdataSolution();
            partition.setEntityList(partitionEntities);
            partition.setValueList(workingSolution.getValueList());
            partitions.add(partition);
        }
        return partitions;
    }

}
```

---

## User Configuration Examples

### XML Configuration

```xml
<solver>
  <partitionedSearch>
    <solutionPartitionerClass>com.example.MyPartitioner</solutionPartitionerClass>
    <solutionPartitionerCustomProperties>
      <property name="partCount" value="4"/>
      <property name="minimumEntityCount" value="100"/>
    </solutionPartitionerCustomProperties>
    <runnablePartThreadLimit>AUTO</runnablePartThreadLimit>
    
    <!-- Phases to run on each partition -->
    <constructionHeuristic/>
    <localSearch>
      <termination>
        <stepCountLimit>1000</stepCountLimit>
      </termination>
    </localSearch>
  </partitionedSearch>
</solver>
```

### Java API Configuration

```java
SolverConfig solverConfig = SolverConfig.create()
    .withPhaseConfigList(
        new PartitionedSearchPhaseConfig()
            .withSolutionPartitionerClass(MyPartitioner.class)
            .withSolutionPartitionerCustomProperties(Map.of(
                "partCount", "4",
                "minimumEntityCount", "100"
            ))
            .withRunnablePartThreadLimit("AUTO")
            .withPhaseConfigs(
                new ConstructionHeuristicPhaseConfig(),
                new LocalSearchPhaseConfig()
                    .withTerminationConfig(
                        new TerminationConfig()
                            .withStepCountLimit(1000)
                    )
            )
    );
```

### Custom SolutionPartitioner Implementation

```java
package com.example;

import java.util.ArrayList;
import java.util.List;
import org.optaplanner.core.api.score.director.ScoreDirector;
import org.optaplanner.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import com.example.domain.MyEntity;
import com.example.domain.MySolution;

public class MyPartitioner implements SolutionPartitioner<MySolution> {

    // Configurable properties (set via custom properties)
    private int partCount = 4;
    private int minimumEntityCount = 50;

    // Setters for custom properties
    public void setPartCount(int partCount) {
        this.partCount = partCount;
    }

    public void setMinimumEntityCount(int minimumEntityCount) {
        this.minimumEntityCount = minimumEntityCount;
    }

    @Override
    public List<MySolution> splitWorkingSolution(
            ScoreDirector<MySolution> scoreDirector,
            Integer runnablePartThreadLimit) {
        
        MySolution originalSolution = scoreDirector.getWorkingSolution();
        List<MyEntity> allEntities = originalSolution.getEntityList();
        
        // Adjust part count based on entity count
        int actualPartCount = partCount;
        if (allEntities.size() / actualPartCount < minimumEntityCount) {
            actualPartCount = Math.max(1, allEntities.size() / minimumEntityCount);
        }
        
        List<MySolution> partitions = new ArrayList<>(actualPartCount);
        
        // Create partition solutions
        for (int i = 0; i < actualPartCount; i++) {
            MySolution partition = new MySolution();
            // Clone problem facts (shared across partitions)
            partition.setValueList(new ArrayList<>(originalSolution.getValueList()));
            // Entities will be added per partition
            partition.setEntityList(new ArrayList<>());
            partitions.add(partition);
        }
        
        // Distribute entities across partitions
        int partIndex = 0;
        for (MyEntity entity : allEntities) {
            MySolution partition = partitions.get(partIndex);
            
            // Clone the entity for this partition
            MyEntity partitionEntity = new MyEntity(
                entity.getId(),
                entity.getSomeProperty()
            );
            
            // Preserve current assignment if exists
            if (entity.getValue() != null) {
                // Find the corresponding value in this partition
                MyValue partitionValue = findValueInPartition(
                    partition, entity.getValue().getId()
                );
                partitionEntity.setValue(partitionValue);
            }
            
            partition.getEntityList().add(partitionEntity);
            partIndex = (partIndex + 1) % actualPartCount;
        }
        
        return partitions;
    }

    private MyValue findValueInPartition(MySolution partition, Long valueId) {
        return partition.getValueList().stream()
            .filter(v -> v.getId().equals(valueId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Value not found in partition: " + valueId
            ));
    }
}
```

### Partitioning Strategies

#### Round-Robin Partitioning

```java
int partIndex = 0;
for (MyEntity entity : allEntities) {
    partitions.get(partIndex).getEntityList().add(cloneEntity(entity));
    partIndex = (partIndex + 1) % partCount;
}
```

#### Geographic/Region-Based Partitioning

```java
Map<String, List<MyEntity>> entitiesByRegion = allEntities.stream()
    .collect(Collectors.groupingBy(MyEntity::getRegion));

int partIndex = 0;
for (List<MyEntity> regionEntities : entitiesByRegion.values()) {
    MySolution partition = partitions.get(partIndex);
    for (MyEntity entity : regionEntities) {
        partition.getEntityList().add(cloneEntity(entity));
    }
    partIndex = (partIndex + 1) % partCount;
}
```

#### Range-Based Partitioning

```java
int entitiesPerPartition = allEntities.size() / partCount;
for (int i = 0; i < partCount; i++) {
    int start = i * entitiesPerPartition;
    int end = (i == partCount - 1) ? allEntities.size() : (i + 1) * entitiesPerPartition;
    List<MyEntity> partitionEntities = allEntities.subList(start, end);
    partitions.get(i).getEntityList().addAll(
        partitionEntities.stream()
            .map(this::cloneEntity)
            .collect(Collectors.toList())
    );
}
```

### Multi-Phase Solver Configuration

```xml
<solver>
  <!-- Phase 1: Construction Heuristic on full problem -->
  <constructionHeuristic>
    <termination>
      <stepCountLimit>10000</stepCountLimit>
    </termination>
  </constructionHeuristic>
  
  <!-- Phase 2: Partitioned Search for refinement -->
  <partitionedSearch>
    <solutionPartitionerClass>com.example.MyPartitioner</solutionPartitionerClass>
    <runnablePartThreadLimit>AUTO</runnablePartThreadLimit>
    <localSearch>
      <termination>
        <stepCountLimit>5000</stepCountLimit>
      </termination>
    </localSearch>
  </partitionedSearch>
  
  <!-- Phase 3: Final Local Search on merged solution -->
  <localSearch>
    <termination>
      <timeSpentLimit>30s</timeSpentLimit>
    </termination>
  </localSearch>
</solver>
```

### Solver Event Listeners

```java
Solver<MySolution> solver = solverFactory.buildSolver();

solver.addEventListener(event -> {
    if (event instanceof BestSolutionChangedEvent) {
        BestSolutionChangedEvent<MySolution> bestEvent = 
            (BestSolutionChangedEvent<MySolution>) event;
        MySolution newBestSolution = bestEvent.getNewBestSolution();
        System.out.println("New best score: " + newBestSolution.getScore());
    }
});
```

### Phase Lifecycle Listeners

```java
((DefaultSolver<MySolution>) solver).addPhaseLifecycleListener(
    new PhaseLifecycleListenerAdapter<MySolution>() {
        @Override
        public void phaseStarted(AbstractPhaseScope<MySolution> phaseScope) {
            if (phaseScope instanceof PartitionedSearchPhaseScope) {
                PartitionedSearchPhaseScope<MySolution> psScope = 
                    (PartitionedSearchPhaseScope<MySolution>) phaseScope;
                System.out.println("Partitioned search started with " + 
                    psScope.getPartCount() + " partitions");
            }
        }
        
        @Override
        public void phaseEnded(AbstractPhaseScope<MySolution> phaseScope) {
            if (phaseScope instanceof PartitionedSearchPhaseScope) {
                System.out.println("Partitioned search ended");
            }
        }
    }
);
```

### Problem Fact Changes

```java
Solver<MySolution> solver = solverFactory.buildSolver();

// Start solving in background
ExecutorService executor = Executors.newSingleThreadExecutor();
Future<MySolution> future = executor.submit(() -> solver.solve(problem));

// Add problem fact change after solving starts
solver.addProblemChange((workingSolution, scoreDirector) -> {
    MyValue newValue = new MyValue("new-value");
    workingSolution.getValueList().add(newValue);
    scoreDirector.beforeProblemFactAdded(newValue);
});

// Get final solution
MySolution bestSolution = future.get();
```

### Testing and Validation

```java
@Test
void testPartitioner() {
    MySolution solution = createTestSolution(1000);
    ScoreDirector<MySolution> scoreDirector = 
        scoreDirectorFactory.buildScoreDirector();
    scoreDirector.setWorkingSolution(solution);
    
    MyPartitioner partitioner = new MyPartitioner();
    partitioner.setPartCount(4);
    
    List<MySolution> partitions = partitioner.splitWorkingSolution(
        scoreDirector, null
    );
    
    // Validate partition count
    assertThat(partitions).hasSize(4);
    
    // Validate all entities are distributed
    int totalEntities = partitions.stream()
        .mapToInt(p -> p.getEntityList().size())
        .sum();
    assertThat(totalEntities).isEqualTo(solution.getEntityList().size());
    
    // Validate no entity is in multiple partitions
    Set<Long> entityIds = partitions.stream()
        .flatMap(p -> p.getEntityList().stream())
        .map(MyEntity::getId)
        .collect(Collectors.toSet());
    assertThat(entityIds).hasSize(totalEntities);
}
```

### Debug Logging Properties

```properties
# Log partitioned search phase details
logging.level.org.optaplanner.core.impl.partitionedsearch=DEBUG

# Log partition queue events
logging.level.org.optaplanner.core.impl.partitionedsearch.queue=TRACE

# Log partition solver lifecycle
logging.level.org.optaplanner.core.impl.solver.AbstractSolver=DEBUG
```

### Performance Monitoring

```java
solver.addEventListener(event -> {
    if (event instanceof BestSolutionChangedEvent) {
        BestSolutionChangedEvent<?> bestEvent = 
            (BestSolutionChangedEvent<?>) event;
        logger.info("New best score: {}, time: {}ms",
            bestEvent.getNewBestSolution().getScore(),
            bestEvent.getTimeMillisSpent());
    }
});

// After solving
logger.info("Total score calculation count: {}", 
    solver.getScoreCalculationCount());
logger.info("Solving time: {}ms", 
    solver.getSolvingDuration().toMillis());
```
