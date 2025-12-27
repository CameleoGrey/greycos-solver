# Partitioned Search Feature - Implementation Guide

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Core Components](#core-components)
4. [Implementation Steps](#implementation-steps)
5. [Configuration](#configuration)
6. [Usage Examples](#usage-examples)
7. [Testing](#testing)
8. [Key Implementation Details](#key-implementation-details)

---

## Overview

**Partitioned Search** is a parallel optimization technique in OptaPlanner that divides a large planning problem into smaller, independent partitions that can be solved concurrently. This feature is particularly useful for:

- Large-scale problems with natural partitioning (e.g., vehicle routing by geographic regions)
- Problems where entities can be grouped independently
- Scenarios requiring faster solution times through parallelization

### Key Benefits
- **Parallel Processing**: Multiple partitions solved simultaneously on separate threads
- **Scalability**: Handles larger problems by dividing them into manageable chunks
- **Flexibility**: Custom partitioning strategies via [`SolutionPartitioner`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/partitioner/SolutionPartitioner.java:37)
- **Resource Management**: Configurable thread limits to prevent CPU exhaustion

---

## Architecture

### High-Level Flow

```
┌─────────────────────────────────────────────────────────────┐
│                     Main Solver Thread                       │
│  ┌──────────────────────────────────────────────────────┐   │
│  │          PartitionedSearchPhase                      │   │
│  │  1. splitWorkingSolution() → List<Solution>         │   │
│  │  2. Create PartitionSolver for each partition       │   │
│  │  3. Submit to ExecutorService                        │   │
│  │  4. Process moves from PartitionQueue                │   │
│  │  5. Merge best solutions                             │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
        ┌───────────────────┴───────────────────┐
        │                                       │
        ▼                                       ▼
┌───────────────┐                       ┌───────────────┐
│ Partition 1   │                       │ Partition N   │
│   Thread      │                       │   Thread      │
│  ┌─────────┐ │                       │  ┌─────────┐ │
│  │Phase 1  │ │                       │  │Phase 1  │ │
│  │Phase 2  │ │                       │  │Phase 2  │ │
│  │...      │ │                       │  │...      │ │
│  └─────────┘ │                       │  └─────────┘ │
└───────────────┘                       └───────────────┘
        │                                       │
        └───────────────────┬───────────────────┘
                            │
                            ▼
                    ┌───────────────┐
                    │PartitionQueue │
                    │  (Thread-safe │
                    │   blocking)   │
                    └───────────────┘
                            │
                            ▼
                    ┌───────────────┐
                    │ Main Solver   │
                    │  (applies     │
                    │   moves)      │
                    └───────────────┘
```

### Component Hierarchy

```
Solver
└── Phase
    └── PartitionedSearchPhase
        ├── SolutionPartitioner (user-provided)
        ├── PartitionSolver (child solver)
        │   └── Phase[] (CH, LS, etc.)
        ├── PartitionQueue (thread-safe communication)
        ├── PartitionChangeMove (merge mechanism)
        └── PartitionedSearchPhaseScope/StepScope
```

---

## Core Components

### 1. PartitionedSearchPhase Interface

**Location**: [`PartitionedSearchPhase.java`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/PartitionedSearchPhase.java:35)

```java
public interface PartitionedSearchPhase<Solution_> extends Phase<Solution_> {
    // Marker interface for partitioned search phase
}
```

**Purpose**: Defines the contract for a phase that uses partitioned search algorithm.

---

### 2. DefaultPartitionedSearchPhase (Main Implementation)

**Location**: [`DefaultPartitionedSearchPhase.java`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/DefaultPartitionedSearchPhase.java:58)

**Key Responsibilities**:
- Coordinate partition creation and solving
- Manage thread pool and executor service
- Process partition results via [`PartitionQueue`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionQueue.java:42)
- Apply partition changes to main solution

**Core Method**:
```java
@Override
public void solve(SolverScope<Solution_> solverScope) {
    // 1. Create phase scope
    PartitionedSearchPhaseScope<Solution_> phaseScope = 
        new PartitionedSearchPhaseScope<>(solverScope);
    
    // 2. Split solution into partitions
    List<Solution_> partList = solutionPartitioner.splitWorkingSolution(
        solverScope.getScoreDirector(), runnablePartThreadLimit);
    
    // 3. Create thread pool
    ExecutorService executor = createThreadPoolExecutor(partCount);
    
    // 4. Create queue for inter-thread communication
    PartitionQueue<Solution_> partitionQueue = new PartitionQueue<>(partCount);
    
    // 5. Submit partition solvers
    for (Solution_ part : partList) {
        PartitionSolver<Solution_> partitionSolver = buildPartitionSolver(...);
        partitionSolver.addEventListener(event -> {
            // Create move and add to queue
            PartitionChangeMove<Solution_> move = 
                PartitionChangeMove.createMove(childScoreDirector, partIndex);
            partitionQueue.addMove(partIndex, move);
        });
        executor.submit(() -> partitionSolver.solve(part));
    }
    
    // 6. Process moves from queue (blocking)
    for (PartitionChangeMove<Solution_> step : partitionQueue) {
        doStep(stepScope);  // Apply to main solution
    }
    
    // 7. Cleanup
    executor.shutdown();
}
```

---

### 3. SolutionPartitioner (User-Provided)

**Location**: [`SolutionPartitioner.java`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/partitioner/SolutionPartitioner.java:37)

**Purpose**: Defines how to split a solution into independent partitions.

**Interface**:
```java
public interface SolutionPartitioner<Solution_> {
    /**
     * Splits one solution into multiple partitions.
     * Each planning entity must be in exactly one partition.
     * Problem facts can be in multiple partitions.
     * 
     * @param scoreDirector ScoreDirector with working solution to split
     * @param runnablePartThreadLimit Thread limit (null = unlimited)
     * @return List of partition solutions (size >= 1)
     */
    List<Solution_> splitWorkingSolution(
        ScoreDirector<Solution_> scoreDirector, 
        Integer runnablePartThreadLimit);
}
```

**Example Implementation**:
```java
public class TestdataSolutionPartitioner implements SolutionPartitioner<TestdataSolution> {
    private int partSize = 1;  // Configurable via custom properties
    
    @Override
    public List<TestdataSolution> splitWorkingSolution(
            ScoreDirector<TestdataSolution> scoreDirector,
            Integer runnablePartThreadLimit) {
        TestdataSolution workingSolution = scoreDirector.getWorkingSolution();
        List<TestdataEntity> allEntities = workingSolution.getEntityList();
        
        List<TestdataSolution> partitions = new ArrayList<>();
        for (int i = 0; i < allEntities.size() / partSize; i++) {
            List<TestdataEntity> partitionEntities = 
                new ArrayList<>(allEntities.subList(i * partSize, (i + 1) * partSize));
            TestdataSolution partition = new TestdataSolution();
            partition.setEntityList(partitionEntities);
            partition.setValueList(workingSolution.getValueList());  // Shared facts
            partitions.add(partition);
        }
        return partitions;
    }
}
```

---

### 4. PartitionSolver (Child Solver)

**Location**: [`PartitionSolver.java`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/PartitionSolver.java:36)

**Purpose**: Lightweight solver instance that runs on a separate thread for each partition.

**Key Characteristics**:
- Extends [`AbstractSolver`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/solver/AbstractSolver.java:52)
- Runs configured phases (Construction Heuristic, Local Search, etc.)
- Emits events when best solution improves
- Does not support problem fact changes or early termination

**Solve Method**:
```java
@Override
public Solution_ solve(Solution_ problem) {
    solverScope.initializeYielding();
    try {
        solverScope.setBestSolution(problem);
        solvingStarted(solverScope);
        runPhases(solverScope);  // Run CH, LS, etc.
        solvingEnded(solverScope);
        return solverScope.getBestSolution();
    } finally {
        solverScope.destroyYielding();
    }
}
```

---

### 5. PartitionQueue (Thread-Safe Communication)

**Location**: [`PartitionQueue.java`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionQueue.java:42)

**Purpose**: Thread-safe blocking queue for communication between partition threads and main solver thread.

**Event Types**:
1. **MOVE**: New best solution found in partition
2. **FINISHED**: Partition completed solving
3. **EXCEPTION_THROWN**: Error in partition thread

**Key Features**:
- Uses [`ArrayBlockingQueue`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionQueue.java:59) for thread-safe blocking
- Skips outdated moves (only latest move per partition is applied)
- Propagates exceptions from child threads to main thread
- Tracks score calculation count

**Producer Methods** (called by partition threads):
```java
// Add new best solution move
public void addMove(int partIndex, PartitionChangeMove<Solution_> move) {
    long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
    PartitionChangedEvent<Solution_> event = 
        new PartitionChangedEvent<>(partIndex, eventIndex, move);
    moveEventMap.put(event.getPartIndex(), event);
    queue.add(event);
}

// Signal partition completion
public void addFinish(int partIndex, long partCalculationCount) {
    long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
    PartitionChangedEvent<Solution_> event = 
        new PartitionChangedEvent<>(partIndex, eventIndex, partCalculationCount);
    queue.add(event);
}

// Signal exception
public void addExceptionThrown(int partIndex, Throwable throwable) {
    long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
    PartitionChangedEvent<Solution_> event = 
        new PartitionChangedEvent<>(partIndex, eventIndex, throwable);
    queue.add(event);
}
```

**Consumer Iterator** (called by main thread):
```java
@Override
public Iterator<PartitionChangeMove<Solution_>> iterator() {
    return new PartitionQueueIterator();
}

private class PartitionQueueIterator extends UpcomingSelectionIterator<...> {
    @Override
    protected PartitionChangeMove<Solution_> createUpcomingSelection() {
        while (true) {
            PartitionChangedEvent<Solution_> triggerEvent = queue.take();  // Blocking
            switch (triggerEvent.getType()) {
                case MOVE:
                    // Skip if outdated
                    if (triggerEvent.getEventIndex() <= processedEventIndexMap.get(partIndex)) {
                        continue;
                    }
                    return latestMoveEvent.getMove();
                case FINISHED:
                    openPartCount--;
                    if (openPartCount <= 0) {
                        return noUpcomingSelection();  // All done
                    }
                    continue;
                case EXCEPTION_THROWN:
                    throw new IllegalStateException(..., triggerEvent.getThrowable());
            }
        }
    }
}
```

---

### 6. PartitionChangeMove (Merge Mechanism)

**Location**: [`PartitionChangeMove.java`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionChangeMove.java:43)

**Purpose**: Represents changes from a partition's best solution to be applied to the main solution.

**Creation**:
```java
public static <Solution_> PartitionChangeMove<Solution_> createMove(
        InnerScoreDirector<Solution_, ?> scoreDirector, int partIndex) {
    // Collect all entity variable changes
    Map<GenuineVariableDescriptor<Solution_>, List<Pair<Object, Object>>> changeMap = 
        new LinkedHashMap<>();
    
    for (EntityDescriptor<Solution_> entityDescriptor : entityDescriptors) {
        for (GenuineVariableDescriptor<Solution_> variableDescriptor : 
                entityDescriptor.getDeclaredGenuineVariableDescriptors()) {
            changeMap.put(variableDescriptor, new ArrayList<>(entityCount));
        }
    }
    
    // Visit all entities and capture current variable values
    solutionDescriptor.visitAllEntities(workingSolution, entity -> {
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
```

**Application**:
```java
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
```

**Rebasing** (converting from child to parent score director):
```java
@Override
public PartitionChangeMove<Solution_> rebase(
        ScoreDirector<Solution_> destinationScoreDirector) {
    Map<GenuineVariableDescriptor<Solution_>, List<Pair<Object, Object>>> 
        destinationChangeMap = new LinkedHashMap<>();
    
    for (Map.Entry<GenuineVariableDescriptor<Solution_>, 
            List<Pair<Object, Object>>> entry : changeMap.entrySet()) {
        GenuineVariableDescriptor<Solution_> variableDescriptor = entry.getKey();
        List<Pair<Object, Object>> destinationPairList = new ArrayList<>();
        
        for (Pair<Object, Object> pair : entry.getValue()) {
            Object originEntity = pair.getKey();
            Object destinationEntity = 
                destinationScoreDirector.lookUpWorkingObject(originEntity);
            
            Object originValue = pair.getValue();
            Object destinationValue = 
                destinationScoreDirector.lookUpWorkingObject(originValue);
            
            destinationPairList.add(Pair.of(destinationEntity, destinationValue));
        }
        destinationChangeMap.put(variableDescriptor, destinationPairList);
    }
    return new PartitionChangeMove<>(destinationChangeMap, partIndex);
}
```

---

### 7. Configuration Classes

**PartitionedSearchPhaseConfig**: [`PartitionedSearchPhaseConfig.java`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/config/partitionedsearch/PartitionedSearchPhaseConfig.java:49)

**Key Properties**:
```java
// User-provided partitioner class
private Class<? extends SolutionPartitioner<?>> solutionPartitionerClass;

// Custom properties for partitioner
private Map<String, String> solutionPartitionerCustomProperties;

// Thread limit (null, "AUTO", "UNLIMITED", or number)
private String runnablePartThreadLimit;

// Phases to run in each partition (defaults to CH + LS)
private List<PhaseConfig> phaseConfigList;
```

**DefaultPartitionedSearchPhaseFactory**: [`DefaultPartitionedSearchPhaseFactory.java`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/DefaultPartitionedSearchPhaseFactory.java:44)

**Responsibilities**:
- Build [`PartitionedSearchPhase`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/PartitionedSearchPhase.java:35) from config
- Instantiate [`SolutionPartitioner`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/partitioner/SolutionPartitioner.java:37)
- Resolve thread count
- Set up child thread configuration

---

### 8. Scope Classes

**PartitionedSearchPhaseScope**: [`PartitionedSearchPhaseScope.java`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionedSearchPhaseScope.java:29)
- Tracks partition count
- Maintains last completed step

**PartitionedSearchStepScope**: [`PartitionedSearchStepScope.java`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionedSearchStepScope.java:28)
- Represents a single step (partition change)
- Holds the [`PartitionChangeMove`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionChangeMove.java:43)

---

## Implementation Steps

### Step 1: Create Core Interface

**File**: `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/PartitionedSearchPhase.java`

```java
package org.optaplanner.core.impl.partitionedsearch;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.impl.phase.AbstractPhase;
import org.optaplanner.core.impl.phase.Phase;

/**
 * A {@link PartitionedSearchPhase} is a {@link Phase} which uses a Partition Search algorithm.
 * It splits the {@link PlanningSolution} into pieces and solves those separately with other {@link Phase}s.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public interface PartitionedSearchPhase<Solution_> extends Phase<Solution_> {
}
```

---

### Step 2: Implement SolutionPartitioner Interface

**File**: `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/partitioner/SolutionPartitioner.java`

```java
package org.optaplanner.core.impl.partitionedsearch.partitioner;

import java.util.List;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.score.director.ScoreDirector;

/**
 * Splits one {@link PlanningSolution solution} into multiple partitions.
 * The partitions are solved and merged based on the {@link PlanningSolution#lookUpStrategyType()}.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public interface SolutionPartitioner<Solution_> {
    /**
     * Returns a list of partition cloned {@link PlanningSolution solutions}
     * for which each {@link PlanningEntity planning entity}
     * is partition cloned into exactly 1 of those partitions.
     *
     * @param scoreDirector never null, the {@link ScoreDirector}
     *        which has the {@link ScoreDirector#getWorkingSolution()} that needs to be split up
     * @param runnablePartThreadLimit null if unlimited, never negative
     * @return never null, {@link List#size()} of at least 1.
     */
    List<Solution_> splitWorkingSolution(
        ScoreDirector<Solution_> scoreDirector, 
        Integer runnablePartThreadLimit);
}
```

---

### Step 3: Create PartitionedSearchPhaseScope and StepScope

**File**: `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionedSearchPhaseScope.java`

```java
package org.optaplanner.core.impl.partitionedsearch.scope;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.impl.phase.scope.AbstractPhaseScope;
import org.optaplanner.core.impl.solver.scope.SolverScope;

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

    public void setLastCompletedStepScope(
            PartitionedSearchStepScope<Solution_> lastCompletedStepScope) {
        this.lastCompletedStepScope = lastCompletedStepScope;
    }
}
```

**File**: `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionedSearchStepScope.java`

```java
package org.optaplanner.core.impl.partitionedsearch.scope;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.impl.phase.scope.AbstractStepScope;

public class PartitionedSearchStepScope<Solution_> extends AbstractStepScope<Solution_> {
    private final PartitionedSearchPhaseScope<Solution_> phaseScope;
    private PartitionChangeMove<Solution_> step = null;
    private String stepString = null;

    public PartitionedSearchStepScope(PartitionedSearchPhaseScope<Solution_> phaseScope) {
        this(phaseScope, phaseScope.getNextStepIndex());
    }

    public PartitionedSearchStepScope(
            PartitionedSearchPhaseScope<Solution_> phaseScope, int stepIndex) {
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

    public String getStepString() {
        return stepString;
    }

    public void setStepString(String stepString) {
        this.stepString = stepString;
    }
}
```

---

### Step 4: Create PartitionChangeMove

**File**: `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionChangeMove.java`

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
 * Applies a new best solution from a partition child solver into the global working solution.
 */
public final class PartitionChangeMove<Solution_> extends AbstractMove<Solution_> {

    public static <Solution_> PartitionChangeMove<Solution_> createMove(
            InnerScoreDirector<Solution_, ?> scoreDirector, int partIndex) {
        SolutionDescriptor<Solution_> solutionDescriptor = 
            scoreDirector.getSolutionDescriptor();
        Solution_ workingSolution = scoreDirector.getWorkingSolution();

        int entityCount = solutionDescriptor.getEntityCount(workingSolution);
        Map<GenuineVariableDescriptor<Solution_>, List<Pair<Object, Object>>> changeMap = 
            new LinkedHashMap<>(solutionDescriptor.getEntityDescriptors().size() * 3);
        
        for (EntityDescriptor<Solution_> entityDescriptor : 
                solutionDescriptor.getEntityDescriptors()) {
            for (GenuineVariableDescriptor<Solution_> variableDescriptor : 
                    entityDescriptor.getDeclaredGenuineVariableDescriptors()) {
                changeMap.put(variableDescriptor, new ArrayList<>(entityCount));
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

    private final Map<GenuineVariableDescriptor<Solution_>, List<Pair<Object, Object>>> changeMap;
    private final int partIndex;

    public PartitionChangeMove(
            Map<GenuineVariableDescriptor<Solution_>, List<Pair<Object, Object>>> changeMap,
            int partIndex) {
        this.changeMap = changeMap;
        this.partIndex = partIndex;
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
    public boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector) {
        return true;
    }

    @Override
    protected PartitionChangeMove<Solution_> createUndoMove(
            ScoreDirector<Solution_> scoreDirector) {
        throw new UnsupportedOperationException("Undo move should not be called.");
    }

    @Override
    public PartitionChangeMove<Solution_> rebase(
            ScoreDirector<Solution_> destinationScoreDirector) {
        Map<GenuineVariableDescriptor<Solution_>, List<Pair<Object, Object>>> 
            destinationChangeMap = new LinkedHashMap<>(changeMap.size());
        
        for (Map.Entry<GenuineVariableDescriptor<Solution_>, 
                List<Pair<Object, Object>>> entry : changeMap.entrySet()) {
            GenuineVariableDescriptor<Solution_> variableDescriptor = entry.getKey();
            List<Pair<Object, Object>> originPairList = entry.getValue();
            List<Pair<Object, Object>> destinationPairList = 
                new ArrayList<>(originPairList.size());
            
            for (Pair<Object, Object> pair : originPairList) {
                Object originEntity = pair.getKey();
                Object destinationEntity = 
                    destinationScoreDirector.lookUpWorkingObject(originEntity);
                if (destinationEntity == null && originEntity != null) {
                    throw new IllegalStateException("The destinationEntity (" + destinationEntity
                            + ") cannot be null if the originEntity (" + originEntity + ") is not null.");
                }
                
                Object originValue = pair.getValue();
                Object destinationValue = 
                    destinationScoreDirector.lookUpWorkingObject(originValue);
                if (destinationValue == null && originValue != null) {
                    throw new IllegalStateException("The destinationEntity (" + destinationEntity
                            + ")'s destinationValue (" + destinationValue
                            + ") cannot be null if the originEntity (" + originEntity
                            + ")'s originValue (" + originValue + ") is not null.\n"
                            + "Maybe add the originValue (" + originValue + ") of class (" 
                            + originValue.getClass()
                            + ") as problem fact in the planning solution with a "
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
        throw new UnsupportedOperationException("Impossible situation: " 
            + PartitionChangeMove.class.getSimpleName()
            + " is only used to communicate between a part thread and the solver thread.");
    }

    @Override
    public Collection<? extends Object> getPlanningValues() {
        throw new UnsupportedOperationException("Impossible situation: " 
            + PartitionChangeMove.class.getSimpleName()
            + " is only used to communicate between a part thread and the solver thread.");
    }

    @Override
    public String toString() {
        int changeCount = changeMap.values().stream().mapToInt(List::size).sum();
        return "part-" + partIndex + " {" + changeCount + " variables changed}";
    }
}
```

---

### Step 5: Create PartitionQueue

**File**: `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionQueue.java`

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
        queue = new ArrayBlockingQueue<>(partCount * 100);
        moveEventMap = new ConcurrentHashMap<>(partCount);
        
        Map<Integer, AtomicLong> nextEventIndexMap = new HashMap<>(partCount);
        for (int i = 0; i < partCount; i++) {
            nextEventIndexMap.put(i, new AtomicLong(0));
        }
        this.nextEventIndexMap = Collections.unmodifiableMap(nextEventIndexMap);
        
        openPartCount = partCount;
        partsCalculationCount = 0L;
        
        processedEventIndexMap = new HashMap<>(partCount);
        for (int i = 0; i < partCount; i++) {
            processedEventIndexMap.put(i, -1L);
        }
    }

    public void addMove(int partIndex, PartitionChangeMove<Solution_> move) {
        long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
        PartitionChangedEvent<Solution_> event = 
            new PartitionChangedEvent<>(partIndex, eventIndex, move);
        moveEventMap.put(event.getPartIndex(), event);
        queue.add(event);
    }

    public void addFinish(int partIndex, long partCalculationCount) {
        long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
        PartitionChangedEvent<Solution_> event = 
            new PartitionChangedEvent<>(partIndex, eventIndex, partCalculationCount);
        queue.add(event);
    }

    public void addExceptionThrown(int partIndex, Throwable throwable) {
        long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
        PartitionChangedEvent<Solution_> event = 
            new PartitionChangedEvent<>(partIndex, eventIndex, throwable);
        queue.add(event);
    }

    @Override
    public Iterator<PartitionChangeMove<Solution_>> iterator() {
        return new PartitionQueueIterator();
    }

    private class PartitionQueueIterator 
            extends UpcomingSelectionIterator<PartitionChangeMove<Solution_>> {
        @Override
        protected PartitionChangeMove<Solution_> createUpcomingSelection() {
            while (true) {
                PartitionChangedEvent<Solution_> triggerEvent;
                try {
                    triggerEvent = queue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                        "Solver thread was interrupted in Partitioned Search.", e);
                }
                switch (triggerEvent.getType()) {
                    case MOVE:
                        int partIndex = triggerEvent.getPartIndex();
                        long processedEventIndex = processedEventIndexMap.get(partIndex);
                        if (triggerEvent.getEventIndex() <= processedEventIndex) {
                            LOGGER.trace("    Skipped event of partIndex ({}).", partIndex);
                            continue;
                        }
                        PartitionChangedEvent<Solution_> latestMoveEvent = 
                            moveEventMap.get(partIndex);
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

---

### Step 6: Create PartitionChangedEvent

**File**: `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionChangedEvent.java`

```java
package org.optaplanner.core.impl.partitionedsearch.queue;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.impl.partitionedsearch.scope.PartitionChangeMove;

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

    public PartitionChangedEvent(int partIndex, long eventIndex, 
            PartitionChangeMove<Solution_> move) {
        this.partIndex = partIndex;
        this.eventIndex = eventIndex;
        this.type = PartitionChangedEventType.MOVE;
        this.move = move;
        partCalculationCount = null;
        throwable = null;
    }

    public PartitionChangedEvent(int partIndex, long eventIndex, Throwable throwable) {
        this.partIndex = partIndex;
        this.eventIndex = eventIndex;
        this.type = PartitionChangedEventType.EXCEPTION_THROWN;
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

### Step 7: Create PartitionSolver

**File**: `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/PartitionSolver.java`

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
    public boolean addProblemFactChanges(List<ProblemFactChange<Solution_> problemFactChanges) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addProblemChange(ProblemChange<Solution_> problemChange) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addProblemChanges(List<ProblemChange<Solution_> problemChangeList) {
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
    }

    public long getScoreCalculationCount() {
        return solverScope.getScoreCalculationCount();
    }
}
```

---

### Step 8: Create DefaultPartitionedSearchPhase

**File**: `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/DefaultPartitionedSearchPhase.java`

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
        PartitionedSearchPhaseScope<Solution_> phaseScope = 
            new PartitionedSearchPhaseScope<>(solverScope);
        
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
                    PartitionChangeMove<Solution_> move = 
                        PartitionChangeMove.createMove(childScoreDirector, partIndex);
                    InnerScoreDirector<Solution_, ?> parentScoreDirector = 
                        solverScope.getScoreDirector();
                    move = move.rebase(parentScoreDirector);
                    partitionQueue.addMove(partIndex, move);
                });
                executor.submit(() -> {
                    try {
                        partitionSolver.solve(part);
                        long partCalculationCount = partitionSolver.getScoreCalculationCount();
                        partitionQueue.addFinish(partIndex, partCalculationCount);
                    } catch (Throwable throwable) {
                        logger.trace("{}            Part thread ({}) exception that will be propagated to the solver thread.",
                                logIndentation, partIndex, throwable);
                        partitionQueue.addExceptionThrown(partIndex, throwable);
                    }
                });
            }
            
            for (PartitionChangeMove<Solution_> step : partitionQueue) {
                PartitionedSearchStepScope<Solution_> stepScope = 
                    new PartitionedSearchStepScope<>(phaseScope);
                stepStarted(stepScope);
                stepScope.setStep(step);
                if (logger.isDebugEnabled()) {
                    stepScope.setStepString(step.toString());
                }
                doStep(stepScope);
                stepEnded(stepScope);
                phaseScope.setLastCompletedStepScope(stepScope);
            }
            
            phaseScope.addChildThreadsScoreCalculationCount(
                partitionQueue.getPartsCalculationCount());
        } finally {
            childThreadPlumbingTermination.terminateChildren();
            ThreadUtils.shutdownAwaitOrKill(executor, logIndentation, "Partitioned Search");
        }
        phaseEnded(phaseScope);
    }

    private ExecutorService createThreadPoolExecutor(int partCount) {
        ThreadPoolExecutor threadPoolExecutor = 
            (ThreadPoolExecutor) Executors.newFixedThreadPool(partCount, threadFactory);
        if (threadPoolExecutor.getMaximumPoolSize() < partCount) {
            throw new IllegalStateException(
                    "The threadPoolExecutor's maximumPoolSize (" 
                        + threadPoolExecutor.getMaximumPoolSize()
                            + ") is less than the partCount (" + partCount 
                            + "), so some partitions will starve.\n"
                            + "Normally this is impossible because the threadPoolExecutor should be unbounded."
                            + " Use runnablePartThreadLimit (" + runnablePartThreadLimit
                            + ") instead to avoid CPU hogging and live locks.");
        }
        return threadPoolExecutor;
    }

    public PartitionSolver<Solution_> buildPartitionSolver(
            ChildThreadPlumbingTermination<Solution_> childThreadPlumbingTermination,
            Semaphore runnablePartThreadSemaphore,
            SolverScope<Solution_> solverScope) {
        BestSolutionRecaller<Solution_> bestSolutionRecaller =
                BestSolutionRecallerFactory.create()
                    .buildBestSolutionRecaller(configPolicy.getEnvironmentMode());
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
                    (stepScope.getBestScoreImproved() ? "new" : "   "), 
                    phaseScope.getBestScore(),
                    stepScope.getStepString());
        }
    }

    @Override
    public void phaseEnded(PartitionSearchPhaseScope<Solution_> phaseScope) {
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

        public Builder(int phaseIndex, String logIndentation, 
                Termination<Solution_> phaseTermination,
                SolutionPartitioner<Solution_> solutionPartitioner, 
                ThreadFactory threadFactory,
                Integer runnablePartThreadLimit, 
                List<PhaseConfig> phaseConfigList,
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

---

### Step 9: Create Configuration Classes

**File**: `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/config/partitionedsearch/PartitionedSearchPhaseConfig.java`

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

    protected Class<? extends SolutionPartitioner<?>> solutionPartitionerClass = null;
    @XmlJavaTypeAdapter(JaxbCustomPropertiesAdapter.class)
    protected Map<String, String> solutionPartitionerCustomProperties = null;
    protected String runnablePartThreadLimit = null;

    @XmlElements({
            @XmlElement(name = ConstructionHeuristicPhaseConfig.XML_ELEMENT_NAME,
                    type = ConstructionHeuristicPhaseConfig.class),
            @XmlElement(name = CustomPhaseConfig.XML_ELEMENT_NAME, 
                    type = CustomPhaseConfig.class),
            @XmlElement(name = ExhaustiveSearchPhaseConfig.XML_ELEMENT_NAME, 
                    type = ExhaustiveSearchPhaseConfig.class),
            @XmlElement(name = LocalSearchPhaseConfig.XML_ELEMENT_NAME, 
                    type = LocalSearchPhaseConfig.class),
            @XmlElement(name = NoChangePhaseConfig.XML_ELEMENT_NAME, 
                    type = NoChangePhaseConfig.class),
            @XmlElement(name = PartitionedSearchPhaseConfig.XML_ELEMENT_NAME, 
                    type = PartitionedSearchPhaseConfig.class)
    })
    protected List<PhaseConfig> phaseConfigList = null;

    // Getters and setters
    public Class<? extends SolutionPartitioner<?>> getSolutionPartitionerClass() {
        return solutionPartitionerClass;
    }

    public void setSolutionPartitionerClass(
            Class<? extends SolutionPartitioner<?>> solutionPartitionerClass) {
        this.solutionPartitionerClass = solutionPartitionerClass;
    }

    public Map<String, String> getSolutionPartitionerCustomProperties() {
        return solutionPartitionerCustomProperties;
    }

    public void setSolutionPartitionerCustomProperties(
            Map<String, String> solutionPartitionerCustomProperties) {
        this.solutionPartitionerCustomProperties = solutionPartitionerCustomProperties;
    }

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

    // With methods
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

    public PartitionedSearchPhaseConfig withRunnablePartThreadLimit(
            String runnablePartThreadLimit) {
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
                solutionPartitionerCustomProperties, 
                inheritedConfig.getSolutionPartitionerCustomProperties());
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

### Step 10: Create DefaultPartitionedSearchPhaseFactory

**File**: `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/DefaultPartitionedSearchPhaseFactory.java`

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
    private static final Logger LOGGER = 
        LoggerFactory.getLogger(DefaultPartitionedSearchPhaseFactory.class);

    public DefaultPartitionedSearchPhaseFactory(PartitionedSearchPhaseConfig phaseConfig) {
        super(phaseConfig);
    }

    @Override
    public PartitionedSearchPhase<Solution_> buildPhase(
            int phaseIndex, 
            HeuristicConfigPolicy<Solution_> solverConfigPolicy,
            BestSolutionRecaller<Solution_> bestSolutionRecaller, 
            Termination<Solution_> solverTermination) {
        HeuristicConfigPolicy<Solution_> phaseConfigPolicy = 
            solverConfigPolicy.createPhaseConfigPolicy();
        ThreadFactory threadFactory = 
            solverConfigPolicy.buildThreadFactory(ChildThreadType.PART_THREAD);
        Termination<Solution_> phaseTermination = 
            buildPhaseTermination(phaseConfigPolicy, solverTermination);
        Integer resolvedActiveThreadCount = 
            resolveActiveThreadCount(phaseConfig.getRunnablePartThreadLimit());
        List<PhaseConfig> phaseConfigList_ = phaseConfig.getPhaseConfigList();
        if (ConfigUtils.isEmptyCollection(phaseConfigList_)) {
            phaseConfigList_ = Arrays.asList(
                new ConstructionHeuristicPhaseConfig(), 
                new LocalSearchPhaseConfig());
        }

        DefaultPartitionedSearchPhase.Builder<Solution_> builder = 
            new DefaultPartitionedSearchPhase.Builder<>(phaseIndex,
                    solverConfigPolicy.getLogIndentation(), phaseTermination, 
                    buildSolutionPartitioner(), threadFactory,
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
                    ConfigUtils.newInstance(phaseConfig, "solutionPartitionerClass", 
                        phaseConfig.getSolutionPartitionerClass());
            ConfigUtils.applyCustomProperties(solutionPartitioner, 
                "solutionPartitionerClass",
                phaseConfig.getSolutionPartitionerCustomProperties(), 
                "solutionPartitionerCustomProperties");
            return (SolutionPartitioner<Solution_>) solutionPartitioner;
        } else {
            if (phaseConfig.getSolutionPartitionerCustomProperties() != null) {
                throw new IllegalStateException(
                        "If there is no solutionPartitionerClass (" 
                            + phaseConfig.getSolutionPartitionerClass()
                            + "), then there can be no solutionPartitionerCustomProperties ("
                            + phaseConfig.getSolutionPartitionerCustomProperties() + ") either.");
            }
            throw new UnsupportedOperationException(
                "Generic partitioner not implemented. Please provide a solutionPartitionerClass.");
        }
    }

    protected Integer resolveActiveThreadCount(String runnablePartThreadLimit) {
        int availableProcessorCount = getAvailableProcessors();
        Integer resolvedActiveThreadCount;
        final boolean threadLimitNullOrAuto =
                runnablePartThreadLimit == null 
                    || runnablePartThreadLimit.equals(ACTIVE_THREAD_COUNT_AUTO);
        if (threadLimitNullOrAuto) {
            // Leave one for the Operating System and 1 for the solver thread
            resolvedActiveThreadCount = Math.max(1, availableProcessorCount - 2);
        } else if (runnablePartThreadLimit.equals(ACTIVE_THREAD_COUNT_UNLIMITED)) {
            resolvedActiveThreadCount = null;
        } else {
            resolvedActiveThreadCount = ConfigUtils.resolvePoolSize("runnablePartThreadLimit",
                    runnablePartThreadLimit, ACTIVE_THREAD_COUNT_AUTO, 
                    ACTIVE_THREAD_COUNT_UNLIMITED);
            if (resolvedActiveThreadCount < 1) {
                throw new IllegalArgumentException("The runnablePartThreadLimit (" 
                    + runnablePartThreadLimit
                    + ") resulted in a resolvedActiveThreadCount (" 
                    + resolvedActiveThreadCount
                    + ") that is lower than 1.");
            }
            if (resolvedActiveThreadCount > availableProcessorCount) {
                LOGGER.debug("The resolvedActiveThreadCount ({}) is higher than "
                        + "the availableProcessorCount ({}), so the JVM will "
                        + "round-robin the CPU instead.", 
                        resolvedActiveThreadCount, availableProcessorCount);
            }
        }
        return resolvedActiveThreadCount;
    }

    protected int getAvailableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }
}
```

---

### Step 11: Update PhaseFactory

**File**: `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/phase/PhaseFactory.java`

Add the partitioned search case to the [`create()`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/phase/PhaseFactory.java:44) method:

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
            throw new IllegalArgumentException(String.format("Unknown %s type: (%s).",
                    PhaseConfig.class.getSimpleName(), phaseConfig.getClass().getName()));
        }
    }
    // ... rest of the class
}
```

---

### Step 12: Create Event Listener Interface

**File**: `core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/event/PartitionedSearchPhaseLifecycleListener.java`

```java
package org.optaplanner.core.impl.partitionedsearch.event;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.impl.partitionedsearch.scope.PartitionedSearchPhaseScope;
import org.optaplanner.core.impl.partitionedsearch.scope.PartitionedSearchStepScope;
import org.optaplanner.core.impl.solver.event.SolverLifecycleListener;

public interface PartitionedSearchPhaseLifecycleListener<Solution_> 
        extends SolverLifecycleListener<Solution_> {
    void phaseStarted(PartitionedSearchPhaseScope<Solution_> phaseScope);
    void stepStarted(PartitionedSearchStepScope<Solution_> stepScope);
    void stepEnded(PartitionedSearchStepScope<Solution_> stepScope);
    void phaseEnded(PartitionedSearchPhaseScope<Solution_> phaseScope);
}
```

---

## Configuration

### XML Configuration

```xml
<solver>
    <partitionedSearch>
        <!-- Required: Custom partitioner class -->
        <solutionPartitionerClass>com.example.MySolutionPartitioner</solutionPartitionerClass>
        
        <!-- Optional: Custom properties for partitioner -->
        <solutionPartitionerCustomProperties>
            <entry key="partitionSize">10</entry>
        </solutionPartitionerCustomProperties>
        
        <!-- Optional: Thread limit (default: AUTO) -->
        <runnablePartThreadLimit>AUTO</runnablePartThreadLimit>
        
        <!-- Optional: Phases to run in each partition (default: CH + LS) -->
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
SolverConfig solverConfig = new SolverConfig()
    .withPhaseConfigList(
        new PartitionedSearchPhaseConfig()
            .withSolutionPartitionerClass(MySolutionPartitioner.class)
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

SolverFactory<MySolution> solverFactory = SolverFactory.create(solverConfig);
Solver<MySolution> solver = solverFactory.buildSolver();
```

### Thread Limit Options

- **`"AUTO"`** (default): Uses `availableProcessors - 2` threads
- **`"UNLIMITED"`**: Uses one thread per partition
- **Number**: Specific thread count (e.g., `"4"`)
- **`null`**: Same as `"AUTO"`

---

## Usage Examples

### Example 1: Simple Entity Partitioning

**SolutionPartitioner Implementation**:
```java
public class MySolutionPartitioner implements SolutionPartitioner<MySolution> {
    private int partitionSize = 10;
    
    public void setPartitionSize(int partitionSize) {
        this.partitionSize = partitionSize;
    }
    
    @Override
    public List<MySolution> splitWorkingSolution(
            ScoreDirector<MySolution> scoreDirector,
            Integer runnablePartThreadLimit) {
        MySolution workingSolution = scoreDirector.getWorkingSolution();
        List<MyEntity> allEntities = workingSolution.getEntityList();
        
        List<MySolution> partitions = new ArrayList<>();
        for (int i = 0; i < allEntities.size(); i += partitionSize) {
            int end = Math.min(i + partitionSize, allEntities.size());
            List<MyEntity> partitionEntities = 
                new ArrayList<>(allEntities.subList(i, end));
            
            MySolution partition = new MySolution();
            partition.setEntityList(partitionEntities);
            partition.setValueList(workingSolution.getValueList());  // Shared facts
            partitions.add(partition);
        }
        return partitions;
    }
}
```

### Example 2: Geographic Partitioning (Vehicle Routing)

```java
public class GeographicPartitioner implements SolutionPartitioner<VehicleRoutingSolution> {
    private int regionCount = 4;
    
    public void setRegionCount(int regionCount) {
        this.regionCount = regionCount;
    }
    
    @Override
    public List<VehicleRoutingSolution> splitWorkingSolution(
            ScoreDirector<VehicleRoutingSolution> scoreDirector,
            Integer runnablePartThreadLimit) {
        VehicleRoutingSolution workingSolution = scoreDirector.getWorkingSolution();
        List<Customer> customers = workingSolution.getCustomerList();
        
        // Cluster customers by location using k-means
        List<List<Customer>> clusters = clusterCustomers(customers, regionCount);
        
        List<VehicleRoutingSolution> partitions = new ArrayList<>();
        for (List<Customer> cluster : clusters) {
            VehicleRoutingSolution partition = new VehicleRoutingSolution();
            partition.setCustomerList(cluster);
            partition.setVehicleList(workingSolution.getVehicleList());
            partition.setDepotList(workingSolution.getDepotList());
            partitions.add(partition);
        }
        return partitions;
    }
    
    private List<List<Customer>> clusterCustomers(List<Customer> customers, int k) {
        // Implement k-means clustering
        // ...
    }
}
```

### Example 3: Time-Based Partitioning (Employee Scheduling)

```java
public class TimeWindowPartitioner implements SolutionPartitioner<EmployeeSchedulingSolution> {
    private int daysPerPartition = 7;
    
    public void setDaysPerPartition(int daysPerPartition) {
        this.daysPerPartition = daysPerPartition;
    }
    
    @Override
    public List<EmployeeSchedulingSolution> splitWorkingSolution(
            ScoreDirector<EmployeeSchedulingSolution> scoreDirector,
            Integer runnablePartThreadLimit) {
        EmployeeSchedulingSolution workingSolution = scoreDirector.getWorkingSolution();
        List<Shift> allShifts = workingSolution.getShiftList();
        
        // Group shifts by time windows
        Map<Integer, List<Shift>> shiftsByWindow = new HashMap<>();
        for (Shift shift : allShifts) {
            int windowIndex = shift.getStartTime().toLocalDate().getDayOfYear() / daysPerPartition;
            shiftsByWindow.computeIfAbsent(windowIndex, k -> new ArrayList<>()).add(shift);
        }
        
        List<EmployeeSchedulingSolution> partitions = new ArrayList<>();
        for (List<Shift> windowShifts : shiftsByWindow.values()) {
            EmployeeSchedulingSolution partition = new EmployeeSchedulingSolution();
            partition.setShiftList(windowShifts);
            partition.setEmployeeList(workingSolution.getEmployeeList());
            partitions.add(partition);
        }
        return partitions;
    }
}
```

---

## Testing

### Unit Tests

**Test Partitioner**:
```java
class MySolutionPartitionerTest {
    @Test
    void testPartitioning() {
        MySolutionPartitioner partitioner = new MySolutionPartitioner();
        partitioner.setPartitionSize(10);
        
        MySolution solution = createTestSolution(30);  // 30 entities
        
        ScoreDirector<MySolution> scoreDirector = mockScoreDirector(solution);
        List<MySolution> partitions = partitioner.splitWorkingSolution(
            scoreDirector, null);
        
        assertThat(partitions).hasSize(3);
        assertThat(partitions).allSatisfy(p -> 
            assertThat(p.getEntityList()).hasSize(10));
    }
}
```

**Integration Test** (from [`DefaultPartitionedSearchPhaseTest.java`](core/optaplanner-core-impl/src/test/java/org/optaplanner/core/impl/partitionedsearch/DefaultPartitionedSearchPhaseTest.java:57)):
```java
@Test
void partCount() {
    final int partSize = 3;
    final int partCount = 7;
    
    SolverFactory<TestdataSolution> solverFactory = createSolverFactory(
        false, SolverConfig.MOVE_THREAD_COUNT_NONE, partSize);
    DefaultSolver<TestdataSolution> solver = 
        (DefaultSolver<TestdataSolution>) solverFactory.buildSolver();
    
    PartitionedSearchPhase<TestdataSolution> phase = 
        (PartitionedSearchPhase<TestdataSolution>) solver.getPhaseList().get(0);
    
    phase.addPhaseLifecycleListener(new PhaseLifecycleListenerAdapter<>() {
        @Override
        public void phaseStarted(AbstractPhaseScope<TestdataSolution> phaseScope) {
            assertThat(((PartitionedSearchPhaseScope) phaseScope).getPartCount())
                .isEqualTo(Integer.valueOf(partCount));
        }
    });
    
    solver.solve(createSolution(partCount * partSize, 2));
}
```

---

## Key Implementation Details

### Thread Safety

- **[`PartitionQueue`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionQueue.java:42)**: Uses [`ConcurrentHashMap`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionQueue.java:60) and [`ArrayBlockingQueue`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionQueue.java:59) for thread-safe operations
- **Event Indexing**: Each partition maintains an atomic event index to track ordering
- **Move Skipping**: Outdated moves are skipped to ensure only the latest partition state is applied

### Termination

- **Child Thread Termination**: Uses [`ChildThreadPlumbingTermination`](../../../optaplanner/core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/solver/termination/ChildThreadPlumbingTermination.java) to signal termination to all partition threads
- **Composite Termination**: Each partition solver uses [`OrCompositeTermination`](../../../optaplanner/core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/solver/termination/OrCompositeTermination.java) combining child thread termination and phase termination

### Score Calculation

- **Independent Calculation**: Each partition calculates its own score
- **Merge via Moves**: [`PartitionChangeMove`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionChangeMove.java:43) applies partition changes to main solution
- **Rebasing**: Moves are rebased from child to parent score director using [`lookUpWorkingObject()`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionChangeMove.java:115)

### Resource Management

- **Thread Pool**: Fixed-size thread pool with one thread per partition
- **Semaphore**: Optional semaphore limits runnable threads to prevent CPU exhaustion
- **Cleanup**: Executor service is properly shut down in finally block

### Error Handling

- **Exception Propagation**: Exceptions in partition threads are captured and rethrown in main thread
- **Graceful Shutdown**: Child threads are terminated even if one fails
- **Logging**: Detailed logging for debugging partition behavior

---

## Summary

The Partitioned Search feature in OptaPlanner is a sophisticated parallel optimization technique that:

1. **Divides** large problems into independent partitions via custom [`SolutionPartitioner`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/partitioner/SolutionPartitioner.java:37)
2. **Solves** each partition concurrently using separate [`PartitionSolver`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/PartitionSolver.java:36) instances
3. **Communicates** results via thread-safe [`PartitionQueue`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionQueue.java:42)
4. **Merges** improvements using [`PartitionChangeMove`](core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionChangeMove.java:43)
5. **Manages** resources with configurable thread limits and proper cleanup

The implementation requires careful attention to thread safety, termination handling, and score rebasing to ensure correct and efficient parallel optimization.
