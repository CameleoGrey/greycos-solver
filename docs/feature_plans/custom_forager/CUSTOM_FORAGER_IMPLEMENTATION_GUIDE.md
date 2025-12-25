# Custom Forager Implementation Guide for OptaPlanner

This guide provides detailed instructions on how to implement and configure all types of foragers in OptaPlanner. If your fork only has the default choice available, follow these steps to enable all forager types.

## Table of Contents

1. [Overview](#overview)
2. [Forager Architecture](#forager-architecture)
3. [LocalSearch Forager Implementation](#localsearch-forager-implementation)
4. [ConstructionHeuristic Forager Implementation](#constructionheuristic-forager-implementation)
5. [FinalistPodium Implementation](#finalistpodium-implementation)
6. [Configuration Examples](#configuration-examples)
7. [Testing Your Implementation](#testing-your-implementation)

---

## Overview

OptaPlanner uses foragers to select the best move during optimization. There are two main categories:

1. **LocalSearchForager** - Used in local search phases
2. **ConstructionHeuristicForager** - Used in construction heuristic phases

Each category has a concrete implementation with configurable strategies.

---

## Forager Architecture

### Class Hierarchy

```
LocalSearchForager (interface)
└── AbstractLocalSearchForager (abstract)
    └── AcceptedLocalSearchForager (concrete)

ConstructionHeuristicForager (interface)
└── AbstractConstructionHeuristicForager (abstract)
    └── DefaultConstructionHeuristicForager (concrete)

FinalistPodium (interface)
└── AbstractFinalistPodium (abstract)
    ├── HighestScoreFinalistPodium (concrete)
    └── StrategicOscillationByLevelFinalistPodium (concrete)
```

### Key Configuration Enums

- `FinalistPodiumType` - Determines how moves are selected (4 options)
- `LocalSearchPickEarlyType` - When to stop evaluating moves in local search (3 options)
- `ConstructionHeuristicPickEarlyType` - When to stop evaluating moves in construction heuristic (4 options)

---

## LocalSearch Forager Implementation

### Step 1: Verify Required Files Exist

Ensure the following files exist in your fork:

```
core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/forager/
├── LocalSearchForager.java
├── AbstractLocalSearchForager.java
├── AcceptedLocalSearchForager.java
├── LocalSearchForagerFactory.java
└── finalist/
    ├── FinalistPodium.java
    ├── AbstractFinalistPodium.java
    ├── HighestScoreFinalistPodium.java
    └── StrategicOscillationByLevelFinalistPodium.java
```

### Step 2: Verify Configuration Files Exist

Ensure these configuration files exist:

```
core/optaplanner-core-impl/src/main/java/org/optaplanner/core/config/localsearch/decider/forager/
├── LocalSearchForagerConfig.java
├── LocalSearchPickEarlyType.java
└── FinalistPodiumType.java
```

### Step 3: Implement LocalSearchForager.java

```java
package org.optaplanner.core.impl.localsearch.decider.forager;

import org.optaplanner.core.impl.heuristic.selector.move.MoveSelector;
import org.optaplanner.core.impl.localsearch.decider.LocalSearchDecider;
import org.optaplanner.core.impl.localsearch.event.LocalSearchPhaseLifecycleListener;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchMoveScope;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchStepScope;

/**
 * Collects the moves and picks the next step from those for the {@link LocalSearchDecider}.
 *
 * @see AbstractLocalSearchForager
 */
public interface LocalSearchForager<Solution_> extends LocalSearchPhaseLifecycleListener<Solution_> {

    /**
     * @return true if it can be combined with a {@link MoveSelector#isNeverEnding()} that returns true.
     */
    boolean supportsNeverEndingMoveSelector();

    /**
     * @param moveScope never null
     */
    void addMove(LocalSearchMoveScope<Solution_> moveScope);

    /**
     * @return true if no further moves should be selected (and evaluated) for this step.
     */
    boolean isQuitEarly();

    /**
     * @param stepScope never null
     * @return sometimes null, for example if no move is selected
     */
    LocalSearchMoveScope<Solution_> pickMove(LocalSearchStepScope<Solution_> stepScope);
}
```

### Step 4: Implement AbstractLocalSearchForager.java

```java
package org.optaplanner.core.impl.localsearch.decider.forager;

import org.optaplanner.core.impl.localsearch.event.LocalSearchPhaseLifecycleListenerAdapter;

/**
 * Abstract superclass for {@link LocalSearchForager}.
 */
public abstract class AbstractLocalSearchForager<Solution_> extends LocalSearchPhaseLifecycleListenerAdapter<Solution_>
        implements LocalSearchForager<Solution_> {

    // Common functionality for all local search foragers
}
```

### Step 5: Implement AcceptedLocalSearchForager.java

```java
package org.optaplanner.core.impl.localsearch.decider.forager;

import java.util.List;

import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.config.localsearch.decider.forager.LocalSearchPickEarlyType;
import org.optaplanner.core.impl.localsearch.decider.acceptor.Acceptor;
import org.optaplanner.core.impl.localsearch.decider.forager.finalist.FinalistPodium;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchMoveScope;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchPhaseScope;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchStepScope;
import org.optaplanner.core.impl.solver.scope.SolverScope;

/**
 * A {@link LocalSearchForager} which forages accepted moves and ignores unaccepted moves.
 *
 * @see LocalSearchForager
 * @see Acceptor
 */
public class AcceptedLocalSearchForager<Solution_> extends AbstractLocalSearchForager<Solution_> {

    protected final FinalistPodium<Solution_> finalistPodium;
    protected final LocalSearchPickEarlyType pickEarlyType;
    protected final int acceptedCountLimit;
    protected final boolean breakTieRandomly;

    protected long selectedMoveCount;
    protected long acceptedMoveCount;
    protected LocalSearchMoveScope<Solution_> earlyPickedMoveScope;

    public AcceptedLocalSearchForager(FinalistPodium<Solution_> finalistPodium,
            LocalSearchPickEarlyType pickEarlyType, int acceptedCountLimit, boolean breakTieRandomly) {
        this.finalistPodium = finalistPodium;
        this.pickEarlyType = pickEarlyType;
        this.acceptedCountLimit = acceptedCountLimit;
        if (acceptedCountLimit < 1) {
            throw new IllegalArgumentException("The acceptedCountLimit (" + acceptedCountLimit
                    + ") cannot be negative or zero.");
        }
        this.breakTieRandomly = breakTieRandomly;
    }

    @Override
    public void solvingStarted(SolverScope<Solution_> solverScope) {
        super.solvingStarted(solverScope);
        finalistPodium.solvingStarted(solverScope);
    }

    @Override
    public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
        super.phaseStarted(phaseScope);
        finalistPodium.phaseStarted(phaseScope);
    }

    @Override
    public void stepStarted(LocalSearchStepScope<Solution_> stepScope) {
        super.stepStarted(stepScope);
        finalistPodium.stepStarted(stepScope);
        selectedMoveCount = 0L;
        acceptedMoveCount = 0L;
        earlyPickedMoveScope = null;
    }

    @Override
    public boolean supportsNeverEndingMoveSelector() {
        return acceptedCountLimit < Integer.MAX_VALUE;
    }

    @Override
    public void addMove(LocalSearchMoveScope<Solution_> moveScope) {
        selectedMoveCount++;
        if (moveScope.getAccepted()) {
            acceptedMoveCount++;
            checkPickEarly(moveScope);
        }
        finalistPodium.addMove(moveScope);
    }

    protected void checkPickEarly(LocalSearchMoveScope<Solution_> moveScope) {
        switch (pickEarlyType) {
            case NEVER:
                break;
            case FIRST_BEST_SCORE_IMPROVING:
                Score bestScore = moveScope.getStepScope().getPhaseScope().getBestScore();
                if (moveScope.getScore().compareTo(bestScore) > 0) {
                    earlyPickedMoveScope = moveScope;
                }
                break;
            case FIRST_LAST_STEP_SCORE_IMPROVING:
                Score lastStepScore = moveScope.getStepScope().getPhaseScope()
                        .getLastCompletedStepScope().getScore();
                if (moveScope.getScore().compareTo(lastStepScore) > 0) {
                    earlyPickedMoveScope = moveScope;
                }
                break;
            default:
                throw new IllegalStateException("The pickEarlyType (" + pickEarlyType + ") is not implemented.");
        }
    }

    @Override
    public boolean isQuitEarly() {
        return earlyPickedMoveScope != null || acceptedMoveCount >= acceptedCountLimit;
    }

    @Override
    public LocalSearchMoveScope<Solution_> pickMove(LocalSearchStepScope<Solution_> stepScope) {
        stepScope.setSelectedMoveCount(selectedMoveCount);
        stepScope.setAcceptedMoveCount(acceptedMoveCount);
        if (earlyPickedMoveScope != null) {
            return earlyPickedMoveScope;
        }
        List<LocalSearchMoveScope<Solution_>> finalistList = finalistPodium.getFinalistList();
        if (finalistList.isEmpty()) {
            return null;
        }
        if (finalistList.size() == 1 || !breakTieRandomly) {
            return finalistList.get(0);
        }
        int randomIndex = stepScope.getWorkingRandom().nextInt(finalistList.size());
        return finalistList.get(randomIndex);
    }

    @Override
    public void stepEnded(LocalSearchStepScope<Solution_> stepScope) {
        super.stepEnded(stepScope);
        finalistPodium.stepEnded(stepScope);
    }

    @Override
    public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
        super.phaseEnded(phaseScope);
        finalistPodium.phaseEnded(phaseScope);
        selectedMoveCount = 0L;
        acceptedMoveCount = 0L;
        earlyPickedMoveScope = null;
    }

    @Override
    public void solvingEnded(SolverScope<Solution_> solverScope) {
        super.solvingEnded(solverScope);
        finalistPodium.solvingEnded(solverScope);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + pickEarlyType + ", " + acceptedCountLimit + ")";
    }
}
```

### Step 6: Implement LocalSearchForagerFactory.java

```java
package org.optaplanner.core.impl.localsearch.decider.forager;

import java.util.Objects;

import org.optaplanner.core.config.localsearch.decider.forager.FinalistPodiumType;
import org.optaplanner.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import org.optaplanner.core.config.localsearch.decider.forager.LocalSearchPickEarlyType;

public class LocalSearchForagerFactory<Solution_> {

    public static <Solution_> LocalSearchForagerFactory<Solution_> create(LocalSearchForagerConfig foragerConfig) {
        return new LocalSearchForagerFactory<>(foragerConfig);
    }

    private final LocalSearchForagerConfig foragerConfig;

    public LocalSearchForagerFactory(LocalSearchForagerConfig foragerConfig) {
        this.foragerConfig = foragerConfig;
    }

    public LocalSearchForager<Solution_> buildForager() {
        LocalSearchPickEarlyType pickEarlyType_ =
                Objects.requireNonNullElse(foragerConfig.getPickEarlyType(), LocalSearchPickEarlyType.NEVER);
        int acceptedCountLimit_ = Objects.requireNonNullElse(foragerConfig.getAcceptedCountLimit(), Integer.MAX_VALUE);
        FinalistPodiumType finalistPodiumType_ =
                Objects.requireNonNullElse(foragerConfig.getFinalistPodiumType(), FinalistPodiumType.HIGHEST_SCORE);
        // Breaking ties randomly leads to better results statistically
        boolean breakTieRandomly_ = Objects.requireNonNullElse(foragerConfig.getBreakTieRandomly(), true);
        return new AcceptedLocalSearchForager<>(finalistPodiumType_.buildFinalistPodium(), pickEarlyType_,
                acceptedCountLimit_, breakTieRandomly_);
    }
}
```

---

## ConstructionHeuristic Forager Implementation

### Step 1: Verify Required Files Exist

Ensure the following files exist in your fork:

```
core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/constructionheuristic/decider/forager/
├── ConstructionHeuristicForager.java
├── AbstractConstructionHeuristicForager.java
├── DefaultConstructionHeuristicForager.java
└── ConstructionHeuristicForagerFactory.java
```

### Step 2: Verify Configuration Files Exist

Ensure these configuration files exist:

```
core/optaplanner-core-impl/src/main/java/org/optaplanner/core/config/constructionheuristic/decider/forager/
├── ConstructionHeuristicForagerConfig.java
└── ConstructionHeuristicPickEarlyType.java
```

### Step 3: Implement ConstructionHeuristicForager.java

```java
package org.optaplanner.core.impl.constructionheuristic.decider.forager;

import org.optaplanner.core.impl.constructionheuristic.event.ConstructionHeuristicPhaseLifecycleListener;
import org.optaplanner.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import org.optaplanner.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;

/**
 * @see AbstractConstructionHeuristicForager
 */
public interface ConstructionHeuristicForager<Solution_>
        extends ConstructionHeuristicPhaseLifecycleListener<Solution_> {

    void addMove(ConstructionHeuristicMoveScope<Solution_> moveScope);

    boolean isQuitEarly();

    ConstructionHeuristicMoveScope<Solution_> pickMove(ConstructionHeuristicStepScope<Solution_> stepScope);
}
```

### Step 4: Implement AbstractConstructionHeuristicForager.java

```java
package org.optaplanner.core.impl.constructionheuristic.decider.forager;

import org.optaplanner.core.impl.constructionheuristic.event.ConstructionHeuristicPhaseLifecycleListenerAdapter;

/**
 * Abstract superclass for {@link ConstructionHeuristicForager}.
 */
public abstract class AbstractConstructionHeuristicForager<Solution_>
        extends ConstructionHeuristicPhaseLifecycleListenerAdapter<Solution_>
        implements ConstructionHeuristicForager<Solution_> {

    // Common functionality for all construction heuristic foragers
}
```

### Step 5: Implement DefaultConstructionHeuristicForager.java

```java
package org.optaplanner.core.impl.constructionheuristic.decider.forager;

import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.config.constructionheuristic.decider.forager.ConstructionHeuristicPickEarlyType;
import org.optaplanner.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import org.optaplanner.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;

public class DefaultConstructionHeuristicForager<Solution_> extends AbstractConstructionHeuristicForager<Solution_> {

    protected final ConstructionHeuristicPickEarlyType pickEarlyType;

    protected long selectedMoveCount;
    protected ConstructionHeuristicMoveScope<Solution_> earlyPickedMoveScope;
    protected ConstructionHeuristicMoveScope<Solution_> maxScoreMoveScope;

    public DefaultConstructionHeuristicForager(ConstructionHeuristicPickEarlyType pickEarlyType) {
        this.pickEarlyType = pickEarlyType;
    }

    @Override
    public void stepStarted(ConstructionHeuristicStepScope<Solution_> stepScope) {
        super.stepStarted(stepScope);
        selectedMoveCount = 0L;
        earlyPickedMoveScope = null;
        maxScoreMoveScope = null;
    }

    @Override
    public void stepEnded(ConstructionHeuristicStepScope<Solution_> stepScope) {
        super.stepEnded(stepScope);
        earlyPickedMoveScope = null;
        maxScoreMoveScope = null;
    }

    @Override
    public void addMove(ConstructionHeuristicMoveScope<Solution_> moveScope) {
        selectedMoveCount++;
        checkPickEarly(moveScope);
        if (maxScoreMoveScope == null || moveScope.getScore().compareTo(maxScoreMoveScope.getScore()) > 0) {
            maxScoreMoveScope = moveScope;
        }
    }

    protected void checkPickEarly(ConstructionHeuristicMoveScope<Solution_> moveScope) {
        switch (pickEarlyType) {
            case NEVER:
                break;
            case FIRST_NON_DETERIORATING_SCORE:
                Score lastStepScore = moveScope.getStepScope().getPhaseScope()
                        .getLastCompletedStepScope().getScore();
                if (moveScope.getScore().withInitScore(0).compareTo(lastStepScore.withInitScore(0)) >= 0) {
                    earlyPickedMoveScope = moveScope;
                }
                break;
            case FIRST_FEASIBLE_SCORE:
                if (moveScope.getScore().withInitScore(0).isFeasible()) {
                    earlyPickedMoveScope = moveScope;
                }
                break;
            case FIRST_FEASIBLE_SCORE_OR_NON_DETERIORATING_HARD:
                Score lastStepScore2 = moveScope.getStepScope().getPhaseScope()
                        .getLastCompletedStepScope().getScore();
                Score lastStepScoreDifference = moveScope.getScore().withInitScore(0)
                        .subtract(lastStepScore2.withInitScore(0));
                if (lastStepScoreDifference.isFeasible()) {
                    earlyPickedMoveScope = moveScope;
                }
                break;
            default:
                throw new IllegalStateException("The pickEarlyType (" + pickEarlyType + ") is not implemented.");
        }
    }

    @Override
    public boolean isQuitEarly() {
        return earlyPickedMoveScope != null;
    }

    @Override
    public ConstructionHeuristicMoveScope<Solution_> pickMove(ConstructionHeuristicStepScope<Solution_> stepScope) {
        stepScope.setSelectedMoveCount(selectedMoveCount);
        if (earlyPickedMoveScope != null) {
            return earlyPickedMoveScope;
        } else {
            return maxScoreMoveScope;
        }
    }
}
```

### Step 6: Implement ConstructionHeuristicForagerFactory.java

```java
package org.optaplanner.core.impl.constructionheuristic.decider.forager;

import java.util.Objects;

import org.optaplanner.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import org.optaplanner.core.config.constructionheuristic.decider.forager.ConstructionHeuristicPickEarlyType;

public class ConstructionHeuristicForagerFactory<Solution_> {

    public static <Solution_> ConstructionHeuristicForagerFactory<Solution_> create(
            ConstructionHeuristicForagerConfig foragerConfig) {
        return new ConstructionHeuristicForagerFactory<>(foragerConfig);
    }

    private final ConstructionHeuristicForagerConfig foragerConfig;

    public ConstructionHeuristicForagerFactory(ConstructionHeuristicForagerConfig foragerConfig) {
        this.foragerConfig = foragerConfig;
    }

    public ConstructionHeuristicForager<Solution_> buildForager() {
        ConstructionHeuristicPickEarlyType pickEarlyType_ =
                Objects.requireNonNullElse(foragerConfig.getPickEarlyType(), ConstructionHeuristicPickEarlyType.NEVER);
        return new DefaultConstructionHeuristicForager<>(pickEarlyType_);
    }
}
```

---

## FinalistPodium Implementation

### Step 1: Verify Required Files Exist

Ensure the following files exist in your fork:

```
core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/localsearch/decider/forager/finalist/
├── FinalistPodium.java
├── AbstractFinalistPodium.java
├── HighestScoreFinalistPodium.java
└── StrategicOscillationByLevelFinalistPodium.java
```

### Step 2: Implement FinalistPodium.java

```java
package org.optaplanner.core.impl.localsearch.decider.forager.finalist;

import java.util.List;

import org.optaplanner.core.impl.localsearch.decider.forager.LocalSearchForager;
import org.optaplanner.core.impl.localsearch.event.LocalSearchPhaseLifecycleListener;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchMoveScope;

/**
 * A podium gathers the finalists (the {@link LocalSearchMoveScope}s which might win) and picks the winner.
 *
 * @see AbstractFinalistPodium
 * @see HighestScoreFinalistPodium
 */
public interface FinalistPodium<Solution_> extends LocalSearchPhaseLifecycleListener<Solution_> {

    /**
     * See {@link LocalSearchForager#addMove(LocalSearchMoveScope)}.
     *
     * @param moveScope never null
     */
    void addMove(LocalSearchMoveScope<Solution_> moveScope);

    /**
     *
     * @return never null, sometimes empty
     */
    List<LocalSearchMoveScope<Solution_>> getFinalistList();
}
```

### Step 3: Implement AbstractFinalistPodium.java

```java
package org.optaplanner.core.impl.localsearch.decider.forager.finalist;

import java.util.ArrayList;
import java.util.List;

import org.optaplanner.core.impl.localsearch.event.LocalSearchPhaseLifecycleListenerAdapter;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchMoveScope;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchPhaseScope;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchStepScope;

/**
 * Abstract superclass for {@link FinalistPodium}.
 *
 * @see FinalistPodium
 */
public abstract class AbstractFinalistPodium<Solution_> extends LocalSearchPhaseLifecycleListenerAdapter<Solution_>
        implements FinalistPodium<Solution_> {

    protected static final int FINALIST_LIST_MAX_SIZE = 1_024_000;

    protected boolean finalistIsAccepted;
    protected List<LocalSearchMoveScope<Solution_>> finalistList = new ArrayList<>(1024);

    @Override
    public void stepStarted(LocalSearchStepScope<Solution_> stepScope) {
        super.stepStarted(stepScope);
        finalistIsAccepted = false;
        finalistList.clear();
    }

    protected void clearAndAddFinalist(LocalSearchMoveScope<Solution_> moveScope) {
        finalistList.clear();
        finalistList.add(moveScope);
    }

    protected void addFinalist(LocalSearchMoveScope<Solution_> moveScope) {
        if (finalistList.size() >= FINALIST_LIST_MAX_SIZE) {
            // Avoid unbounded growth and OutOfMemoryException
            return;
        }
        finalistList.add(moveScope);
    }

    @Override
    public List<LocalSearchMoveScope<Solution_>> getFinalistList() {
        return finalistList;
    }

    @Override
    public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
        super.phaseEnded(phaseScope);
        finalistIsAccepted = false;
        finalistList.clear();
    }
}
```

### Step 4: Implement HighestScoreFinalistPodium.java

```java
package org.optaplanner.core.impl.localsearch.decider.forager.finalist;

import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchMoveScope;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchPhaseScope;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchStepScope;

/**
 * Default implementation of {@link FinalistPodium}.
 *
 * @see FinalistPodium
 */
public final class HighestScoreFinalistPodium<Solution_> extends AbstractFinalistPodium<Solution_> {

    protected Score finalistScore;

    @Override
    public void stepStarted(LocalSearchStepScope<Solution_> stepScope) {
        super.stepStarted(stepScope);
        finalistScore = null;
    }

    @Override
    public void addMove(LocalSearchMoveScope<Solution_> moveScope) {
        boolean accepted = moveScope.getAccepted();
        if (finalistIsAccepted && !accepted) {
            return;
        }
        if (accepted && !finalistIsAccepted) {
            finalistIsAccepted = true;
            finalistScore = null;
        }
        Score moveScore = moveScope.getScore();
        int scoreComparison = doComparison(moveScore);
        if (scoreComparison > 0) {
            finalistScore = moveScore;
            clearAndAddFinalist(moveScope);
        } else if (scoreComparison == 0) {
            addFinalist(moveScope);
        }
    }

    private int doComparison(Score moveScore) {
        if (finalistScore == null) {
            return 1;
        }
        return moveScore.compareTo(finalistScore);
    }

    @Override
    public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
        super.phaseEnded(phaseScope);
        finalistScore = null;
    }
}
```

### Step 5: Implement StrategicOscillationByLevelFinalistPodium.java

```java
package org.optaplanner.core.impl.localsearch.decider.forager.finalist;

import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchMoveScope;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchPhaseScope;
import org.optaplanner.core.impl.localsearch.scope.LocalSearchStepScope;

/**
 * Strategic oscillation, works well with Tabu search.
 *
 * @see FinalistPodium
 */
public final class StrategicOscillationByLevelFinalistPodium<Solution_> extends AbstractFinalistPodium<Solution_> {

    protected final boolean referenceBestScoreInsteadOfLastStepScore;

    protected Score referenceScore;
    protected Number[] referenceLevelNumbers;

    protected Score finalistScore;
    protected Number[] finalistLevelNumbers;
    protected boolean finalistImprovesUponReference;

    public StrategicOscillationByLevelFinalistPodium(boolean referenceBestScoreInsteadOfLastStepScore) {
        this.referenceBestScoreInsteadOfLastStepScore = referenceBestScoreInsteadOfLastStepScore;
    }

    @Override
    public void stepStarted(LocalSearchStepScope<Solution_> stepScope) {
        super.stepStarted(stepScope);
        referenceScore = referenceBestScoreInsteadOfLastStepScore
                ? stepScope.getPhaseScope().getBestScore()
                : stepScope.getPhaseScope().getLastCompletedStepScope().getScore();
        referenceLevelNumbers = referenceBestScoreInsteadOfLastStepScore
                ? stepScope.getPhaseScope().getBestScore().toLevelNumbers()
                : stepScope.getPhaseScope().getLastCompletedStepScope().getScore().toLevelNumbers();
        finalistScore = null;
        finalistLevelNumbers = null;
        finalistImprovesUponReference = false;
    }

    @Override
    public void addMove(LocalSearchMoveScope<Solution_> moveScope) {
        boolean accepted = moveScope.getAccepted();
        if (finalistIsAccepted && !accepted) {
            return;
        }
        if (accepted && !finalistIsAccepted) {
            finalistIsAccepted = true;
            finalistScore = null;
            finalistLevelNumbers = null;
        }
        Score moveScore = moveScope.getScore();
        Number[] moveLevelNumbers = moveScore.toLevelNumbers();
        int comparison = doComparison(moveScore, moveLevelNumbers);
        if (comparison > 0) {
            finalistScore = moveScore;
            finalistLevelNumbers = moveLevelNumbers;
            finalistImprovesUponReference = (moveScore.compareTo(referenceScore) > 0);
            clearAndAddFinalist(moveScope);
        } else if (comparison == 0) {
            addFinalist(moveScope);
        }
    }

    private int doComparison(Score moveScore, Number[] moveLevelNumbers) {
        if (finalistScore == null) {
            return 1;
        }
        // If there is an improving move, do not oscillate
        if (!finalistImprovesUponReference && moveScore.compareTo(referenceScore) < 0) {
            for (int i = 0; i < referenceLevelNumbers.length; i++) {
                boolean moveIsHigher = ((Comparable) moveLevelNumbers[i]).compareTo(referenceLevelNumbers[i]) > 0;
                boolean finalistIsHigher = ((Comparable) finalistLevelNumbers[i]).compareTo(referenceLevelNumbers[i]) > 0;
                if (moveIsHigher) {
                    if (finalistIsHigher) {
                        // Both are higher, take the best one but do not ignore higher levels
                        break;
                    } else {
                        // The move has the first level which is higher while the finalist is lower than the reference
                        return 1;
                    }
                } else {
                    if (finalistIsHigher) {
                        // The finalist has the first level which is higher while the move is lower than the reference
                        return -1;
                    } else {
                        // Both are lower, ignore this level
                    }
                }
            }
        }
        return moveScore.compareTo(finalistScore);
    }

    @Override
    public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
        super.phaseEnded(phaseScope);
        referenceScore = null;
        referenceLevelNumbers = null;
        finalistScore = null;
        finalistLevelNumbers = null;
    }
}
```

---

## Configuration Classes Implementation

### LocalSearchPickEarlyType.java

```java
package org.optaplanner.core.config.localsearch.decider.forager;

import jakarta.xml.bind.annotation.XmlEnum;

@XmlEnum
public enum LocalSearchPickEarlyType {
    NEVER,
    FIRST_BEST_SCORE_IMPROVING,
    FIRST_LAST_STEP_SCORE_IMPROVING;
}
```

### FinalistPodiumType.java

```java
package org.optaplanner.core.config.localsearch.decider.forager;

import jakarta.xml.bind.annotation.XmlEnum;

import org.optaplanner.core.impl.localsearch.decider.forager.finalist.FinalistPodium;
import org.optaplanner.core.impl.localsearch.decider.forager.finalist.HighestScoreFinalistPodium;
import org.optaplanner.core.impl.localsearch.decider.forager.finalist.StrategicOscillationByLevelFinalistPodium;

@XmlEnum
public enum FinalistPodiumType {
    HIGHEST_SCORE,
    STRATEGIC_OSCILLATION,
    STRATEGIC_OSCILLATION_BY_LEVEL,
    STRATEGIC_OSCILLATION_BY_LEVEL_ON_BEST_SCORE;

    public <Solution_> FinalistPodium<Solution_> buildFinalistPodium() {
        switch (this) {
            case HIGHEST_SCORE:
                return new HighestScoreFinalistPodium<>();
            case STRATEGIC_OSCILLATION:
            case STRATEGIC_OSCILLATION_BY_LEVEL:
                return new StrategicOscillationByLevelFinalistPodium<>(false);
            case STRATEGIC_OSCILLATION_BY_LEVEL_ON_BEST_SCORE:
                return new StrategicOscillationByLevelFinalistPodium<>(true);
            default:
                throw new IllegalStateException("The finalistPodiumType (" + this + ") is not implemented.");
        }
    }
}
```

### ConstructionHeuristicPickEarlyType.java

```java
package org.optaplanner.core.config.constructionheuristic.decider.forager;

import jakarta.xml.bind.annotation.XmlEnum;

@XmlEnum
public enum ConstructionHeuristicPickEarlyType {
    NEVER,
    FIRST_NON_DETERIORATING_SCORE,
    FIRST_FEASIBLE_SCORE,
    FIRST_FEASIBLE_SCORE_OR_NON_DETERIORATING_HARD;
}
```

---

## Configuration Examples

### XML Configuration for LocalSearch

```xml
<solver>
  <localSearch>
    <localSearchDecider>
      <forager>
        <!-- FinalistPodiumType: HIGHEST_SCORE, STRATEGIC_OSCILLATION, 
             STRATEGIC_OSCILLATION_BY_LEVEL, STRATEGIC_OSCILLATION_BY_LEVEL_ON_BEST_SCORE -->
        <finalistPodiumType>HIGHEST_SCORE</finalistPodiumType>
        
        <!-- LocalSearchPickEarlyType: NEVER, FIRST_BEST_SCORE_IMPROVING, 
             FIRST_LAST_STEP_SCORE_IMPROVING -->
        <pickEarlyType>NEVER</pickEarlyType>
        
        <!-- Maximum number of accepted moves to evaluate (default: Integer.MAX_VALUE) -->
        <acceptedCountLimit>1000</acceptedCountLimit>
        
        <!-- Break ties randomly (default: true) -->
        <breakTieRandomly>true</breakTieRandomly>
      </forager>
    </localSearchDecider>
  </localSearch>
</solver>
```

### Java API Configuration for LocalSearch

```java
SolverConfig solverConfig = new SolverConfig();
solverConfig.withLocalSearchPhaseConfig(new LocalSearchPhaseConfig()
    .withDeciderConfig(new LocalSearchDeciderConfig()
        .withForagerConfig(new LocalSearchForagerConfig()
            .withFinalistPodiumType(FinalistPodiumType.HIGHEST_SCORE)
            .withPickEarlyType(LocalSearchPickEarlyType.NEVER)
            .withAcceptedCountLimit(1000)
            .withBreakTieRandomly(true))));
```

### XML Configuration for ConstructionHeuristic

```xml
<solver>
  <constructionHeuristic>
    <constructionHeuristicDecider>
      <forager>
        <!-- ConstructionHeuristicPickEarlyType: NEVER, FIRST_NON_DETERIORATING_SCORE, 
             FIRST_FEASIBLE_SCORE, FIRST_FEASIBLE_SCORE_OR_NON_DETERIORATING_HARD -->
        <pickEarlyType>NEVER</pickEarlyType>
      </forager>
    </constructionHeuristicDecider>
  </constructionHeuristic>
</solver>
```

### Java API Configuration for ConstructionHeuristic

```java
SolverConfig solverConfig = new SolverConfig();
solverConfig.withConstructionHeuristicPhaseConfig(new ConstructionHeuristicPhaseConfig()
    .withDeciderConfig(new ConstructionHeuristicDeciderConfig()
        .withForagerConfig(new ConstructionHeuristicForagerConfig()
            .withPickEarlyType(ConstructionHeuristicPickEarlyType.NEVER))));
```

---

## Forager Type Descriptions

### LocalSearch Forager Types

#### FinalistPodiumType Options:

1. **HIGHEST_SCORE** (default)
   - Selects the move with the highest score
   - Simple and predictable
   - Works well for most problems

2. **STRATEGIC_OSCILLATION**
   - Uses strategic oscillation based on last step score
   - Helps escape local optima
   - Works well with Tabu search

3. **STRATEGIC_OSCILLATION_BY_LEVEL**
   - Strategic oscillation by score level based on last step score
   - More sophisticated oscillation strategy
   - Good for problems with multiple score levels

4. **STRATEGIC_OSCILLATION_BY_LEVEL_ON_BEST_SCORE**
   - Strategic oscillation by score level based on best score
   - Most aggressive oscillation strategy
   - Best for complex problems with many local optima

#### LocalSearchPickEarlyType Options:

1. **NEVER** (default)
   - Evaluates all moves
   - Most thorough but slowest
   - Best for small problem instances

2. **FIRST_BEST_SCORE_IMPROVING**
   - Stops when a move improves the best score
   - Faster than NEVER
   - Good for large problem instances

3. **FIRST_LAST_STEP_SCORE_IMPROVING**
   - Stops when a move improves the last step score
   - Fastest option
   - Best for very large problem instances

### ConstructionHeuristic Forager Types

#### ConstructionHeuristicPickEarlyType Options:

1. **NEVER** (default)
   - Evaluates all moves
   - Most thorough but slowest
   - Best for finding optimal initial solutions

2. **FIRST_NON_DETERIORATING_SCORE**
   - Stops when a move doesn't deteriorate the score
   - Faster than NEVER
   - Good for most problems

3. **FIRST_FEASIBLE_SCORE**
   - Stops when a feasible move is found
   - Fastest for reaching feasibility
   - Best when feasibility is the primary goal

4. **FIRST_FEASIBLE_SCORE_OR_NON_DETERIORATING_HARD**
   - Stops when a feasible move is found or hard score doesn't deteriorate
   - Balanced approach
   - Good for problems with soft and hard constraints

---

## Testing Your Implementation

### Unit Tests

Create unit tests for each forager type:

```java
public class LocalSearchForagerTest {

    @Test
    public void testAcceptedLocalSearchForagerWithHighestScore() {
        LocalSearchForager<TestSolution> forager = new AcceptedLocalSearchForager<>(
            new HighestScoreFinalistPodium<>(),
            LocalSearchPickEarlyType.NEVER,
            Integer.MAX_VALUE,
            true
        );
        // Test implementation
    }

    @Test
    public void testAcceptedLocalSearchForagerWithStrategicOscillation() {
        LocalSearchForager<TestSolution> forager = new AcceptedLocalSearchForager<>(
            new StrategicOscillationByLevelFinalistPodium<>(false),
            LocalSearchPickEarlyType.NEVER,
            Integer.MAX_VALUE,
            true
        );
        // Test implementation
    }
}
```

### Integration Tests

Test with actual solver configurations:

```java
@Test
public void testLocalSearchWithDifferentForagerTypes() {
    for (FinalistPodiumType podiumType : FinalistPodiumType.values()) {
        for (LocalSearchPickEarlyType pickEarlyType : LocalSearchPickEarlyType.values()) {
            SolverConfig config = new SolverConfig()
                .withLocalSearchPhaseConfig(new LocalSearchPhaseConfig()
                    .withDeciderConfig(new LocalSearchDeciderConfig()
                        .withForagerConfig(new LocalSearchForagerConfig()
                            .withFinalistPodiumType(podiumType)
                            .withPickEarlyType(pickEarlyType))));
            
            Solver<TestSolution> solver = SolverFactory.create(config).buildSolver();
            // Test solver execution
        }
    }
}
```

### Performance Benchmarks

Compare different forager types:

```java
@Test
public void benchmarkForagerTypes() {
    Map<String, Long> results = new HashMap<>();
    
    for (FinalistPodiumType podiumType : FinalistPodiumType.values()) {
        long startTime = System.currentTimeMillis();
        
        SolverConfig config = new SolverConfig()
            .withLocalSearchPhaseConfig(new LocalSearchPhaseConfig()
                .withDeciderConfig(new LocalSearchDeciderConfig()
                    .withForagerConfig(new LocalSearchForagerConfig()
                        .withFinalistPodiumType(podiumType))));
        
        Solver<TestSolution> solver = SolverFactory.create(config).buildSolver();
        TestSolution solution = solver.solve(createProblem());
        
        long endTime = System.currentTimeMillis();
        results.put(podiumType.name(), endTime - startTime);
    }
    
    // Print results
    results.forEach((type, time) -> 
        System.out.println(type + ": " + time + "ms"));
}
```

---

## Troubleshooting

### Common Issues

1. **ClassNotFoundException for forager classes**
   - Ensure all files are in the correct package structure
   - Verify that the classpath includes the optaplanner-core-impl module

2. **Configuration not applied**
   - Check that the configuration XML/Java API is correct
   - Verify that the factory is being called with the correct config

3. **Poor performance with new forager types**
   - Different forager types work better for different problems
   - Experiment with different combinations
   - Consider the problem size and complexity

4. **Random behavior**
   - The `breakTieRandomly` setting is enabled by default
   - Set it to false for deterministic behavior

---

## Best Practices

1. **Start with defaults**: Begin with `HIGHEST_SCORE` and `NEVER` for most problems

2. **Experiment systematically**: Change one parameter at a time and measure impact

3. **Consider problem characteristics**:
   - Large problems: Use `FIRST_BEST_SCORE_IMPROVING` or `FIRST_LAST_STEP_SCORE_IMPROVING`
   - Many local optima: Use strategic oscillation types
   - Need feasibility quickly: Use `FIRST_FEASIBLE_SCORE`

4. **Benchmark**: Always benchmark different configurations for your specific problem

5. **Profile**: Use profiling tools to identify bottlenecks in move evaluation

---

## Summary

This guide provides complete implementation details for all OptaPlanner forager types:

- **2 main forager categories**: LocalSearch and ConstructionHeuristic
- **4 FinalistPodium types** for local search
- **3 LocalSearchPickEarlyType options** for local search
- **4 ConstructionHeuristicPickEarlyType options** for construction heuristic

All implementations follow the same pattern and can be configured via XML or Java API. Choose the appropriate combination based on your problem characteristics and performance requirements.
