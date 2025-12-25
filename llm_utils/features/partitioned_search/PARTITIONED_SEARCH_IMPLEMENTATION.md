# Partitioned Search Feature Implementation Guide

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Core Components](#core-components)
4. [Supporting Infrastructure](#supporting-infrastructure)
5. [Implementation Steps](#implementation-steps)
6. [Configuration](#configuration)
7. [Custom Partitioner Implementation](#custom-partitioner-implementation)
8. [Testing](#testing)
9. [Key Design Decisions](#key-design-decisions)
10. [File Structure](#file-structure)

---

## Overview

Partitioned Search is a parallel optimization strategy in OptaPlanner that splits a planning problem into multiple independent partitions, solves them concurrently using separate solver instances, and merges the results back into a single solution.

### Key Concepts

- **Partitioning**: The problem is divided into N independent sub-problems
- **Parallel Solving**: Each partition is solved by a separate thread/solver
- **Merging**: Results from partitions are applied to the main solution incrementally
- **Thread Pool Management**: Controls CPU usage to prevent resource starvation

### Benefits

- **Scalability**: Utilizes multiple CPU cores for large problems
- **Speedup**: Can significantly reduce solving time for partitionable problems
- **Flexibility**: Allows custom partitioning strategies per problem domain

### Limitations

- Requires problems that can be meaningfully partitioned
- Overhead of partitioning and merging may outweigh benefits for small problems
- Not all constraints are partition-friendly (cross-partition constraints)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Main Solver Thread                          │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         PartitionedSearchPhase                            │  │
│  │  ┌────────────────────────────────────────────────────┐  │  │
│  │  │  SolutionPartitioner.splitWorkingSolution()        │  │  │
│  │  └────────────────────────────────────────────────────┘  │  │
│  │           ↓                                              │  │
│  │  ┌────────────────────────────────────────────────────┐  │  │
│  │  │  PartitionQueue (BlockingQueue)                     │  │  │
│  │  │  ┌──────┬──────┬──────┬──────┐                     │  │  │
│  │  │  │ Part0│ Part1│ Part2│ ...  │ ← Events            │  │  │
│  │  │  └──────┴──────┴──────┴──────┘                     │  │  │
│  │  └────────────────────────────────────────────────────┘  │  │
│  │           ↓                                              │  │
│  │  ┌────────────────────────────────────────────────────┐  │  │
│  │  │  Iterates over PartitionChangeMoves                │  │  │
│  │  │  Applies moves to main solution                     │  │  │
│  │  └────────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ Partition 0  │    │ Partition 1  │    │ Partition 2  │
│   Thread     │    │   Thread     │    │   Thread     │
│ ┌──────────┐ │    │ ┌──────────┐ │    │ ┌──────────┐ │
│ │Partition │ │    │ │Partition │ │    │ │Partition │ │
│ │  Solver  │ │    │ │  Solver  │ │    │ │  Solver  │ │
│ │  (CH+LS) │ │    │ │  (CH+LS) │ │    │ │  (CH+LS) │ │
│ └──────────┘ │    │ └──────────┘ │    │ └──────────┘ │
│      ↓       │    │      ↓       │    │      ↓       │
│ Best Sol    │    │ Best Sol     │    │ Best Sol     │
│ Partition   │    │ Partition     │    │ Partition     │
│ ChangeMove  │    │ ChangeMove    │    │ ChangeMove    │
└──────────────┘    └──────────────┘    └──────────────┘
```

### Thread Communication Flow

1. **Main Thread**: Partitions solution, creates thread pool
2. **Partition Threads**: Solve their assigned partition independently
3. **Event Queue**: Partition threads emit events (new best solution, finished, exception)
4. **Main Thread**: Consumes events, applies `PartitionChangeMove` to main solution
5. **Termination**: All partitions finish or parent solver terminates

---

## Core Components

### 1. Configuration Layer

#### [`PartitionedSearchPhaseConfig`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/config/partitionedsearch/PartitionedSearchPhaseConfig.java)

```java
@XmlType(propOrder = {
    "solutionPartitionerClass",
    "solutionPartitionerCustomProperties",
    "runnablePartThreadLimit",
    "phaseConfigList"
})
public class PartitionedSearchPhaseConfig extends PhaseConfig<PartitionedSearchPhaseConfig> {
    
    // Custom partitioner implementation
    protected Class<? extends SolutionPartitioner<?>> solutionPartitionerClass;
    protected Map<String, String> solutionPartitionerCustomProperties;
    
    // Thread management
    protected String runnablePartThreadLimit; // "AUTO", "UNLIMITED", or number
    
    // Nested phases for each partition
    protected List<PhaseConfig> phaseConfigList;
}
```

**Key Properties:**
- `solutionPartitionerClass`: Custom implementation for splitting solutions
- `runnablePartThreadLimit`: Controls CPU usage (default: "AUTO")
- `phaseConfigList`: Phases to run on each partition (default: CH + LS)

### 2. Factory Layer

#### [`DefaultPartitionedSearchPhaseFactory`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/DefaultPartitionedSearchPhaseFactory.java)

```java
public class DefaultPartitionedSearchPhaseFactory<Solution_>
        extends AbstractPhaseFactory<Solution_, PartitionedSearchPhaseConfig> {
    
    @Override
    public PartitionedSearchPhase<Solution_> buildPhase(...) {
        // 1. Create thread factory for partition threads
        ThreadFactory threadFactory = solverConfigPolicy.buildThreadFactory(
            ChildThreadType.PART_THREAD);
        
        // 2. Resolve thread limit
        Integer resolvedActiveThreadCount = resolveActiveThreadCount(
            phaseConfig.getRunnablePartThreadLimit());
        
        // 3. Build solution partitioner
        SolutionPartitioner<Solution_> partitioner = buildSolutionPartitioner();
        
        // 4. Build nested phases (default: CH + LS)
        List<PhaseConfig> phaseConfigList = phaseConfig.getPhaseConfigList();
        if (ConfigUtils.isEmptyCollection(phaseConfigList)) {
            phaseConfigList = Arrays.asList(
                new ConstructionHeuristicPhaseConfig(),
                new LocalSearchPhaseConfig());
        }
        
        // 5. Create and return phase
        return new DefaultPartitionedSearchPhase.Builder<>(...)
            .build();
    }
}
```

**Thread Resolution Logic:**
```java
protected Integer resolveActiveThreadCount(String runnablePartThreadLimit) {
    int availableProcessorCount = Runtime.getRuntime().availableProcessors();
    
    if (runnablePartThreadLimit == null || 
        runnablePartThreadLimit.equals(ACTIVE_THREAD_COUNT_AUTO)) {
        // Leave 2 cores for OS and main solver thread
        return Math.max(1, availableProcessorCount - 2);
    } else if (runnablePartThreadLimit.equals(ACTIVE_THREAD_COUNT_UNLIMITED)) {
        return null; // No limit
    } else {
        return ConfigUtils.resolvePoolSize(...);
    }
}
```

### 3. Phase Implementation

#### [`DefaultPartitionedSearchPhase`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/DefaultPartitionedSearchPhase.java)

```java
public class DefaultPartitionedSearchPhase<Solution_> extends AbstractPhase<Solution_> {
    
    @Override
    public void solve(SolverScope<Solution_> solverScope) {
        // 1. Create phase scope
        PartitionedSearchPhaseScope<Solution_> phaseScope = 
            new PartitionedSearchPhaseScope<>(solverScope);
        
        // 2. Split solution into partitions
        List<Solution_> partList = solutionPartitioner.splitWorkingSolution(
            solverScope.getScoreDirector(), runnablePartThreadLimit);
        int partCount = partList.size();
        phaseScope.setPartCount(partCount);
        
        // 3. Create thread pool and synchronization primitives
        ExecutorService executor = createThreadPoolExecutor(partCount);
        ChildThreadPlumbingTermination<Solution_> childThreadPlumbingTermination =
            new ChildThreadPlumbingTermination<>();
        PartitionQueue<Solution_> partitionQueue = new PartitionQueue<>(partCount);
        Semaphore runnablePartThreadSemaphore = runnablePartThreadLimit == null ? null
            : new Semaphore(runnablePartThreadLimit, true);
        
        try {
            // 4. Submit partition solvers to thread pool
            for (ListIterator<Solution_> it = partList.listIterator(); it.hasNext();) {
                int partIndex = it.nextIndex();
                Solution_ part = it.next();
                
                PartitionSolver<Solution_> partitionSolver = buildPartitionSolver(
                    childThreadPlumbingTermination, runnablePartThreadSemaphore, solverScope);
                
                // Add event listener for best solution changes
                partitionSolver.addEventListener(event -> {
                    InnerScoreDirector<Solution_, ?> childScoreDirector =
                        partitionSolver.solverScope.getScoreDirector();
                    PartitionChangeMove<Solution_> move = PartitionChangeMove.createMove(
                        childScoreDirector, partIndex);
                    InnerScoreDirector<Solution_, ?> parentScoreDirector = 
                        solverScope.getScoreDirector();
                    move = move.rebase(parentScoreDirector);
                    partitionQueue.addMove(partIndex, move);
                });
                
                // Submit partition solver to thread pool
                executor.submit(() -> {
                    try {
                        partitionSolver.solve(part);
                        long partCalculationCount = partitionSolver.getScoreCalculationCount();
                        partitionQueue.addFinish(partIndex, partCalculationCount);
                    } catch (Throwable throwable) {
                        partitionQueue.addExceptionThrown(partIndex, throwable);
                    }
                });
            }
            
            // 5. Consume events and apply to main solution
            for (PartitionChangeMove<Solution_> step : partitionQueue) {
                PartitionedSearchStepScope<Solution_> stepScope = 
                    new PartitionedSearchStepScope<>(phaseScope);
                stepStarted(stepScope);
                stepScope.setStep(step);
                doStep(stepScope);
                stepEnded(stepScope);
                phaseScope.setLastCompletedStepScope(stepScope);
            }
            
        } finally {
            childThreadPlumbingTermination.terminateChildren();
            ThreadUtils.shutdownAwaitOrKill(executor, logIndentation, "Partitioned Search");
        }
    }
}
```

### 4. Partitioner Interface

#### [`SolutionPartitioner`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/partitioner/SolutionPartitioner.java)

```java
public interface SolutionPartitioner<Solution_> {
    
    /**
     * Splits a solution into multiple partitions.
     * Each planning entity must be in exactly one partition.
     * Problem facts can be in multiple partitions.
     */
    List<Solution_> splitWorkingSolution(
        ScoreDirector<Solution_> scoreDirector, 
        Integer runnablePartThreadLimit);
}
```

### 5. Partition Queue

#### [`PartitionQueue`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionQueue.java)

Thread-safe queue for communication between partition threads and main solver thread.

```java
public class PartitionQueue<Solution_> 
        implements Iterable<PartitionChangeMove<Solution_>> {
    
    // Blocking queue for events
    private BlockingQueue<PartitionChangedEvent<Solution_>> queue;
    
    // Map for latest move per partition
    private Map<Integer, PartitionChangedEvent<Solution_>> moveEventMap;
    
    // Producer-side: next event index for each partition
    private final Map<Integer, AtomicLong> nextEventIndexMap;
    
    // Consumer-side: tracking processed events
    private int openPartCount;
    private final Map<Integer, Long> processedEventIndexMap;
    
    public void addMove(int partIndex, PartitionChangeMove<Solution_> move) {
        long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
        PartitionChangedEvent<Solution_> event = new PartitionChangedEvent<>(
            partIndex, eventIndex, move);
        moveEventMap.put(event.getPartIndex(), event);
        queue.add(event);
    }
    
    public void addFinish(int partIndex, long partCalculationCount) {
        long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
        PartitionChangedEvent<Solution_> event = new PartitionChangedEvent<>(
            partIndex, eventIndex, partCalculationCount);
        queue.add(event);
    }
    
    public void addExceptionThrown(int partIndex, Throwable throwable) {
        long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
        PartitionChangedEvent<Solution_> event = new PartitionChangedEvent<>(
            partIndex, eventIndex, throwable);
        queue.add(event);
    }
}
```

**Event Types:**
- `MOVE`: New best solution found in a partition
- `FINISHED`: Partition solver completed
- `EXCEPTION_THROWN`: Partition solver failed

### 6. Partition Change Move

#### [`PartitionChangeMove`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionChangeMove.java)

Represents changes from a partition to be applied to the main solution.

```java
public final class PartitionChangeMove<Solution_> extends AbstractMove<Solution_> {
    
    private final Map<GenuineVariableDescriptor<Solution_>, 
                      List<Pair<Object, Object>>> changeMap;
    private final int partIndex;
    
    public static <Solution_> PartitionChangeMove<Solution_> createMove(
            InnerScoreDirector<Solution_, ?> scoreDirector, int partIndex) {
        SolutionDescriptor<Solution_> solutionDescriptor = 
            scoreDirector.getSolutionDescriptor();
        Solution_ workingSolution = scoreDirector.getWorkingSolution();
        
        Map<GenuineVariableDescriptor<Solution_>, 
            List<Pair<Object, Object>>> changeMap = new LinkedHashMap<>();
        
        // Collect all variable changes from partition
        for (EntityDescriptor<Solution_> entityDescriptor : 
                solutionDescriptor.getEntityDescriptors()) {
            for (GenuineVariableDescriptor<Solution_> variableDescriptor : 
                    entityDescriptor.getDeclaredGenuineVariableDescriptors()) {
                changeMap.put(variableDescriptor, new ArrayList<>());
            }
        }
        
        solutionDescriptor.visitAllEntities(workingSolution, entity -> {
            EntityDescriptor<Solution_> entityDescriptor = 
                solutionDescriptor.findEntityDescriptorOrFail(entity.getClass());
            if (entityDescriptor.isMovable(scoreDirector, entity)) {
                for (GenuineVariableDescriptor<Solution_> variableDescriptor : 
                        entityDescriptor.getGenuineVariableDescriptorList()) {
                    Object value = variableDescriptor.getValue(entity);
                    changeMap.get(variableDescriptor).add(Pair.of(entity, value));
                }
            }
        });
        
        return new PartitionChangeMove<>(changeMap, partIndex);
    }
    
    @Override
    protected void doMoveOnGenuineVariables(ScoreDirector<Solution_> scoreDirector) {
        InnerScoreDirector<Solution_, ?> innerScoreDirector = 
            (InnerScoreDirector<Solution_, ?>) scoreDirector;
        for (Map.Entry<GenuineVariableDescriptor<Solution_>, 
                      List<Pair<Object, Object>>> entry : changeMap.entrySet()) {
            GenuineVariableDescriptor<Solution_> variableDescriptor = entry.getKey();
            for (Pair<Object, Object> pair : entry.getValue()) {
                Object entity = pair.getKey();
                Object value = pair.getValue();
                innerScoreDirector.changeVariableFacade(variableDescriptor, entity, value);
            }
        }
    }
    
    @Override
    public PartitionChangeMove<Solution_> rebase(
            ScoreDirector<Solution_> destinationScoreDirector) {
        // Rebase entities and values from partition to main solution
        Map<GenuineVariableDescriptor<Solution_>, 
            List<Pair<Object, Object>>> destinationChangeMap = new LinkedHashMap<>();
        
        for (Map.Entry<GenuineVariableDescriptor<Solution_>, 
                      List<Pair<Object, Object>>> entry : changeMap.entrySet()) {
            GenuineVariableDescriptor<Solution_> variableDescriptor = entry.getKey();
            List<Pair<Object, Object>> originPairList = entry.getValue();
            List<Pair<Object, Object>> destinationPairList = new ArrayList<>();
            
            for (Pair<Object, Object> pair : originPairList) {
                Object originEntity = pair.getKey();
                Object destinationEntity = destinationScoreDirector.lookUpWorkingObject(originEntity);
                
                Object originValue = pair.getValue();
                Object destinationValue = destinationScoreDirector.lookUpWorkingObject(originValue);
                
                destinationPairList.add(Pair.of(destinationEntity, destinationValue));
            }
            destinationChangeMap.put(variableDescriptor, destinationPairList);
        }
        return new PartitionChangeMove<>(destinationChangeMap, partIndex);
    }
}
```

### 7. Partition Solver

#### [`PartitionSolver`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/PartitionSolver.java)

Simplified solver for solving a single partition.

```java
public class PartitionSolver<Solution_> extends AbstractSolver<Solution_> {
    
    protected final SolverScope<Solution_> solverScope;
    
    public PartitionSolver(BestSolutionRecaller<Solution_> bestSolutionRecaller,
                          Termination<Solution_> termination,
                          List<Phase<Solution_>> phaseList,
                          SolverScope<Solution_> solverScope) {
        super(bestSolutionRecaller, termination, phaseList);
        this.solverScope = solverScope;
    }
    
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
    
    public long getScoreCalculationCount() {
        return solverScope.getScoreCalculationCount();
    }
}
```

---

## Supporting Infrastructure

### AbstractPhase Base Class

The [`AbstractPhase`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/phase/AbstractPhase.java) class provides foundation for all phase implementations, including PartitionedSearchPhase.

```java
public abstract class AbstractPhase<Solution_> implements Phase<Solution_> {
    
    protected final int phaseIndex;
    protected final String logIndentation;
    protected final Termination<Solution_> phaseTermination;
    protected final boolean assertStepScoreFromScratch;
    protected final boolean assertExpectedStepScore;
    protected final boolean assertShadowVariablesAreNotStaleAfterStep;
    
    protected PhaseLifecycleSupport<Solution_> phaseLifecycleSupport = new PhaseLifecycleSupport<>();
    protected AbstractSolver<Solution_> solver;
    
    // Lifecycle methods called by DefaultSolver
    public void solvingStarted(SolverScope<Solution_> solverScope) { ... }
    public void solvingEnded(SolverScope<Solution_> solverScope) { ... }
    public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) { ... }
    public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) { ... }
    public void stepStarted(AbstractStepScope<Solution_> stepScope) { ... }
    public void stepEnded(AbstractStepScope<Solution_> stepScope) { ... }
    
    // Abstract method for subclasses
    public abstract String getPhaseTypeString();
    
    // Builder pattern for phase construction
    protected abstract static class Builder<Solution_> { ... }
}
```

**Key Responsibilities:**
- Manages phase lifecycle events
- Handles termination logic
- Provides assertion support for debugging
- Coordinates with parent solver

### ChildThreadType Enum

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/solver/thread/ChildThreadType.java`

```java
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

This enum identifies the type of child thread for proper thread management and termination handling.

### ChildThreadPlumbingTermination

Special termination class that coordinates termination between parent and child threads.

```java
public class ChildThreadPlumbingTermination<Solution_> implements Termination<Solution_> {
    
    private volatile boolean terminated = false;
    
    public void terminateChildren() {
        terminated = true;
    }
    
    @Override
    public boolean isSolverTerminated(SolverScope<Solution_> solverScope) {
        return terminated;
    }
    
    @Override
    public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) { }
    @Override
    public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) { }
    @Override
    public void solvingStarted(SolverScope<Solution_> solverScope) { }
    @Override
    public void solvingEnded(SolverScope<Solution_> solverScope) { }
    @Override
    public void stepStarted(AbstractStepScope<Solution_> stepScope) { }
    @Override
    public void stepEnded(AbstractStepScope<Solution_> stepScope) { }
}
```

### UpcomingSelectionIterator

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/selector/common/iterator/UpcomingSelectionIterator.java`

Abstract base class for iterators that lazily compute upcoming selections.

```java
public abstract class UpcomingSelectionIterator<S> extends SelectionIterator<S> {
    
    /**
     * Lazily compute next upcoming selection.
     * @return next selection, or {@code null} if no more selections
     */
    protected abstract S createUpcomingSelection();
    
    /**
     * Indicates that there are no more selections.
     */
    protected final S noUpcomingSelection() {
        return noUpcomingSelection(true);
    }
    
    // ... implementation details
}
```

The [`PartitionQueue`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionQueue.java) iterator extends this class to provide lazy iteration over partition events.

### ThreadUtils

Utility class for managing thread pool shutdown.

```java
public final class ThreadUtils {
    
    public static void shutdownAwaitOrKill(ExecutorService executorService,
            String logIndentation, String name) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("{}    {} did not terminate in time, forcing shutdown.",
                    logIndentation, name);
                List<Runnable> droppedTasks = executorService.shutdownNow();
                logger.warn("{}    {} forced to drop {} tasks.",
                    logIndentation, name, droppedTasks.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }
}
```

### Pair Utility Class

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/util/Pair.java`

Immutable pair class used by [`PartitionChangeMove`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionChangeMove.java) to store entity-value mappings.

```java
public final class Pair<A, B> {
    
    private final A key;
    private final B value;
    
    public static <A, B> Pair<A, B> of(A key, B value) {
        return new Pair<>(key, value);
    }
    
    public A getKey() { return key; }
    public B getValue() { return value; }
}
```

### OrCompositeTermination

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/solver/termination/OrCompositeTermination.java`

Composite termination that terminates when any child termination triggers. Used by partition solvers to combine parent termination with child thread termination.

```java
public class OrCompositeTermination<Solution_> implements Termination<Solution_> {
    
    private final List<Termination<Solution_>> terminationList;
    
    @Override
    public boolean isSolverTerminated(SolverScope<Solution_> solverScope) {
        return terminationList.stream()
            .anyMatch(t -> t.isSolverTerminated(solverScope));
    }
    
    // ... other termination methods
}
```

### HeuristicConfigPolicy

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/HeuristicConfigPolicy.java`

Policy object that holds configuration for building solver components. Provides methods for creating child thread configurations.

```java
public class HeuristicConfigPolicy<Solution_> {
    
    public ThreadFactory buildThreadFactory(ChildThreadType childThreadType) { ... }
    
    public HeuristicConfigPolicy<Solution_> createPhaseConfigPolicy() { ... }
    
    public HeuristicConfigPolicy<Solution_> createChildThreadConfigPolicy(
            ChildThreadType childThreadType) { ... }
    
    public EnvironmentMode getEnvironmentMode() { ... }
    public String getLogIndentation() { ... }
}
```

### InnerScoreDirector

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/score/director/InnerScoreDirector.java`

Internal score director interface used by [`PartitionChangeMove`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionChangeMove.java) to access score director internals.

```java
public interface InnerScoreDirector<Solution_, Score_ extends Score<Score_>>
        extends ScoreDirector<Solution_> {
    
    SolutionDescriptor<Solution_> getSolutionDescriptor();
    Solution_ getWorkingSolution();
    
    void changeVariableFacade(GenuineVariableDescriptor<Solution_> variableDescriptor,
                             Object entity, Object value);
    
    Object lookUpWorkingObject(Object object);
}
```

### XSD Schema Definition

The XML schema must be updated to include partitionedSearch phase configuration.

**File:** `core/optaplanner-core-impl/src/main/resources/solver.xsd`

```xml
<!-- In phase choice element -->
<xs:element name="partitionedSearch" type="tns:partitionedSearchPhaseConfig"/>

<!-- Complex type definition -->
<xs:complexType name="partitionedSearchPhaseConfig">
    <xs:complexContent>
        <xs:extension base="tns:phaseConfig">
            <xs:sequence>
                <xs:element minOccurs="0" name="solutionPartitionerClass" type="xs:string"/>
                <xs:element minOccurs="0" name="solutionPartitionerCustomProperties" type="tns:jaxbAdaptedMap"/>
                <xs:element minOccurs="0" name="runnablePartThreadLimit" type="xs:string"/>
                <xs:choice maxOccurs="unbounded" minOccurs="0">
                    <xs:element name="constructionHeuristic" type="tns:constructionHeuristicPhaseConfig"/>
                    <xs:element name="customPhase" type="tns:customPhaseConfig"/>
                    <xs:element name="exhaustiveSearch" type="tns:exhaustiveSearchPhaseConfig"/>
                    <xs:element name="localSearch" type="tns:localSearchPhaseConfig"/>
                    <xs:element name="noChangePhase" type="tns:noChangePhaseConfig"/>
                    <xs:element name="partitionedSearch" type="tns:partitionedSearchPhaseConfig"/>
                </xs:choice>
            </xs:sequence>
        </xs:extension>
    </xs:complexContent>
</xs:complexType>
```

### Package Info for Configuration

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/config/partitionedsearch/package-info.java`

```java
@XmlSchema(
    namespace = SolverConfig.XML_NAMESPACE,
    elementFormDefault = XmlNsForm.QUALIFIED)
package org.optaplanner.core.config.partitionedsearch;

import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
import org.optaplanner.core.config.solver.SolverConfig;
```

---

## Implementation Steps

### Step 1: Create Configuration Class

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/config/partitionedsearch/PartitionedSearchPhaseConfig.java`

```java
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
    
    protected Class<? extends SolutionPartitioner<?>> solutionPartitionerClass = null;
    @XmlJavaTypeAdapter(JaxbCustomPropertiesAdapter.class)
    protected Map<String, String> solutionPartitionerCustomProperties = null;
    protected String runnablePartThreadLimit = null;
    
    @XmlElements({
        @XmlElement(name = ConstructionHeuristicPhaseConfig.XML_ELEMENT_NAME,
                type = ConstructionHeuristicPhaseConfig.class),
        @XmlElement(name = LocalSearchPhaseConfig.XML_ELEMENT_NAME,
                type = LocalSearchPhaseConfig.class),
        // ... other phase types
    })
    protected List<PhaseConfig> phaseConfigList = null;
    
    // Getters, setters, with-methods, inherit(), copyConfig(), visitReferencedClasses()
}
```

### Step 2: Create Phase Interface

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/PartitionedSearchPhase.java`

```java
public interface PartitionedSearchPhase<Solution_> extends Phase<Solution_> {
    // Marker interface extending Phase
}
```

### Step 3: Create Solution Partitioner Interface

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/partitioner/SolutionPartitioner.java`

```java
public interface SolutionPartitioner<Solution_> {
    
    List<Solution_> splitWorkingSolution(
        ScoreDirector<Solution_> scoreDirector, 
        Integer runnablePartThreadLimit);
}
```

### Step 4: Create Partition Queue

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionQueue.java`

```java
public class PartitionQueue<Solution_> 
        implements Iterable<PartitionChangeMove<Solution_>> {
    
    private BlockingQueue<PartitionChangedEvent<Solution_>> queue;
    private Map<Integer, PartitionChangedEvent<Solution_>> moveEventMap;
    private final Map<Integer, AtomicLong> nextEventIndexMap;
    private int openPartCount;
    private final Map<Integer, Long> processedEventIndexMap;
    
    public PartitionQueue(int partCount) {
        queue = new ArrayBlockingQueue<>(partCount * 100);
        moveEventMap = new ConcurrentHashMap<>(partCount);
        // Initialize maps...
    }
    
    public void addMove(int partIndex, PartitionChangeMove<Solution_> move) { ... }
    public void addFinish(int partIndex, long partCalculationCount) { ... }
    public void addExceptionThrown(int partIndex, Throwable throwable) { ... }
    
    @Override
    public Iterator<PartitionChangeMove<Solution_>> iterator() {
        return new PartitionQueueIterator();
    }
    
    private class PartitionQueueIterator 
            extends UpcomingSelectionIterator<PartitionChangeMove<Solution_>> {
        
        @Override
        protected PartitionChangeMove<Solution_> createUpcomingSelection() {
            while (true) {
                PartitionChangedEvent<Solution_> triggerEvent = queue.take();
                
                switch (triggerEvent.getType()) {
                    case MOVE:
                        // Skip outdated events, return latest move
                        ...
                    case FINISHED:
                        openPartCount--;
                        if (openPartCount <= 0) {
                            return noUpcomingSelection();
                        }
                        continue;
                    case EXCEPTION_THROWN:
                        throw new IllegalStateException(...);
                }
            }
        }
    }
}
```

### Step 5: Create Partition Changed Event

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionChangedEvent.java`

```java
public final class PartitionChangedEvent<Solution_> {
    
    private final int partIndex;
    private final long eventIndex;
    private final PartitionChangedEventType type;
    private final PartitionChangeMove<Solution_> move;
    private final Long partCalculationCount;
    private final Throwable throwable;
    
    public enum PartitionChangedEventType {
        MOVE,
        FINISHED,
        EXCEPTION_THROWN;
    }
    
    // Constructors for each event type
    // Getters for all fields
}
```

### Step 6: Create Partition Change Move

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionChangeMove.java`

```java
public final class PartitionChangeMove<Solution_> extends AbstractMove<Solution_> {
    
    public static <Solution_> PartitionChangeMove<Solution_> createMove(
            InnerScoreDirector<Solution_, ?> scoreDirector, int partIndex) {
        // Collect all entity-variable pairs from partition
        // Return new PartitionChangeMove
    }
    
    private final Map<GenuineVariableDescriptor<Solution_>, 
                      List<Pair<Object, Object>>> changeMap;
    private final int partIndex;
    
    @Override
    protected void doMoveOnGenuineVariables(ScoreDirector<Solution_> scoreDirector) {
        // Apply variable changes to destination solution
    }
    
    @Override
    public PartitionChangeMove<Solution_> rebase(
            ScoreDirector<Solution_> destinationScoreDirector) {
        // Rebase entities and values to destination solution
    }
}
```

### Step 7: Create Phase Scopes

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionedSearchPhaseScope.java`

```java
public class PartitionedSearchPhaseScope<Solution_> 
        extends AbstractPhaseScope<Solution_> {
    
    private Integer partCount;
    private PartitionedSearchStepScope<Solution_> lastCompletedStepScope;
    
    public PartitionedSearchPhaseScope(SolverScope<Solution_> solverScope) {
        super(solverScope);
        lastCompletedStepScope = new PartitionedSearchStepScope<>(this, -1);
    }
    
    // Getters and setters
}
```

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionedSearchStepScope.java`

```java
public class PartitionedSearchStepScope<Solution_> 
        extends AbstractStepScope<Solution_> {
    
    private final PartitionedSearchPhaseScope<Solution_> phaseScope;
    private PartitionChangeMove<Solution_> step = null;
    private String stepString = null;
    
    // Getters and setters
}
```

### Step 8: Create Partition Solver

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/PartitionSolver.java`

```java
public class PartitionSolver<Solution_> extends AbstractSolver<Solution_> {
    
    protected final SolverScope<Solution_> solverScope;
    
    public PartitionSolver(BestSolutionRecaller<Solution_> bestSolutionRecaller,
                          Termination<Solution_> termination,
                          List<Phase<Solution_>> phaseList,
                          SolverScope<Solution_> solverScope) {
        super(bestSolutionRecaller, termination, phaseList);
        this.solverScope = solverScope;
    }
    
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
    
    public long getScoreCalculationCount() {
        return solverScope.getScoreCalculationCount();
    }
}
```

### Step 9: Create Lifecycle Listener

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/event/PartitionedSearchPhaseLifecycleListener.java`

```java
public interface PartitionedSearchPhaseLifecycleListener<Solution_> 
        extends SolverLifecycleListener<Solution_> {
    
    void phaseStarted(PartitionedSearchPhaseScope<Solution_> phaseScope);
    void stepStarted(PartitionedSearchStepScope<Solution_> stepScope);
    void stepEnded(PartitionedSearchStepScope<Solution_> stepScope);
    void phaseEnded(PartitionedSearchPhaseScope<Solution_> phaseScope);
}
```

### Step 10: Create Factory

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/DefaultPartitionedSearchPhaseFactory.java`

```java
public class DefaultPartitionedSearchPhaseFactory<Solution_>
        extends AbstractPhaseFactory<Solution_, PartitionedSearchPhaseConfig> {
    
    @Override
    public PartitionedSearchPhase<Solution_> buildPhase(
            int phaseIndex, 
            HeuristicConfigPolicy<Solution_> solverConfigPolicy,
            BestSolutionRecaller<Solution_> bestSolutionRecaller, 
            Termination<Solution_> solverTermination) {
        
        HeuristicConfigPolicy<Solution_> phaseConfigPolicy = 
            solverConfigPolicy.createPhaseConfigPolicy();
        ThreadFactory threadFactory = solverConfigPolicy.buildThreadFactory(
            ChildThreadType.PART_THREAD);
        Termination<Solution_> phaseTermination = 
            buildPhaseTermination(phaseConfigPolicy, solverTermination);
        
        Integer resolvedActiveThreadCount = resolveActiveThreadCount(
            phaseConfig.getRunnablePartThreadLimit());
        
        List<PhaseConfig> phaseConfigList_ = phaseConfig.getPhaseConfigList();
        if (ConfigUtils.isEmptyCollection(phaseConfigList_)) {
            phaseConfigList_ = Arrays.asList(
                new ConstructionHeuristicPhaseConfig(),
                new LocalSearchPhaseConfig());
        }
        
        DefaultPartitionedSearchPhase.Builder<Solution_> builder = 
            new DefaultPartitionedSearchPhase.Builder<>(
                phaseIndex,
                solverConfigPolicy.getLogIndentation(),
                phaseTermination,
                buildSolutionPartitioner(),
                threadFactory,
                resolvedActiveThreadCount,
                phaseConfigList_,
                phaseConfigPolicy.createChildThreadConfigPolicy(
                    ChildThreadType.PART_THREAD));
        
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
                ConfigUtils.newInstance(phaseConfig, "solutionPartitionerClass", 
                    phaseConfig.getSolutionPartitionerClass());
            ConfigUtils.applyCustomProperties(solutionPartitioner, 
                "solutionPartitionerClass",
                phaseConfig.getSolutionPartitionerCustomProperties(), 
                "solutionPartitionerCustomProperties");
            return (SolutionPartitioner<Solution_>) solutionPartitioner;
        } else {
            throw new UnsupportedOperationException(
                "Generic partitioner not implemented. " +
                "Please specify a solutionPartitionerClass.");
        }
    }
}
```

### Step 11: Create Default Phase Implementation

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/DefaultPartitionedSearchPhase.java`

```java
public class DefaultPartitionedSearchPhase<Solution_> extends AbstractPhase<Solution_>
        implements PartitionedSearchPhase<Solution_>, 
                   PartitionedSearchPhaseLifecycleListener<Solution_> {
    
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
    
    @Override
    public void solve(SolverScope<Solution_> solverScope) {
        // Implementation as shown in Architecture section
    }
    
    private ExecutorService createThreadPoolExecutor(int partCount) {
        ThreadPoolExecutor threadPoolExecutor = 
            (ThreadPoolExecutor) Executors.newFixedThreadPool(partCount, threadFactory);
        if (threadPoolExecutor.getMaximumPoolSize() < partCount) {
            throw new IllegalStateException(...);
        }
        return threadPoolExecutor;
    }
    
    public PartitionSolver<Solution_> buildPartitionSolver(
            ChildThreadPlumbingTermination<Solution_> childThreadPlumbingTermination,
            Semaphore runnablePartThreadSemaphore,
            SolverScope<Solution_> solverScope) {
        BestSolutionRecaller<Solution_> bestSolutionRecaller =
            BestSolutionRecallerFactory.create().buildBestSolutionRecaller(
                configPolicy.getEnvironmentMode());
        Termination<Solution_> partTermination = new OrCompositeTermination<>(
            childThreadPlumbingTermination,
            phaseTermination.createChildThreadTermination(
                solverScope, ChildThreadType.PART_THREAD));
        List<Phase<Solution_>> phaseList =
            PhaseFactory.buildPhases(phaseConfigList, configPolicy, 
                bestSolutionRecaller, partTermination);
        
        SolverScope<Solution_> partSolverScope = 
            solverScope.createChildThreadSolverScope(ChildThreadType.PART_THREAD);
        partSolverScope.setRunnableThreadSemaphore(runnablePartThreadSemaphore);
        return new PartitionSolver<>(bestSolutionRecaller, partTermination, 
            phaseList, partSolverScope);
    }
    
    protected void doStep(PartitionedSearchStepScope<Solution_> stepScope) {
        Move<Solution_> nextStep = stepScope.getStep();
        nextStep.doMoveOnly(stepScope.getScoreDirector());
        calculateWorkingStepScore(stepScope, nextStep);
        solver.getBestSolutionRecaller().processWorkingSolutionDuringStep(stepScope);
    }
    
    // Lifecycle methods: phaseStarted, stepStarted, stepEnded, phaseEnded
    
    public static class Builder<Solution_> extends AbstractPhase.Builder<Solution_> {
        // Builder implementation
    }
}
```

### Step 12: Register Phase Factory

**File:** `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/phase/PhaseFactory.java`

```java
public interface PhaseFactory<Solution_> {
    
    static <Solution_> PhaseFactory<Solution_> create(PhaseConfig<?> phaseConfig) {
        if (LocalSearchPhaseConfig.class.isAssignableFrom(phaseConfig.getClass())) {
            return new DefaultLocalSearchPhaseFactory<>((LocalSearchPhaseConfig) phaseConfig);
        } else if (ConstructionHeuristicPhaseConfig.class.isAssignableFrom(phaseConfig.getClass())) {
            return new DefaultConstructionHeuristicPhaseFactory<>((ConstructionHeuristicPhaseConfig) phaseConfig);
        } else if (PartitionedSearchPhaseConfig.class.isAssignableFrom(phaseConfig.getClass())) {
            return new DefaultPartitionedSearchPhaseFactory<>((PartitionedSearchPhaseConfig) phaseConfig);
        } else if (CustomPhaseConfig.class.isAssignableFrom(phaseConfig.getClass())) {
            return new DefaultCustomPhaseFactory<>((CustomPhaseConfig) phaseConfig);
        } else if (ExhaustiveSearchPhaseConfig.class.isAssignableFrom(phaseConfig.getClass())) {
            return new DefaultExhaustiveSearchPhaseFactory<>((ExhaustiveSearchPhaseConfig) phaseConfig);
        } else if (NoChangePhaseConfig.class.isAssignableFrom(phaseConfig.getClass())) {
            return new NoChangePhaseFactory<>((NoChangePhaseConfig) phaseConfig);
        } else {
            throw new IllegalArgumentException(...);
        }
    }
    
    // ...
}
```

---

## Configuration

### XML Configuration

```xml
<solver>
    <partitionedSearch>
        <solutionPartitionerClass>com.example.MyPartitioner</solutionPartitionerClass>
        <solutionPartitionerCustomProperties>
            <property name="partSize" value="100"/>
        </solutionPartitionerCustomProperties>
        <runnablePartThreadLimit>AUTO</runnablePartThreadLimit>
        
        <!-- Nested phases for each partition -->
        <constructionHeuristic>
            <constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
        </constructionHeuristic>
        <localSearch>
            <localSearchType>HILL_CLIMBING</localSearchType>
        </localSearch>
    </partitionedSearch>
</solver>
```

### Java API Configuration

```java
SolverConfig solverConfig = new SolverConfig();

PartitionedSearchPhaseConfig partitionedSearchPhaseConfig = new PartitionedSearchPhaseConfig();
partitionedSearchPhaseConfig.setSolutionPartitionerClass(MyPartitioner.class);

Map<String, String> customProperties = new HashMap<>();
customProperties.put("partSize", "100");
partitionedSearchPhaseConfig.setSolutionPartitionerCustomProperties(customProperties);

partitionedSearchPhaseConfig.setRunnablePartThreadLimit("AUTO");

partitionedSearchPhaseConfig.setPhaseConfigList(Arrays.asList(
    new ConstructionHeuristicPhaseConfig(),
    new LocalSearchPhaseConfig()
));

solverConfig.setPhaseConfigList(Arrays.asList(partitionedSearchPhaseConfig));
```

### Runnable Part Thread Limit Options

| Value | Description |
|-------|-------------|
| `null` or `"AUTO"` | Use `availableProcessors - 2` (default) |
| `"UNLIMITED"` | No limit on runnable threads |
| Number | Specific limit (e.g., "4") |

---

## Custom Partitioner Implementation

### Example: Entity-Based Partitioner

```java
public class EntityPartitioner implements SolutionPartitioner<MySolution> {
    
    private int entitiesPerPartition = 10;
    
    public void setEntitiesPerPartition(int entitiesPerPartition) {
        this.entitiesPerPartition = entitiesPerPartition;
    }
    
    @Override
    public List<MySolution> splitWorkingSolution(
            ScoreDirector<MySolution> scoreDirector, 
            Integer runnablePartThreadLimit) {
        
        MySolution workingSolution = scoreDirector.getWorkingSolution();
        List<MyEntity> allEntities = workingSolution.getEntityList();
        
        List<MySolution> partitions = new ArrayList<>();
        
        for (int i = 0; i < allEntities.size(); i += entitiesPerPartition) {
            int end = Math.min(i + entitiesPerPartition, allEntities.size());
            List<MyEntity> partitionEntities = 
                new ArrayList<>(allEntities.subList(i, end));
            
            MySolution partition = new MySolution();
            partition.setEntityList(partitionEntities);
            // Copy problem facts (shared across partitions)
            partition.setValueList(workingSolution.getValueList());
            partition.setResourceList(workingSolution.getResourceList());
            
            partitions.add(partition);
        }
        
        return partitions;
    }
}
```

### Example: Geographic Partitioner

```java
public class GeographicPartitioner implements SolutionPartitioner<VehicleRoutingSolution> {
    
    private int partitions = 4;
    
    public void setPartitions(int partitions) {
        this.partitions = partitions;
    }
    
    @Override
    public List<VehicleRoutingSolution> splitWorkingSolution(
            ScoreDirector<VehicleRoutingSolution> scoreDirector,
            Integer runnablePartThreadLimit) {
        
        VehicleRoutingSolution workingSolution = scoreDirector.getWorkingSolution();
        List<Customer> customers = workingSolution.getCustomerList();
        List<Vehicle> vehicles = workingSolution.getVehicleList();
        
        // Cluster customers by geographic region
        Map<Integer, List<Customer>> customerClusters = 
            clusterCustomersByRegion(customers, partitions);
        
        List<VehicleRoutingSolution> partitions = new ArrayList<>();
        
        for (Map.Entry<Integer, List<Customer>> entry : customerClusters.entrySet()) {
            VehicleRoutingSolution partition = new VehicleRoutingSolution();
            partition.setCustomerList(entry.getValue());
            
            // Assign vehicles to each partition
            List<Vehicle> partitionVehicles = assignVehiclesToRegion(
                vehicles, entry.getKey(), partitions);
            partition.setVehicleList(partitionVehicles);
            
            // Copy shared problem facts
            partition.setDepotList(workingSolution.getDepotList());
            
            partitions.add(partition);
        }
        
        return partitions;
    }
    
    private Map<Integer, List<Customer>> clusterCustomersByRegion(
            List<Customer> customers, int numRegions) {
        // Implement clustering logic (e.g., k-means, grid-based)
        Map<Integer, List<Customer>> clusters = new HashMap<>();
        for (int i = 0; i < numRegions; i++) {
            clusters.put(i, new ArrayList<>());
        }
        
        for (Customer customer : customers) {
            int region = calculateRegion(customer.getLocation(), numRegions);
            clusters.get(region).add(customer);
        }
        
        return clusters;
    }
}
```

### Partitioner Best Practices

1. **Balanced Partitions**: Ensure partitions have roughly equal complexity
2. **Minimal Cross-Partition Constraints**: Minimize dependencies between partitions
3. **Shared Problem Facts**: Problem facts can be shared across partitions
4. **Entity Assignment**: Each entity must be in exactly one partition
5. **Custom Properties**: Use custom properties for configurable partitioning

---

## Testing

### Unit Test for Partitioner

```java
class MyPartitionerTest {
    
    @Test
    void splitWorkingSolution_createsCorrectPartitions() {
        MySolution solution = createTestSolution(100); // 100 entities
        
        ScoreDirector<MySolution> scoreDirector = mock(ScoreDirector.class);
        when(scoreDirector.getWorkingSolution()).thenReturn(solution);
        
        MyPartitioner partitioner = new MyPartitioner();
        partitioner.setEntitiesPerPartition(10);
        
        List<MySolution> partitions = partitioner.splitWorkingSolution(
            scoreDirector, 4);
        
        assertThat(partitions).hasSize(10);
        assertThat(partitions).allSatisfy(p -> 
            assertThat(p.getEntityList()).hasSize(10));
    }
}
```

### Integration Test for Partitioned Search

```java
class PartitionedSearchPhaseTest {
    
    @Test
    @Timeout(10)
    void partitionedSearch_solvesProblem() {
        SolverConfig solverConfig = new SolverConfig();
        
        PartitionedSearchPhaseConfig partitionedSearchPhaseConfig = 
            new PartitionedSearchPhaseConfig();
        partitionedSearchPhaseConfig.setSolutionPartitionerClass(
            TestdataSolutionPartitioner.class);
        
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put("partSize", "3");
        partitionedSearchPhaseConfig.setSolutionPartitionerCustomProperties(
            customProperties);
        
        partitionedSearchPhaseConfig.setPhaseConfigList(Arrays.asList(
            new ConstructionHeuristicPhaseConfig(),
            new LocalSearchPhaseConfig()
                .withTerminationConfig(new TerminationConfig()
                    .withStepCountLimit(100))
        ));
        
        solverConfig.setPhaseConfigList(
            Arrays.asList(partitionedSearchPhaseConfig));
        
        SolverFactory<TestdataSolution> solverFactory = 
            SolverFactory.create(solverConfig);
        Solver<TestdataSolution> solver = solverFactory.buildSolver();
        
        TestdataSolution problem = createTestSolution(21, 10);
        TestdataSolution solution = solver.solve(problem);
        
        assertThat(solution.getScore().isFeasible()).isTrue();
    }
}
```

---

## Key Design Decisions

### 1. Thread Pool Management

**Decision**: Use fixed-size thread pool with semaphore for CPU throttling.

**Rationale**:
- Fixed pool ensures all partitions can run eventually
- Semaphore prevents CPU hogging
- Allows graceful degradation on resource-constrained systems

### 2. Event-Based Communication

**Decision**: Use blocking queue with event objects for thread communication.

**Rationale**:
- Decouples partition threads from main thread
- Allows handling of multiple event types (moves, completion, exceptions)
- Built-in blocking semantics simplify synchronization

### 3. Move Rebase Pattern

**Decision**: Rebase moves from partition to main solution context.

**Rationale**:
- Partitions have cloned entities/values
- Main solution needs to reference original objects
- LookUpStrategy enables object translation

### 4. Latest-Event-Only Processing

**Decision**: Only process the latest move from each partition.

**Rationale**:
- Older moves are superseded by newer best solutions
- Reduces unnecessary score calculations
- Simplifies event handling logic

### 5. Nested Phase Configuration

**Decision**: Allow arbitrary nested phases for partition solving.

**Rationale**:
- Different problems may require different phase combinations
- Allows CH + LS as default (most common case)
- Provides flexibility for custom optimization strategies

### 6. Custom Partitioner Interface

**Decision**: Require custom partitioner implementation.

**Rationale**:
- Generic partitioning is problem-domain specific
- No one-size-fits-all partitioning strategy
- Allows optimization for specific problem structures

---

## File Structure

### Complete Directory Layout

```
core/optaplanner-core-impl/src/main/java/org/optaplanner/core/
├── config/
│   └── partitionedsearch/
│       ├── package-info.java
│       └── PartitionedSearchPhaseConfig.java
└── impl/
    └── partitionedsearch/
        ├── DefaultPartitionedSearchPhase.java
        ├── DefaultPartitionedSearchPhaseFactory.java
        ├── PartitionedSearchPhase.java
        ├── PartitionSolver.java
        ├── event/
        │   └── PartitionedSearchPhaseLifecycleListener.java
        ├── partitioner/
        │   └── SolutionPartitioner.java
        ├── queue/
        │   ├── PartitionChangedEvent.java
        │   └── PartitionQueue.java
        └── scope/
            ├── PartitionChangeMove.java
            ├── PartitionedSearchPhaseScope.java
            └── PartitionedSearchStepScope.java
```

### File Descriptions

| File | Description |
|------|-------------|
| `config/partitionedsearch/PartitionedSearchPhaseConfig.java` | XML/Java configuration class for partitioned search phase |
| `config/partitionedsearch/package-info.java` | JAXB XML schema package definition |
| `impl/partitionedsearch/PartitionedSearchPhase.java` | Marker interface extending Phase |
| `impl/partitionedsearch/DefaultPartitionedSearchPhase.java` | Main phase implementation with thread pool management |
| `impl/partitionedsearch/DefaultPartitionedSearchPhaseFactory.java` | Factory for building partitioned search phases |
| `impl/partitionedsearch/PartitionSolver.java` | Simplified solver for solving individual partitions |
| `impl/partitionedsearch/event/PartitionedSearchPhaseLifecycleListener.java` | Lifecycle listener interface for partitioned search |
| `impl/partitionedsearch/partitioner/SolutionPartitioner.java` | Interface for custom solution partitioning strategies |
| `impl/partitionedsearch/queue/PartitionChangedEvent.java` | Event types for partition communication |
| `impl/partitionedsearch/queue/PartitionQueue.java` | Thread-safe blocking queue for partition events |
| `impl/partitionedsearch/scope/PartitionChangeMove.java` | Move representing partition changes to apply to main solution |
| `impl/partitionedsearch/scope/PartitionedSearchPhaseScope.java` | Phase scope with partition count tracking |
| `impl/partitionedsearch/scope/PartitionedSearchStepScope.java` | Step scope for partition change moves |

### Required Modifications to Existing Files

| File | Modification |
|------|--------------|
| `impl/phase/PhaseFactory.java` | Add `PartitionedSearchPhaseConfig` case in `create()` method |
| `impl/phase/AbstractPhaseFactory.java` | Base class for phase factories (no changes needed) |
| `impl/phase/AbstractPhase.java` | Base class for phases (no changes needed) |
| `impl/solver/thread/ChildThreadType.java` | Add `PART_THREAD` enum value (already exists in this fork) |
| `impl/solver/termination/ChildThreadPlumbingTermination.java` | Termination coordination for child threads |
| `impl/solver/termination/OrCompositeTermination.java` | Composite termination for partition solvers |
| `impl/solver/thread/ThreadUtils.java` | Thread pool shutdown utility |
| `impl/score/director/InnerScoreDirector.java` | Internal score director interface (no changes needed) |
| `impl/heuristic/HeuristicConfigPolicy.java` | Configuration policy for building solver components |
| `impl/util/Pair.java` | Immutable pair utility (no changes needed) |
| `impl/heuristic/selector/common/iterator/UpcomingSelectionIterator.java` | Base iterator for lazy selection (no changes needed) |
| `impl/solver/scope/SolverScope.java` | Child thread scope creation support |
| `resources/solver.xsd` | Add partitionedSearchPhaseConfig XML schema type |

---

## Summary

The Partitioned Search feature in OptaPlanner enables parallel optimization of large planning problems by:

1. **Partitioning**: Splitting problem using a custom [`SolutionPartitioner`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/partitioner/SolutionPartitioner.java)
2. **Parallel Solving**: Running independent solver instances on each partition
3. **Merging**: Applying [`PartitionChangeMove`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionChangeMove.java) events to main solution
4. **Thread Management**: Using [`PartitionQueue`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionQueue.java) for thread-safe communication

The implementation consists of approximately 12 new Java files across configuration, factory, phase, queue, and scope packages, plus modifications to 11 existing files, providing a complete parallel optimization framework for OptaPlanner.

The Partitioned Search feature in OptaPlanner enables parallel optimization of large planning problems by:

1. **Partitioning**: Splitting the problem using a custom [`SolutionPartitioner`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/partitioner/SolutionPartitioner.java)
2. **Parallel Solving**: Running independent solver instances on each partition
3. **Merging**: Applying [`PartitionChangeMove`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionChangeMove.java) events to the main solution
4. **Thread Management**: Using [`PartitionQueue`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionQueue.java) for thread-safe communication

The implementation consists of approximately 12 new Java files across configuration, factory, phase, queue, and scope packages, plus modifications to 6 existing files, providing a complete parallel optimization framework for OptaPlanner.
