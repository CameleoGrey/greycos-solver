# Multistage Move Feature - Implementation Plan

## Executive Summary

This document provides a detailed implementation plan for the **Multistage Move** feature in Greycos Solver. Multistage moves enable the solver to make coordinated, multi-step changes in a single atomic operation, allowing it to escape local optima and score traps where single-variable moves are ineffective.

**Key Insight from Research**: Multistage moves are fundamentally different from union moves. Union moves combine multiple move types horizontally (OR composition), while multistage moves compose multiple dependent steps vertically (AND composition) into one atomic move.

---

## Table of Contents

1. [Concept Overview](#1-concept-overview)
2. [Architecture Design](#2-architecture-design)
3. [Implementation Components](#3-implementation-components)
4. [Implementation Phases](#4-implementation-phases)
5. [API Design](#5-api-design)
6. [Configuration Examples](#6-configuration-examples)
7. [Testing Strategy](#7-testing-strategy)
8. [Performance Considerations](#8-performance-considerations)
9. [Migration Path](#9-migration-path)
10. [Open Questions](#10-open-questions)

---

## 1. Concept Overview

### 1.1 What is a Multistage Move?

A **multistage move** is a composite move that executes multiple dependent steps atomically:

```
One Logical Move:
  Stage 1 → Stage 2 → Stage 3 → ... → Stage N
         ↓
    Final solution evaluated (single score delta)
```

**Key Characteristics**:
- **Atomic**: All stages execute as one operation; intermediate states are never scored
- **Sequential**: Later stages depend on earlier stages
- **Coordinated**: Changes are designed to work together to achieve a specific goal
- **Escape Mechanism**: Enables jumps out of local optima that require multiple simultaneous adjustments

### 1.2 Why Multistage Moves Matter

Based on TimeFold research, multistage moves address critical limitations of single-variable moves:

| Problem | Single-Variable Moves | Multistage Moves |
|---------|----------------------|------------------|
| **Local Optima** | Get stuck in score traps | Can escape with coordinated jumps |
| **Constraint Violations** | May create temporary infeasibility | Avoid invalid intermediate states |
| **Complex Structures** | Cannot handle routes/chains efficiently | Can restructure entire segments |
| **Solution Quality** | May plateau early | Can reach better solutions |

### 1.3 Real-World Use Cases

From research and common optimization problems:

1. **Vehicle Routing (VRP)**: k-opt moves that remove and reconnect multiple route segments
2. **Scheduling**: Swap moves that exchange time slots without creating conflicts
3. **Workload Balancing**: Pillar moves that reassign entire groups of tasks
4. **Chained Planning**: Sub-chain moves that relocate connected entities
5. **Ruin and Recreate**: Large neighborhood search within a single move

### 1.4 Multistage vs Union Move (Critical Distinction)

| Aspect | Union Move | Multistage Move |
|--------|-----------|-----------------|
| **Composition** | Horizontal (OR) | Vertical (AND) |
| **Purpose** | Choose between move types | Define how a move is built |
| **Dependency** | None between moves | Strong between stages |
| **Atomicity** | Each move atomic | Whole sequence atomic |
| **Search Effect** | Wider exploration | Larger jumps |
| **Typical Use** | Mix neighborhoods | Escape traps/encode structure |

**Example**:
```xml
<!-- Union: Choose one of these move types -->
<unionMoveSelector>
  <changeMoveSelector/>
  <swapMoveSelector/>
</unionMoveSelector>

<!-- Multistage: One move built from multiple stages -->
<multistageMoveSelector>
  <stageProviderClass>com.example.MyStageProvider</stageProviderClass>
</multistageMoveSelector>
```

---

## 2. Architecture Design

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  Solver Configuration                       │
│  (XML, Java API, or Programmatic Configuration)             │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              MultistageMoveSelectorConfig                  │
│  - stageProviderClass: Class<?>                            │
│  - entityClass: Class<?> (optional)                         │
│  - variableName: String (optional)                          │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│         GreycosSolverEnterpriseService (Enterprise)        │
│  - buildBasicMultistageMoveSelectorFactory()                │
│  - buildListMultistageMoveSelectorFactory()                 │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│         MultistageMoveSelectorFactory                       │
│  - Creates MultistageMoveSelector from config              │
│  - Instantiates StageProvider                               │
│  - Builds stage move selectors                             │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              MultistageMoveSelector                         │
│  - Manages stage transitions                                 │
│  - Coordinates move generation                              │
│  - Ensures atomic execution                                 │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                 StageProvider Interface                     │
│  - createStages(HeuristicConfigPolicy)                     │
│  - Returns List<MoveSelector> for each stage               │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              Stage Move Selectors                           │
│  - ChangeMoveSelector, SwapMoveSelector, etc.              │
│  - Each stage generates moves for its specific purpose      │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Component Relationships

```
MultistageMoveSelector
    ├── StageProvider (user-provided)
    │   ├── Stage 1: MoveSelector (e.g., PillarChangeMoveSelector)
    │   ├── Stage 2: MoveSelector (e.g., SwapMoveSelector)
    │   └── Stage 3: MoveSelector (e.g., ChangeMoveSelector)
    ├── CurrentStageIndex (tracks active stage)
    ├── MoveComposer (combines stage moves into composite)
    └── StageTransitionLogic (decides when to advance stages)
```

### 2.3 Data Flow

```
1. Configuration Loading
   XML → MultistageMoveSelectorConfig

2. Factory Creation
   Config → GreycosSolverEnterpriseService
           → MultistageMoveSelectorFactory

3. Stage Provider Instantiation
   Factory → StageProvider (via reflection)

4. Stage Selector Construction
   StageProvider → List<MoveSelector> (stages)

5. Move Generation (during solving)
   For each step:
     a. Select current stage
     b. Generate move from stage selector
     c. Compose with moves from other stages (if needed)
     d. Return composite move

6. Move Execution
   CompositeMove.doMove() → executes all stages atomically
   → ScoreDirector calculates single delta
```

---

## 3. Implementation Components

### 3.1 Core Components

#### 3.1.1 StageProvider Interface

**Location**: `core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/generic/StageProvider.java`

**Purpose**: User-defined interface that creates move selectors for each stage.

```java
package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import org.jspecify.annotations.NonNull;

/**
 * Provider that creates move selectors for each stage of a multistage move.
 *
 * <p>A multistage move consists of multiple stages, where each stage
 * generates moves for a specific purpose. The StageProvider defines
 * what stages exist and how they are constructed.
 *
 * <p>Example stages for a VRP problem:
 * <ul>
 *   <li>Stage 1: Select a sub-route (pillar selection)</li>
 *   <li>Stage 2: Remove it (ruin)</li>
 *   <li>Stage 3: Reinsert using heuristic (recreate)</li>
 * </ul>
 *
 * @param <Solution_> the solution type
 */
public interface StageProvider<Solution_> {

    /**
     * Creates the move selectors for each stage.
     *
     * <p>The order of the list matters: stages are executed sequentially.
     * Each stage's move selector generates moves that contribute to the
     * overall multistage move.
     *
     * @param configPolicy the configuration policy, never null
     * @return a list of move selectors, one for each stage, never null
     */
    @NonNull
    List<MoveSelector<Solution_>> createStages(
            @NonNull HeuristicConfigPolicy<Solution_> configPolicy);

    /**
     * Returns the number of stages.
     *
     * @return the stage count, must be at least 1
     */
    int getStageCount();
}
```

#### 3.1.2 MultistageMoveSelector

**Location**: `core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/generic/MultistageMoveSelector.java`

**Purpose**: Manages multistage move generation and execution.

```java
package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.selector.move.AbstractSelector;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;
import org.jspecify.annotations.NonNull;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * A move selector that generates multistage moves.
 *
 * <p>A multistage move consists of multiple coordinated changes
 * that are executed atomically. This selector manages the stage
 * transitions and composes the final composite move.
 *
 * <p>Example: Ruin and Recreate
 * <pre>
 * Stage 1: Select subset of entities
 * Stage 2: Unassign them (ruin)
 * Stage 3: Reassign using heuristic (recreate)
 * </pre>
 *
 * @param <Solution_> the solution type
 */
public class MultistageMoveSelector<Solution_> extends AbstractSelector<Solution_>
        implements MoveSelector<Solution_> {

    private final StageProvider<Solution_> stageProvider;
    private final List<MoveSelector<Solution_>> stageSelectors;
    private final boolean randomSelection;

    private ScoreDirector<Solution_> scoreDirector;
    private long workingRandom;

    public MultistageMoveSelector(
            @NonNull StageProvider<Solution_> stageProvider,
            @NonNull List<MoveSelector<Solution_>> stageSelectors,
            boolean randomSelection) {
        this.stageProvider = Objects.requireNonNull(stageProvider);
        this.stageSelectors = List.copyOf(stageSelectors);
        this.randomSelection = randomSelection;

        if (stageSelectors.isEmpty()) {
            throw new IllegalArgumentException(
                "Stage selectors list cannot be empty");
        }
    }

    @Override
    public void stepStarted(@NonNull AbstractStepScope<Solution_> stepScope) {
        this.scoreDirector = stepScope.getScoreDirector();
        this.workingRandom = stepScope.getWorkingRandom();
        for (MoveSelector<Solution_> stageSelector : stageSelectors) {
            stageSelector.stepStarted(stepScope);
        }
    }

    @Override
    public void stepEnded(@NonNull AbstractStepScope<Solution_> stepScope) {
        for (MoveSelector<Solution_> stageSelector : stageSelectors) {
            stageSelector.stepEnded(stepScope);
        }
        this.scoreDirector = null;
    }

    @Override
    public long getSize() {
        // Size is product of all stage sizes (Cartesian product)
        return stageSelectors.stream()
                .mapToLong(MoveSelector::getSize)
                .reduce(1L, (a, b) -> a * b);
    }

    @Override
    public boolean isNeverEnding() {
        // Never ending if any stage is never ending
        return stageSelectors.stream()
                .anyMatch(MoveSelector::isNeverEnding);
    }

    @Override
    public @NonNull Iterator<Move<Solution_>> iterator() {
        if (randomSelection) {
            return new RandomMultistageMoveIterator<>(
                    stageSelectors, workingRandom);
        } else {
            return new SequentialMultistageMoveIterator<>(
                    stageSelectors);
        }
    }

    // Getters for testing
    StageProvider<Solution_> getStageProvider() {
        return stageProvider;
    }

    List<MoveSelector<Solution_>> getStageSelectors() {
        return stageSelectors;
    }
}
```

#### 3.1.3 MultistageMoveIterator Implementations

**Location**: `core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/generic/`

**SequentialMultistageMoveIterator.java**:
```java
package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import ai.greycos.solver.core.impl.heuristic.move.CompositeMove;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.ArrayList;
import org.jspecify.annotations.NonNull;

/**
 * Iterator for multistage moves in sequential order.
 *
 * <p>Generates all combinations of stage moves in a deterministic
 * sequence (Cartesian product).
 */
public class SequentialMultistageMoveIterator<Solution_>
        implements Iterator<Move<Solution_>> {

    private final List<MoveSelector<Solution_>> stageSelectors;
    private final List<Iterator<Move<Solution_>>> stageIterators;
    private final int stageCount;

    private boolean hasNext = true;

    public SequentialMultistageMoveIterator(
            @NonNull List<MoveSelector<Solution_>> stageSelectors) {
        this.stageSelectors = List.copyOf(stageSelectors);
        this.stageCount = stageSelectors.size();
        this.stageIterators = new ArrayList<>(stageCount);

        // Initialize iterators for each stage
        for (MoveSelector<Solution_> selector : stageSelectors) {
            stageIterators.add(selector.iterator());
        }

        // Check if first stage has moves
        hasNext = stageIterators.get(0).hasNext();
    }

    @Override
    public boolean hasNext() {
        return hasNext;
    }

    @Override
    public Move<Solution_> next() {
        if (!hasNext) {
            throw new NoSuchElementException();
        }

        // Collect one move from each stage
        List<Move<Solution_>> moves = new ArrayList<>(stageCount);
        for (Iterator<Move<Solution_>> iterator : stageIterators) {
            moves.add(iterator.next());
        }

        // Advance iterators (Cartesian product logic)
        advanceIterators();

        // Compose into a single composite move
        return CompositeMove.buildMoveComposition(moves);
    }

    private void advanceIterators() {
        // Start from the last stage and work backwards
        for (int i = stageCount - 1; i >= 0; i--) {
            if (stageIterators.get(i).hasNext()) {
                // This stage still has moves, we're done
                return;
            } else {
                // This stage is exhausted, reset it and advance previous stage
                stageIterators.set(i, stageSelectors.get(i).iterator());
                if (!stageIterators.get(i).hasNext()) {
                    // Previous stage also exhausted, continue up
                    continue;
                }
            }
        }

        // If we get here, all stages are exhausted
        hasNext = false;
    }
}
```

**RandomMultistageMoveIterator.java**:
```java
package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import ai.greycos.solver.core.impl.heuristic.move.CompositeMove;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;

import java.util.List;
import java.util.Random;
import org.jspecify.annotations.NonNull;

/**
 * Iterator for multistage moves in random order.
 *
 * <p>Generates random combinations of stage moves.
 * Never ending as long as at least one stage is never ending.
 */
public class RandomMultistageMoveIterator<Solution_>
        implements java.util.Iterator<Move<Solution_>> {

    private final List<MoveSelector<Solution_>> stageSelectors;
    private final Random random;

    public RandomMultistageMoveIterator(
            @NonNull List<MoveSelector<Solution_>> stageSelectors,
            long workingRandom) {
        this.stageSelectors = List.copyOf(stageSelectors);
        this.random = new Random(workingRandom);
    }

    @Override
    public boolean hasNext() {
        // Random iterator is never ending
        return true;
    }

    @Override
    public Move<Solution_> next() {
        List<Move<Solution_>> moves = new ArrayList<>(stageSelectors.size());

        // Get one random move from each stage
        for (MoveSelector<Solution_> selector : stageSelectors) {
            Iterator<Move<Solution_>> iterator = selector.iterator();
            if (!iterator.hasNext()) {
                throw new IllegalStateException(
                    "Stage selector " + selector + " has no moves");
            }

            // Skip to random position
            long skip = (long) (random.nextDouble() * selector.getSize());
            for (long i = 0; i < skip && iterator.hasNext(); i++) {
                iterator.next();
            }

            if (iterator.hasNext()) {
                moves.add(iterator.next());
            } else {
                // Fallback: take the first available move
                iterator = selector.iterator();
                moves.add(iterator.next());
            }
        }

        return CompositeMove.buildMoveComposition(moves);
    }
}
```

#### 3.1.4 MultistageMoveSelectorFactory

**Location**: `core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/generic/MultistageMoveSelectorFactory.java`

**Purpose**: Factory that creates MultistageMoveSelector from configuration.

```java
package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import ai.greycos.solver.core.config.util.ConfigUtils;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelectorFactory;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Factory for creating {@link MultistageMoveSelector}.
 */
public class MultistageMoveSelectorFactory<Solution_>
        extends AbstractMoveSelectorFactory<Solution_, MultistageMoveSelectorConfig> {

    public MultistageMoveSelectorFactory(
            @NonNull MultistageMoveSelectorConfig config) {
        super(config);
    }

    @Override
    protected MoveSelector<Solution_> buildBaseMoveSelector(
            @NonNull HeuristicConfigPolicy<Solution_> configPolicy,
            @NonNull SelectionCacheType minimumCacheType,
            boolean randomSelection) {

        // Instantiate the stage provider
        Class<?> stageProviderClass = config.getStageProviderClass();
        if (stageProviderClass == null) {
            throw new IllegalArgumentException(
                "MultistageMoveSelectorConfig must specify a stageProviderClass");
        }

        @SuppressWarnings("unchecked")
        StageProvider<Solution_> stageProvider =
            (StageProvider<Solution_>) ConfigUtils.newInstance(
                config, "stageProviderClass", stageProviderClass);

        // Create stage selectors
        List<MoveSelector<Solution_>> stageSelectors =
            stageProvider.createStages(configPolicy);

        // Validate stage count
        if (stageSelectors.isEmpty()) {
            throw new IllegalStateException(
                "StageProvider " + stageProviderClass.getName()
                + " returned empty stage list");
        }

        return new MultistageMoveSelector<>(
            stageProvider, stageSelectors, randomSelection);
    }

    @Override
    protected boolean isBaseInherentlyCached() {
        return false;
    }
}
```

#### 3.1.5 ListMultistageMoveSelector (For List Variables)

**Location**: `core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/generic/list/ListMultistageMoveSelector.java`

Similar to basic MultistageMoveSelector but optimized for list planning variables.

```java
package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import ai.greycos.solver.core.impl.heuristic.selector.move.generic.StageProvider;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Multistage move selector for list planning variables.
 *
 * <p>Specialized for list-based problems (e.g., vehicle routing,
 * task scheduling with sequences).
 */
public class ListMultistageMoveSelector<Solution_>
        extends ai.greycos.solver.core.impl.heuristic.selector.move.generic
                .MultistageMoveSelector<Solution_> {

    public ListMultistageMoveSelector(
            @NonNull StageProvider<Solution_> stageProvider,
            @NonNull List<MoveSelector<Solution_>> stageSelectors,
            boolean randomSelection) {
        super(stageProvider, stageSelectors, randomSelection);
    }
}
```

**ListMultistageMoveSelectorFactory.java**:
```java
package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;
import ai.greycos.solver.core.config.util.ConfigUtils;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelectorFactory;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Factory for creating {@link ListMultistageMoveSelector}.
 */
public class ListMultistageMoveSelectorFactory<Solution_>
        extends AbstractMoveSelectorFactory<Solution_, ListMultistageMoveSelectorConfig> {

    public ListMultistageMoveSelectorFactory(
            @NonNull ListMultistageMoveSelectorConfig config) {
        super(config);
    }

    @Override
    protected MoveSelector<Solution_> buildBaseMoveSelector(
            @NonNull HeuristicConfigPolicy<Solution_> configPolicy,
            @NonNull SelectionCacheType minimumCacheType,
            boolean randomSelection) {

        Class<?> stageProviderClass = config.getStageProviderClass();
        if (stageProviderClass == null) {
            throw new IllegalArgumentException(
                "ListMultistageMoveSelectorConfig must specify a stageProviderClass");
        }

        @SuppressWarnings("unchecked")
        StageProvider<Solution_> stageProvider =
            (StageProvider<Solution_>) ConfigUtils.newInstance(
                config, "stageProviderClass", stageProviderClass);

        List<MoveSelector<Solution_>> stageSelectors =
            stageProvider.createStages(configPolicy);

        return new ListMultistageMoveSelector<>(
            stageProvider, stageSelectors, randomSelection);
    }
}
```

### 3.2 Enterprise Service Integration

Update [`GreycosSolverEnterpriseService.java`](../../core/src/main/java/ai/greycos/solver/core/enterprise/GreycosSolverEnterpriseService.java) to replace the `UnsupportedOperationException` with actual implementations:

```java
@Override
public <Solution_>
    AbstractMoveSelectorFactory<Solution_, MultistageMoveSelectorConfig>
        buildBasicMultistageMoveSelectorFactory(
            MultistageMoveSelectorConfig moveSelectorConfig) {
    return new MultistageMoveSelectorFactory<>(moveSelectorConfig);
}

@Override
public <Solution_>
    AbstractMoveSelectorFactory<Solution_, ListMultistageMoveSelectorConfig>
        buildListMultistageMoveSelectorFactory(
            ListMultistageMoveSelectorConfig moveSelectorConfig) {
    return new ListMultistageMoveSelectorFactory<>(moveSelectorConfig);
}
```

### 3.3 XSD Schema Updates

Update [`solver.xsd`](../../core/src/main/resources/solver.xsd) to include multistage move selector definitions:

```xml
<!-- Add within moveSelectorType complexType -->
<xsd:element name="multistageMoveSelector" type="multistageMoveSelectorType"/>

<xsd:complexType name="multistageMoveSelectorType">
    <xsd:complexContent>
        <xsd:extension base="moveSelectorConfigType">
            <xsd:sequence>
                <xsd:element name="stageProviderClass" type="javaClassType"
                             minOccurs="1" maxOccurs="1"/>
                <xsd:element name="entityClass" type="javaClassType"
                             minOccurs="0" maxOccurs="1"/>
                <xsd:element name="variableName" type="xsd:string"
                             minOccurs="0" maxOccurs="1"/>
            </xsd:sequence>
        </xsd:extension>
    </xsd:complexContent>
</xsd:complexType>

<!-- For list variables -->
<xsd:element name="listMultistageMoveSelector" type="listMultistageMoveSelectorType"/>

<xsd:complexType name="listMultistageMoveSelectorType">
    <xsd:complexContent>
        <xsd:extension base="moveSelectorConfigType">
            <xsd:sequence>
                <xsd:element name="stageProviderClass" type="javaClassType"
                             minOccurs="1" maxOccurs="1"/>
            </xsd:sequence>
        </xsd:extension>
    </xsd:complexContent>
</xsd:complexType>
```

---

## 4. Implementation Phases

### Phase 1: Core Infrastructure (Week 1-2)

**Objective**: Establish the foundational components for multistage moves.

**Tasks**:

1. **Create StageProvider Interface**
   - Location: `core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/generic/StageProvider.java`
   - Define `createStages()` and `getStageCount()` methods
   - Add comprehensive JavaDoc

2. **Implement MultistageMoveSelector**
   - Location: `core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/generic/MultistageMoveSelector.java`
   - Implement core move generation logic
   - Handle step lifecycle (stepStarted/stepEnded)
   - Implement size calculation and never-ending detection

3. **Create Iterator Implementations**
   - `SequentialMultistageMoveIterator.java`: Cartesian product iteration
   - `RandomMultistageMoveIterator.java`: Random move generation
   - Ensure proper resource cleanup

4. **Implement MultistageMoveSelectorFactory**
   - Location: `core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/generic/MultistageMoveSelectorFactory.java`
   - Handle StageProvider instantiation via reflection
   - Validate configuration
   - Build stage selectors

5. **Unit Tests**
   - Test StageProvider interface
   - Test MultistageMoveSelector with mock stages
   - Test iterator behavior (sequential and random)
   - Test factory instantiation

**Acceptance Criteria**:
- All core components compile and pass unit tests
- StageProvider can be instantiated and creates stage selectors
- MultistageMoveSelector generates composite moves correctly
- Iterators handle edge cases (empty stages, never-ending stages)

**Deliverables**:
- Source code for all core components
- Unit test suite with >80% coverage
- Design documentation

---

### Phase 2: List Variable Support (Week 3)

**Objective**: Extend multistage moves to support list planning variables.

**Tasks**:

1. **Create ListMultistageMoveSelector**
   - Location: `core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/generic/list/ListMultistageMoveSelector.java`
   - Extend base MultistageMoveSelector
   - Optimize for list-specific operations

2. **Implement ListMultistageMoveSelectorFactory**
   - Location: `core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/generic/list/ListMultistageMoveSelectorFactory.java`
   - Follow same pattern as basic factory
   - Handle list-specific configuration

3. **Unit Tests**
   - Test list multistage move selector
   - Test with list-based problems (e.g., VRP)
   - Verify composite move composition for list variables

**Acceptance Criteria**:
- List multistage moves work correctly
- Performance is comparable to basic multistage moves
- All unit tests pass

**Deliverables**:
- List multistage move implementation
- Unit tests

---

### Phase 3: Enterprise Service Integration (Week 4)

**Objective**: Integrate multistage moves with the enterprise service framework.

**Tasks**:

1. **Update GreycosSolverEnterpriseService**
   - Remove `UnsupportedOperationException` from `buildBasicMultistageMoveSelectorFactory()`
   - Remove `UnsupportedOperationException` from `buildListMultistageMoveSelectorFactory()`
   - Return actual factory instances

2. **Update DefaultGreycosSolverEnterpriseService**
   - Replace exceptions with implementations or keep as community fallback
   - Ensure proper error messages for community edition

3. **Integration Tests**
   - Test enterprise service loads correctly
   - Test license enforcement
   - Test community edition fallback behavior

**Acceptance Criteria**:
- Enterprise edition can use multistage moves
- Community edition throws appropriate exceptions
- License validation works correctly

**Deliverables**:
- Updated enterprise service implementations
- Integration tests

---

### Phase 4: XSD and Configuration (Week 5)

**Objective**: Enable XML configuration for multistage moves.

**Tasks**:

1. **Update solver.xsd**
   - Add `multistageMoveSelector` element definition
   - Add `listMultistageMoveSelector` element definition
   - Ensure proper validation constraints

2. **Update JAXB Bindings**
   - Ensure proper XML binding for new elements
   - Test XML parsing

3. **Configuration Tests**
   - Test XML configuration parsing
   - Test Java API configuration
   - Test programmatic configuration

**Acceptance Criteria**:
- XML configuration works correctly
- All configuration methods produce equivalent results
- XSD validates properly

**Deliverables**:
- Updated XSD schema
- Configuration tests

---

### Phase 5: Example Implementations (Week 6)

**Objective**: Provide concrete examples of multistage moves for common problems.

**Tasks**:

1. **Ruin and Recreate Example**
   - StageProvider that selects entities, ruins, and recreates
   - Demonstrate with simple constraint satisfaction problem

2. **Pillar Move Example**
   - StageProvider for group-based moves
   - Demonstrate with workload balancing problem

3. **k-Opt Example (for VRP)**
   - StageProvider for route optimization
   - Demonstrate with vehicle routing problem

4. **Documentation**
   - Write user guide for multistage moves
   - Provide configuration examples
   - Include best practices

**Acceptance Criteria**:
- All examples compile and run
- Examples demonstrate clear improvement over single-variable moves
- Documentation is clear and comprehensive

**Deliverables**:
- Example StageProvider implementations
- Example solver configurations
- User guide documentation

---

### Phase 6: Performance Optimization (Week 7)

**Objective**: Optimize multistage move performance for production use.

**Tasks**:

1. **Profiling**
   - Profile multistage move generation
   - Identify bottlenecks
   - Compare with union moves

2. **Optimizations**
   - Optimize iterator implementations
   - Reduce object allocations
   - Cache intermediate results where appropriate

3. **Benchmarking**
   - Create benchmarks for common use cases
   - Compare performance with and without multistage moves
   - Document performance characteristics

**Acceptance Criteria**:
- Performance is acceptable for production use
- Benchmarks show clear benefits for appropriate problems
- No memory leaks or excessive allocations

**Deliverables**:
- Optimized implementation
- Benchmark results
- Performance documentation

---

### Phase 7: Testing and Validation (Week 8)

**Objective**: Comprehensive testing to ensure quality and correctness.

**Tasks**:

1. **Unit Tests**
   - Achieve >90% code coverage
   - Test all edge cases
   - Test error handling

2. **Integration Tests**
   - Test with real solver configurations
   - Test with various problem types
   - Test with different metaheuristics (Tabu, SA, etc.)

3. **Regression Tests**
   - Ensure existing functionality is not broken
   - Run full test suite
   - Fix any regressions

4. **Documentation Review**
   - Review all documentation
   - Ensure accuracy
   - Add missing information

**Acceptance Criteria**:
- All tests pass
- Code coverage >90%
- No regressions
- Documentation is complete and accurate

**Deliverables**:
- Complete test suite
- Test reports
- Final documentation

---

## 5. API Design

### 5.1 StageProvider API

```java
public interface StageProvider<Solution_> {

    /**
     * Creates move selectors for each stage.
     *
     * @param configPolicy the configuration policy
     * @return list of move selectors, one per stage
     */
    @NonNull
    List<MoveSelector<Solution_>> createStages(
            @NonNull HeuristicConfigPolicy<Solution_> configPolicy);

    /**
     * Returns the number of stages.
     *
     * @return stage count (must be >= 1)
     */
    int getStageCount();
}
```

### 5.2 Configuration API

#### XML Configuration

```xml
<localSearchPhase>
  <moveSelector>
    <multistageMoveSelector>
      <stageProviderClass>com.example.RuinRecreateStageProvider</stageProviderClass>
      <entityClass>com.example.Task</entityClass>
      <variableName>assignedResource</variableName>
      <selectionOrder>RANDOM</selectionOrder>
    </multistageMoveSelector>
  </moveSelector>
</localSearchPhase>
```

#### Java API Configuration

```java
SolverConfig<Solution> config = SolverConfig.builder(solverClass)
    .withPhase(LocalSearchPhaseConfig.builder()
        .withMoveSelectorConfig(
            MultistageMoveSelectorConfig.builder()
                .withStageProviderClass(MyStageProvider.class)
                .withEntityClass(MyEntity.class)
                .withVariableName("planningVariable")
                .withSelectionOrder(SelectionOrder.RANDOM)
                .build())
        .build())
    .build();
```

#### Programmatic Configuration

```java
StageProvider<MySolution> stageProvider = new MyStageProvider();

MultistageMoveSelector<MySolution> multistageSelector =
    new MultistageMoveSelector<>(
        stageProvider,
        stageProvider.createStages(configPolicy),
        true // random selection
    );
```

### 5.3 Custom StageProvider Example

```java
/**
 * Stage provider for ruin and recreate moves.
 *
 * <p>Stages:
 * <ol>
 *   <li>Stage 1: Select subset of entities to ruin</li>
 *   <li>Stage 2: Unassign them (ruin)</li>
 *   <li>Stage 3: Reassign using construction heuristic (recreate)</li>
 * </ol>
 */
public class RuinRecreateStageProvider<Solution_>
        implements StageProvider<Solution_> {

    private static final int RUIN_SIZE = 10;

    @Override
    public List<MoveSelector<Solution_>> createStages(
            HeuristicConfigPolicy<Solution_> configPolicy) {

        List<MoveSelector<Solution_>> stages = new ArrayList<>();

        // Stage 1: Select entities to ruin
        EntitySelectorConfig entitySelectorConfig = new EntitySelectorConfig()
            .withEntityClass(Task.class)
            .withSelectionOrder(SelectionOrder.RANDOM);

        EntitySelector<Solution_> entitySelector =
            EntitySelectorFactory.create(entitySelectorConfig)
                .buildEntitySelector(configPolicy, ...);

        // Stage 2: Create ruin moves (change to null)
        ChangeMoveSelectorConfig ruinConfig = new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(entitySelectorConfig)
            .withValueSelectorConfig(new ValueSelectorConfig()
                .withNullSelection(true));

        stages.add(new ChangeMoveSelectorFactory<>(ruinConfig)
            .buildMoveSelector(configPolicy, ...));

        // Stage 3: Recreate using construction heuristic
        // This would use a custom move that applies the heuristic
        stages.add(new RecreateMoveSelector<>(entitySelector));

        return stages;
    }

    @Override
    public int getStageCount() {
        return 3;
    }
}
```

---

## 6. Configuration Examples

### 6.1 Simple Two-Stage Move

```xml
<localSearchPhase>
  <moveSelector>
    <unionMoveSelector>
      <!-- Regular moves -->
      <changeMoveSelector/>

      <!-- Multistage move for escaping local optima -->
      <multistageMoveSelector>
        <stageProviderClass>
          com.example.SwapAndRefineStageProvider
        </stageProviderClass>
        <entityClass>com.example.Lecture</entityClass>
        <variableName>room</variableName>
      </multistageMoveSelector>
    </unionMoveSelector>
  </moveSelector>
</localSearchPhase>
```

### 6.2 Ruin and Recreate for VRP

```xml
<localSearchPhase>
  <moveSelector>
    <listMultistageMoveSelector>
      <stageProviderClass>
        com.vrp.RuinRecreateStageProvider
      </stageProviderClass>
      <selectionOrder>RANDOM</selectionOrder>
    </listMultistageMoveSelector>
  </moveSelector>
</localSearchPhase>
```

### 6.3 Adaptive Multi-Stage Strategy

```java
public class AdaptiveStageProvider<Solution_>
        implements StageProvider<Solution_> {

    private final int maxStages;

    public AdaptiveStageProvider(int maxStages) {
        this.maxStages = maxStages;
    }

    @Override
    public List<MoveSelector<Solution_>> createStages(
            HeuristicConfigPolicy<Solution_> configPolicy) {

        List<MoveSelector<Solution_>> stages = new ArrayList<>();

        // Stage 1: Aggressive exploration (pillar moves)
        stages.add(createPillarMoveSelector(configPolicy));

        // Stage 2: Medium-grained moves (swap)
        stages.add(createSwapMoveSelector(configPolicy));

        // Stage 3: Fine-grained refinement (change)
        stages.add(createChangeMoveSelector(configPolicy));

        return stages;
    }

    @Override
    public int getStageCount() {
        return maxStages;
    }
}
```

---

## 7. Testing Strategy

### 7.1 Unit Tests

#### StageProvider Tests

```java
class StageProviderTest {

    @Test
    void testCreateStages_returnsNonEmptyList() {
        StageProvider<TestSolution> provider = new TestStageProvider();
        List<MoveSelector<TestSolution>> stages =
            provider.createStages(mockConfigPolicy);

        assertFalse(stages.isEmpty());
        assertEquals(3, stages.size());
    }

    @Test
    void testGetStageCount_returnsCorrectCount() {
        StageProvider<TestSolution> provider = new TestStageProvider();
        assertEquals(3, provider.getStageCount());
    }
}
```

#### MultistageMoveSelector Tests

```java
class MultistageMoveSelectorTest {

    @Test
    void testGetSize_returnsProductOfStageSizes() {
        List<MoveSelector<TestSolution>> stages = List.of(
            createMockSelector(10),
            createMockSelector(5),
            createMockSelector(2)
        );

        MultistageMoveSelector<TestSolution> selector =
            new MultistageMoveSelector<>(mockProvider, stages, false);

        assertEquals(100, selector.getSize()); // 10 * 5 * 2
    }

    @Test
    void testIsNeverEnding_returnsTrueIfAnyStageNeverEnding() {
        List<MoveSelector<TestSolution>> stages = List.of(
            createMockSelector(false),
            createMockSelector(true),  // Never ending
            createMockSelector(false)
        );

        MultistageMoveSelector<TestSolution> selector =
            new MultistageMoveSelector<>(mockProvider, stages, false);

        assertTrue(selector.isNeverEnding());
    }

    @Test
    void testIterator_generatesCompositeMoves() {
        MultistageMoveSelector<TestSolution> selector =
            createSelectorWithMockStages();

        Iterator<Move<TestSolution>> iterator = selector.iterator();

        assertTrue(iterator.hasNext());
        Move<TestSolution> move = iterator.next();
        assertTrue(move instanceof CompositeMove);
    }
}
```

#### Iterator Tests

```java
class SequentialMultistageMoveIteratorTest {

    @Test
    void testIterator_generatesAllCombinations() {
        List<MoveSelector<TestSolution>> stages = List.of(
            createSelectorWithMoves(2),  // A, B
            createSelectorWithMoves(3)   // X, Y, Z
        );

        Iterator<Move<TestSolution>> iterator =
            new SequentialMultistageMoveIterator<>(stages);

        int count = 0;
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }

        assertEquals(6, count); // 2 * 3 = 6 combinations
    }

    @Test
    void testRandomIterator_neverEnds() {
        List<MoveSelector<TestSolution>> stages = List.of(
            createNeverEndingSelector(),
            createNeverEndingSelector()
        );

        Iterator<Move<TestSolution>> iterator =
            new RandomMultistageMoveIterator<>(stages, 12345L);

        for (int i = 0; i < 1000; i++) {
            assertTrue(iterator.hasNext());
            assertNotNull(iterator.next());
        }
    }
}
```

### 7.2 Integration Tests

```java
class MultistageMoveIntegrationTest {

    @Test
    void testSolver_withMultistageMove_findsBetterSolution() {
        SolverConfig<TestSolution> config = createConfigWithMultistageMove();
        Solver<TestSolution> solver = Solver.create(config);

        TestSolution problem = createTestProblem();
        TestSolution solution = solver.solve(problem);

        assertTrue(solution.getScore().isFeasible());
        assertTrue(solution.getScore().compareTo(problem.getScore()) > 0);
    }

    @Test
    void testSolver_withRuinRecreate_escapesLocalOptimum() {
        // Create a problem known to have local optima
        TestProblem problem = createProblemWithLocalOptimum();

        Solver<TestSolution> solver = createSolverWithRuinRecreate();
        TestSolution solution = solver.solve(problem);

        // Verify solver escaped local optimum
        assertNotEquals(getLocalOptimumScore(), solution.getScore());
    }
}
```

### 7.3 Performance Tests

```java
@Benchmark
class MultistageMoveBenchmark {

    @Benchmark
    public void benchmarkMultistageMoveGeneration(Blackhole bh) {
        MultistageMoveSelector<TestSolution> selector =
            createMultistageSelector();

        Iterator<Move<TestSolution>> iterator = selector.iterator();
        for (int i = 0; i < 1000; i++) {
            bh.consume(iterator.next());
        }
    }

    @Benchmark
    public void benchmarkUnionMoveGeneration(Blackhole bh) {
        UnionMoveSelector<TestSolution> selector =
            createUnionSelector();

        Iterator<Move<TestSolution>> iterator = selector.iterator();
        for (int i = 0; i < 1000; i++) {
            bh.consume(iterator.next());
        }
    }
}
```

### 7.4 Test Coverage Goals

| Component | Target Coverage | Priority |
|-----------|----------------|----------|
| StageProvider | 100% | High |
| MultistageMoveSelector | 95% | High |
| Iterators | 95% | High |
| Factories | 90% | Medium |
| Integration | 80% | Medium |

---

## 8. Performance Considerations

### 8.1 Time Complexity

| Operation | Complexity | Notes |
|-----------|------------|-------|
| Move generation (sequential) | O(Π stageSizes) | Cartesian product |
| Move generation (random) | O(stageCount) | Constant per move |
| Size calculation | O(stageCount) | Product of stage sizes |
| Never-ending check | O(stageCount) | Any stage never-ending? |

### 8.2 Space Complexity

| Component | Space | Notes |
|-----------|-------|-------|
| Stage selectors | O(stageCount) | One selector per stage |
| Iterators | O(stageCount) | One iterator per stage |
| Composite moves | O(stageCount) | One move per stage |

### 8.3 Optimization Strategies

1. **Lazy Move Generation**
   - Only generate moves when needed
   - Don't precompute all combinations

2. **Stage Caching**
   - Cache stage selectors if they don't change
   - Reuse iterators where possible

3. **Move Composition**
   - Use efficient CompositeMove implementation
   - Avoid unnecessary move copying

4. **Random Selection**
   - Use efficient random number generation
   - Avoid bias in random selection

### 8.4 Performance Benchmarks

Expected performance characteristics:

| Scenario | Expected Performance | Notes |
|----------|---------------------|-------|
| Small problems (n < 100) | < 1ms per move | Fast enough |
| Medium problems (n < 1000) | 1-10ms per move | Acceptable |
| Large problems (n < 10000) | 10-100ms per move | May need optimization |
| Very large problems (n > 10000) | > 100ms per move | Consider alternatives |

### 8.5 Memory Usage

Expected memory usage:

| Component | Memory per Instance | Notes |
|-----------|---------------------|-------|
| MultistageMoveSelector | ~1KB | Negligible |
| Stage selectors | ~100B per stage | Depends on stage type |
| Iterators | ~50B per iterator | Temporary |
| Composite moves | ~200B per move | Depends on move complexity |

---

## 9. Migration Path

### 9.1 For Existing Users

**No Breaking Changes**: Multistage moves are an addition, not a replacement.

**Optional Adoption**: Users can adopt multistage moves gradually:

1. **Start with Union Moves** (already available)
2. **Add Multistage Moves** for specific problems
3. **Replace Union Moves** if multistage proves better

### 9.2 Migration Example

**Before (Union Only)**:
```xml
<unionMoveSelector>
  <changeMoveSelector/>
  <swapMoveSelector/>
  <pillarSwapMoveSelector/>
</unionMoveSelector>
```

**After (Union + Multistage)**:
```xml
<unionMoveSelector>
  <changeMoveSelector/>
  <swapMoveSelector/>
  <pillarSwapMoveSelector/>
  <multistageMoveSelector>
    <stageProviderClass>com.example.AdaptiveStageProvider</stageProviderClass>
  </multistageMoveSelector>
</unionMoveSelector>
```

### 9.3 Backward Compatibility

- Existing configurations continue to work
- No changes to existing APIs
- New feature is opt-in via configuration

### 9.4 Deprecation Plan

No deprecation needed. Multistage moves complement existing features.

---

## 10. Open Questions

### 10.1 Technical Questions

1. **Stage Transition Logic**
   - Question: Should stages transition automatically or be controlled by the StageProvider?
   - Recommendation: Automatic based on iterator pattern (current design)

2. **Move Composition Strategy**
   - Question: How should moves from different stages be composed?
   - Recommendation: Use existing CompositeMove infrastructure

3. **Error Handling**
   - Question: What happens if a stage fails to generate moves?
   - Recommendation: Throw IllegalStateException with clear message

4. **Caching Strategy**
   - Question: Should multistage moves support caching?
   - Recommendation: Yes, via existing cacheType configuration

### 10.2 Design Questions

1. **Stage Provider Lifecycle**
   - Question: When should StageProvider be instantiated?
   - Recommendation: During factory construction, not per-solve

2. **Stage Selector Sharing**
   - Question: Can stages share state or selectors?
   - Recommendation: No, each stage should be independent

3. **Randomness Control**
   - Question: How to ensure reproducible results with random selection?
   - Recommendation: Use workingRandom from step scope (current design)

### 10.3 User Experience Questions

1. **Configuration Complexity**
   - Question: Is multistage move configuration too complex for average users?
   - Recommendation: Provide pre-built StageProvider implementations for common patterns

2. **Performance Expectations**
   - Question: How to set realistic performance expectations?
   - Recommendation: Document use cases where multistage shines (VRP, scheduling)

3. **Debugging Support**
   - Question: How to help users debug multistage move issues?
   - Recommendation: Add logging for stage transitions and move composition

### 10.4 Future Enhancements

1. **Adaptive Stage Selection**
   - Future: Dynamically choose which stages to use based on solving progress

2. **Machine Learning Integration**
   - Future: Use ML to learn optimal stage configurations

3. **Parallel Stage Execution**
   - Future: Execute independent stages in parallel (if applicable)

4. **Stage Composition Language**
   - Future: DSL for defining stages declaratively

---

## Appendix A: Glossary

| Term | Definition |
|------|------------|
| **Multistage Move** | A composite move consisting of multiple dependent stages executed atomically |
| **Stage** | A single step in a multistage move that generates moves for a specific purpose |
| **Stage Provider** | User-defined interface that creates move selectors for each stage |
| **Composite Move** | A move that combines multiple child moves into one atomic operation |
| **Union Move** | A selector that combines multiple move types (horizontal composition) |
| **Ruin and Recreate** | A multistage move pattern that removes and reinserts entities |
| **Pillar Move** | A move that operates on groups of entities sharing the same value |
| **k-Opt Move** | A multistage move for VRP that removes and reconnects route segments |
| **Local Optimum** | A solution that is better than all neighbors but not globally optimal |
| **Score Trap** | A situation where single-variable moves cannot improve the solution |

---

## Appendix B: References

### Research Documents

1. **TimeFold Multistage Move Research**
   - File: `docs/feature_plans/multistage_move/multistage_move_research_results.md`
   - Key insights: Multistage moves enable coordinated changes and escape local optima

2. **Multistage vs Union Move Comparison**
   - File: `docs/feature_plans/multistage_move/MULTISTAGE_VS_UNION_MOVE_COMPARISON.md`
   - Key insights: Fundamental difference between horizontal (union) and vertical (multistage) composition

### Existing Code

1. **MultistageMoveSelectorConfig**
   - Path: `core/src/main/java/ai/greycos/solver/core/config/heuristic/selector/move/generic/MultistageMoveSelectorConfig.java`
   - Current state: Configuration class exists, throws UnsupportedOperationException in community edition

2. **GreycosSolverEnterpriseService**
   - Path: `core/src/main/java/ai/greycos/solver/core/enterprise/GreycosSolverEnterpriseService.java`
   - Current state: Interface defines factory methods but implementations throw exceptions

3. **UnionMoveSelector**
   - Path: `core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/composite/UnionMoveSelector.java`
   - Reference: Similar pattern for composite selectors

4. **CompositeMove**
   - Path: `core/src/main/java/ai/greycos/solver/core/impl/heuristic/move/CompositeMove.java`
   - Reference: Infrastructure for composing multiple moves

### External Resources

1. **TimeFold Documentation**
   - URL: https://docs.timefold.ai
   - Topics: Move selectors, local search, optimization algorithms

2. **OptaPlanner Documentation**
   - URL: https://www.optaplanner.org
   - Topics: Move types, neighborhood search, metaheuristics

---

## Appendix C: Implementation Checklist

### Phase 1: Core Infrastructure
- [ ] Create StageProvider interface
- [ ] Implement MultistageMoveSelector
- [ ] Implement SequentialMultistageMoveIterator
- [ ] Implement RandomMultistageMoveIterator
- [ ] Implement MultistageMoveSelectorFactory
- [ ] Write unit tests
- [ ] Review and approve

### Phase 2: List Variable Support
- [ ] Create ListMultistageMoveSelector
- [ ] Implement ListMultistageMoveSelectorFactory
- [ ] Write unit tests
- [ ] Review and approve

### Phase 3: Enterprise Service Integration
- [ ] Update GreycosSolverEnterpriseService
- [ ] Update DefaultGreycosSolverEnterpriseService
- [ ] Write integration tests
- [ ] Review and approve

### Phase 4: XSD and Configuration
- [ ] Update solver.xsd
- [ ] Update JAXB bindings
- [ ] Write configuration tests
- [ ] Review and approve

### Phase 5: Example Implementations
- [ ] Implement Ruin and Recreate example
- [ ] Implement Pillar Move example
- [ ] Implement k-Opt example
- [ ] Write user guide
- [ ] Review and approve

### Phase 6: Performance Optimization
- [ ] Profile implementation
- [ ] Apply optimizations
- [ ] Create benchmarks
- [ ] Document performance
- [ ] Review and approve

### Phase 7: Testing and Validation
- [ ] Complete unit tests (>90% coverage)
- [ ] Complete integration tests
- [ ] Run regression tests
- [ ] Review documentation
- [ ] Final approval

---

## Conclusion

This implementation plan provides a comprehensive roadmap for implementing the Multistage Move feature in Greycos Solver. The plan is organized into 7 phases over 8 weeks, with clear objectives, tasks, acceptance criteria, and deliverables for each phase.

**Key Success Factors**:

1. **Clear Understanding**: Multistage moves are fundamentally different from union moves
2. **Phased Approach**: Incremental implementation reduces risk
3. **Comprehensive Testing**: Unit, integration, and performance tests ensure quality
4. **Documentation**: Examples and user guides enable adoption
5. **Performance Focus**: Optimization ensures production readiness

**Expected Benefits**:

- **Better Solutions**: Ability to escape local optima and reach higher-quality solutions
- **Flexible Architecture**: Extensible framework for custom multistage moves
- **Enterprise Feature**: Differentiator for enterprise edition
- **User Empowerment**: Users can encode domain knowledge in multistage moves

**Next Steps**:

1. Review and approve this plan
2. Assign resources and timeline
3. Begin Phase 1 implementation
4. Regular progress reviews

---

**Document Version**: 1.0
**Last Updated**: 2025
**Status**: Ready for Review
