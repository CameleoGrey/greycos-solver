# Comprehensive Analysis: MULTISTAGE_MOVE and Custom Forager

## Executive Summary

Both MULTISTAGE_MOVE and Custom Forager are **enterprise-exclusive features** in Greycos Solver. They are gated behind the enterprise licensing system and throw `UnsupportedOperationException` in the community edition. These features provide advanced move selection and move harvesting capabilities for optimization solving.

---

## 1. MULTISTAGE_MOVE

### 1.1 Definition

**MULTISTAGE_MOVE** is a `Feature` enum value defined in [`GreycosSolverEnterpriseService.java:225-227`](../../core/src/main/java/ai/greycos/solver/core/enterprise/GreycosSolverEnterpriseService.java:225-227):

```java
MULTISTAGE_MOVE(
    "Multistage move selector",
    "remove multistageMoveSelector and/or listMultistageMoveSelector from the solver configuration")
```

**Purpose**: Provides a multistage move selector that can apply different move selection strategies in stages during the solving process.

### 1.2 Configuration Classes

#### Basic Variable Multistage Move Selector

- **File**: [`MultistageMoveSelectorConfig.java`](../../core/src/main/java/ai/greycos/solver/core/config/heuristic/selector/move/generic/MultistageMoveSelectorConfig.java:1-101)
- **XML Element**: `multistageMoveSelector`
- **Properties**:
  - `stageProviderClass` (Class<?>) - Required: Custom class that provides move selection stages
  - `entityClass` (Class<?>) - Optional: Target entity class
  - `variableName` (String) - Optional: Target planning variable name

#### List Variable Multistage Move Selector

- **File**: [`ListMultistageMoveSelectorConfig.java`](../../core/src/main/java/ai/greycos/solver/core/config/heuristic/selector/move/generic/list/ListMultistageMoveSelectorConfig.java:1-69)
- **XML Element**: `listMultistageMoveSelector`
- **Properties**:
  - `stageProviderClass` (Class<?>) - Required: Custom class that provides move selection stages

### 1.3 Implementation and Usage

**Invocation Points** in [`MoveSelectorFactory.create()`](../../core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/MoveSelectorFactory.java:104-116):

```java
else if (moveSelectorConfig instanceof MultistageMoveSelectorConfig multistageMoveSelectorConfig) {
  var enterpriseService =
      GreycosSolverEnterpriseService.loadOrFail(
          GreycosSolverEnterpriseService.Feature.MULTISTAGE_MOVE);
  return enterpriseService.buildBasicMultistageMoveSelectorFactory(
      multistageMoveSelectorConfig);
}
else if (moveSelectorConfig instanceof ListMultistageMoveSelectorConfig listMultistageMoveSelectorConfig) {
  var enterpriseService =
      GreycosSolverEnterpriseService.loadOrFail(
          GreycosSolverEnterpriseService.Feature.MULTISTAGE_MOVE);
  return enterpriseService.buildListMultistageMoveSelectorFactory(
      listMultistageMoveSelectorConfig);
}
```

**Enterprise Service Methods** in [`GreycosSolverEnterpriseService.java:209-215`](../../core/src/main/java/ai/greycos/solver/core/enterprise/GreycosSolverEnterpriseService.java:209-215):

```java
<Solution_>
    AbstractMoveSelectorFactory<Solution_, MultistageMoveSelectorConfig>
        buildBasicMultistageMoveSelectorFactory(MultistageMoveSelectorConfig moveSelectorConfig);

<Solution_>
    AbstractMoveSelectorFactory<Solution_, ListMultistageMoveSelectorConfig>
        buildListMultistageMoveSelectorFactory(
            ListMultistageMoveSelectorConfig moveSelectorConfig);
```

**Community Edition Fallback** in [`DefaultGreycosSolverEnterpriseService.java:287-299`](../../core/src/main/java/ai/greycos/solver/core/impl/partitionedsearch/DefaultGreycosSolverEnterpriseService.java:287-299):

```java
@Override
public <Solution_>
    AbstractMoveSelectorFactory<Solution_, MultistageMoveSelectorConfig>
        buildBasicMultistageMoveSelectorFactory(MultistageMoveSelectorConfig moveSelectorConfig) {
  throw new UnsupportedOperationException("Multistage move selector is an enterprise feature.");
}

@Override
public <Solution_>
    AbstractMoveSelectorFactory<Solution_, ListMultistageMoveSelectorConfig>
        buildListMultistageMoveSelectorFactory(
            ListMultistageMoveSelectorConfig moveSelectorConfig) {
  throw new UnsupportedOperationException("Multistage move selector is an enterprise feature.");
}
```

### 1.4 Dependencies and Requirements

- **Enterprise License**: Required (checked via [`GreycosSolverEnterpriseService.loadOrFail()`](../../core/src/main/java/ai/greycos/solver/core/enterprise/GreycosSolverEnterpriseService.java:85-112))
- **Custom Stage Provider**: Must implement a stage provider class that defines move selection stages
- **Solver Configuration**: Must be configured in XML or via programmatic configuration

---

## 2. Custom Forager

### 2.1 Definition

**Custom Forager** refers to the ability to provide custom implementations of `ConstructionHeuristicForager` or `LocalSearchForager` for advanced move harvesting strategies during solving phases.

### 2.2 Configuration Classes

#### Construction Heuristic Forager

- **File**: [`ConstructionHeuristicForagerConfig.java`](../../core/src/main/java/ai/greycos/solver/core/config/constructionheuristic/decider/forager/ConstructionHeuristicForagerConfig.java:1-54)
- **XML Element**: `forager` (within `<constructionHeuristic>`)
- **Properties**:
  - `pickEarlyType` (ConstructionHeuristicPickEarlyType) - Optional: Strategy for early move selection
    - `NEVER` - Never pick early
    - `FIRST_NON_DETERIORATING_SCORE` - Pick first non-deteriorating score
    - `FIRST_FEASIBLE_SCORE` - Pick first feasible score
    - `FIRST_FEASIBLE_SCORE_OR_NON_DETERIORATING_HARD` - Pick first feasible or non-deteriorating hard score

#### Local Search Forager

- **File**: [`LocalSearchForagerConfig.java`](../../core/src/main/java/ai/greycos/solver/core/config/localsearch/decider/forager/LocalSearchForagerConfig.java:1-106)
- **XML Element**: `forager` (within `<localSearch>`)
- **Properties**:
  - `pickEarlyType` (LocalSearchPickEarlyType) - Optional: Early termination strategy
  - `acceptedCountLimit` (Integer) - Optional: Maximum number of accepted moves to collect
  - `finalistPodiumType` (FinalistPodiumType) - Optional: Strategy for selecting best moves
    - `HIGHEST_SCORE` - Select highest scoring move
    - `STRATEGIC_OSCILLATION_BY_LEVEL` - Strategic oscillation by score level
  - `breakTieRandomly` (Boolean) - Optional: Randomly break ties

### 2.3 Implementation and Usage

#### Construction Heuristic Forager

**Interface**: [`ConstructionHeuristicForager.java:10-19`](../../core/src/main/java/ai/greycos/solver/core/impl/constructionheuristic/decider/forager/ConstructionHeuristicForager.java:10-19)

```java
public interface ConstructionHeuristicForager<Solution_>
    extends ConstructionHeuristicPhaseLifecycleListener<Solution_> {
  void addMove(ConstructionHeuristicMoveScope<Solution_> moveScope);
  boolean isQuitEarly();
  ConstructionHeuristicMoveScope<Solution_> pickMove(
      ConstructionHeuristicStepScope<Solution_> stepScope);
}
```

**Default Implementation**: [`DefaultConstructionHeuristicForager.java:8-108`](../../core/src/main/java/ai/greycos/solver/core/impl/constructionheuristic/decider/forager/DefaultConstructionHeuristicForager.java:8-108)

**Enterprise Integration** in [`GreycosSolverEnterpriseService.buildConstructionHeuristic()`](../../core/src/main/java/ai/greycos/solver/core/enterprise/GreycosSolverEnterpriseService.java:133-136):

```java
<Solution_> ConstructionHeuristicDecider<Solution_> buildConstructionHeuristic(
    PhaseTermination<Solution_> termination,
    ConstructionHeuristicForager<Solution_> forager,
    HeuristicConfigPolicy<Solution_> configPolicy);
```

**Community Edition Fallback** in [`DefaultGreycosSolverEnterpriseService.buildConstructionHeuristic()`](../../core/src/main/java/ai/greycos/solver/core/impl/partitionedsearch/DefaultGreycosSolverEnterpriseService.java:81-87):

```java
@Override
public <Solution_> ConstructionHeuristicDecider<Solution_> buildConstructionHeuristic(
    PhaseTermination<Solution_> termination,
    ConstructionHeuristicForager<Solution_> forager,
    HeuristicConfigPolicy<Solution_> configPolicy) {
  throw new UnsupportedOperationException(
      "Construction Heuristic with custom forager is an enterprise feature.");
}
```

**Community Implementation** in [`DefaultConstructionHeuristicPhaseFactory.buildDecider()`](../../core/src/main/java/ai/greycos/solver/core/impl/constructionheuristic/DefaultConstructionHeuristicPhaseFactory.java:196-225):

The community edition uses the default forager implementation without custom forager support.

#### Local Search Forager

**Interface**: [`LocalSearchForager.java:14-15`](../../core/src/main/java/ai/greycos/solver/core/impl/localsearch/decider/forager/LocalSearchForager.java:14-15)

**Default Implementation**: [`AcceptedLocalSearchForager.java:18-`](../../core/src/main/java/ai/greycos/solver/core/impl/localsearch/decider/forager/AcceptedLocalSearchForager.java:18-)

**Enterprise Integration** in [`GreycosSolverEnterpriseService.buildLocalSearch()`](../../core/src/main/java/ai/greycos/solver/core/enterprise/GreycosSolverEnterpriseService.java:138-145):

```java
<Solution_> LocalSearchDecider<Solution_> buildLocalSearch(
    int moveThreadCount,
    PhaseTermination<Solution_> termination,
    MoveRepository<Solution_> moveRepository,
    Acceptor<Solution_> acceptor,
    LocalSearchForager<Solution_> forager,
    EnvironmentMode environmentMode,
    HeuristicConfigPolicy<Solution_> configPolicy);
```

**Community Edition Fallback** in [`DefaultGreycosSolverEnterpriseService.buildLocalSearch()`](../../core/src/main/java/ai/greycos/solver/core/impl/partitionedsearch/DefaultGreycosSolverEnterpriseService.java:90-100):

```java
@Override
public <Solution_> LocalSearchDecider<Solution_> buildLocalSearch(
    int moveThreadCount,
    PhaseTermination<Solution_> termination,
    MoveRepository<Solution_> moveRepository,
    Acceptor<Solution_> acceptor,
    LocalSearchForager<Solution_> forager,
    EnvironmentMode environmentMode,
    HeuristicConfigPolicy<Solution_> configPolicy) {
  throw new UnsupportedOperationException(
      "Local Search with moveThreadCount is an enterprise feature.");
}
```

**Community Implementation** in [`DefaultLocalSearchPhaseFactory.buildDecider()`](../../core/src/main/java/ai/greycos/solver/core/impl/localsearch/DefaultLocalSearchPhaseFactory.java:185-220):

The community edition uses the default forager implementation without custom forager support.

### 2.4 Dependencies and Requirements

- **Enterprise License**: Required for custom forager implementations
- **Forager Configuration**: Must be configured via `foragerConfig` in phase configuration
- **Decider Integration**: Custom foragers must integrate with `ConstructionHeuristicDecider` or `LocalSearchDecider`

---

## 3. Functional Relationship

### 3.1 Relationship Between MULTISTAGE_MOVE and Custom Forager

**No Direct Functional Relationship**: MULTISTAGE_MOVE and Custom Forager are **independent enterprise features** that serve different purposes:

- **MULTISTAGE_MOVE**: Controls **move selection strategy** - determines which moves to generate and in what order (stages)
- **Custom Forager**: Controls **move harvesting strategy** - determines how to collect, evaluate, and select moves from generated moves

**Potential Integration**: While independent, they could theoretically work together:
1. MULTISTAGE_MOVE generates moves in stages
2. Custom Forager harvests and selects the best moves from those stages

### 3.2 Shared Dependencies

Both features share:
- **Enterprise License**: Both require [`GreycosSolverEnterpriseService.loadOrFail()`](../../core/src/main/java/ai/greycos/solver/core/enterprise/GreycosSolverEnterpriseService.java:85-112) to activate
- **Move Selector Factory**: Both integrate through the move selector factory pattern
- **Phase Configuration**: Both are configured at the phase level (construction heuristic or local search)
- **Decider Pattern**: Both integrate with the decider architecture for move evaluation

### 3.3 Configuration Flow

```
Solver Configuration
    ↓
Phase Configuration (ConstructionHeuristic or LocalSearch)
    ↓
Move Selector Configuration (includes MULTISTAGE_MOVE if used)
    ↓
MoveSelectorFactory.create() → Checks for MULTISTAGE_MOVE → Loads Enterprise Service
    ↓
Forager Configuration (includes Custom Forager if used)
    ↓
Decider.build() → Checks for Custom Forager → Loads Enterprise Service
    ↓
Phase Execution (with enterprise features if licensed)
```

---

## 4. File and Line Reference Summary

### MULTISTAGE_MOVE References

| File | Lines | Description |
|------|-------|-------------|
| [`GreycosSolverEnterpriseService.java`](../../core/src/main/java/ai/greycos/solver/core/enterprise/GreycosSolverEnterpriseService.java) | 225-227 | Feature enum definition |
| [`GreycosSolverEnterpriseService.java`](../../core/src/main/java/ai/greycos/solver/core/enterprise/GreycosSolverEnterpriseService.java) | 209-215 | Enterprise service methods |
| [`MoveSelectorFactory.java`](../../core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/MoveSelectorFactory.java) | 104-116 | Invocation and factory building |
| [`DefaultGreycosSolverEnterpriseService.java`](../../core/src/main/java/ai/greycos/solver/core/impl/partitionedsearch/DefaultGreycosSolverEnterpriseService.java) | 287-299 | Community edition fallback |
| [`MultistageMoveSelectorConfig.java`](../../core/src/main/java/ai/greycos/solver/core/config/heuristic/selector/move/generic/MultistageMoveSelectorConfig.java) | 1-101 | Configuration class for basic variables |
| [`ListMultistageMoveSelectorConfig.java`](../../core/src/main/java/ai/greycos/solver/core/config/heuristic/selector/move/generic/list/ListMultistageMoveSelectorConfig.java) | 1-69 | Configuration class for list variables |

### Custom Forager References

| File | Lines | Description |
|------|-------|-------------|
| [`DefaultGreycosSolverEnterpriseService.java`](../../core/src/main/java/ai/greycos/solver/core/impl/partitionedsearch/DefaultGreycosSolverEnterpriseService.java) | 81-87 | Construction heuristic custom forager fallback |
| [`DefaultGreycosSolverEnterpriseService.java`](../../core/src/main/java/ai/greycos/solver/core/impl/partitionedsearch/DefaultGreycosSolverEnterpriseService.java) | 90-100 | Local search custom forager fallback |
| [`GreycosSolverEnterpriseService.java`](../../core/src/main/java/ai/greycos/solver/core/enterprise/GreycosSolverEnterpriseService.java) | 133-136 | Construction heuristic enterprise method |
| [`GreycosSolverEnterpriseService.java`](../../core/src/main/java/ai/greycos/solver/core/enterprise/GreycosSolverEnterpriseService.java) | 138-145 | Local search enterprise method |
| [`ConstructionHeuristicForagerConfig.java`](../../core/src/main/java/ai/greycos/solver/core/config/constructionheuristic/decider/forager/ConstructionHeuristicForagerConfig.java) | 1-54 | CH forager configuration |
| [`LocalSearchForagerConfig.java`](../../core/src/main/java/ai/greycos/solver/core/config/localsearch/decider/forager/LocalSearchForagerConfig.java) | 1-106 | LS forager configuration |
| [`ConstructionHeuristicForager.java`](../../core/src/main/java/ai/greycos/solver/core/impl/constructionheuristic/decider/forager/ConstructionHeuristicForager.java) | 10-19 | CH forager interface |
| [`LocalSearchForager.java`](../../core/src/main/java/ai/greycos/solver/core/impl/localsearch/decider/forager/LocalSearchForager.java) | 14-15 | LS forager interface |
| [`DefaultConstructionHeuristicForager.java`](../../core/src/main/java/ai/greycos/solver/core/impl/constructionheuristic/decider/forager/DefaultConstructionHeuristicForager.java) | 8-108 | Default CH forager implementation |
| [`AcceptedLocalSearchForager.java`](../../core/src/main/java/ai/greycos/solver/core/impl/localsearch/decider/forager/AcceptedLocalSearchForager.java) | 18- | Default LS forager implementation |
| [`DefaultConstructionHeuristicPhaseFactory.java`](../../core/src/main/java/ai/greycos/solver/core/impl/constructionheuristic/DefaultConstructionHeuristicPhaseFactory.java) | 196-225 | CH decider building (community) |
| [`DefaultLocalSearchPhaseFactory.java`](../../core/src/main/java/ai/greycos/solver/core/impl/localsearch/DefaultLocalSearchPhaseFactory.java) | 185-220 | LS decider building (community) |

---

## 5. Key Takeaways

1. **Both are Enterprise-Only**: MULTISTAGE_MOVE and Custom Forager require a valid Greycos Enterprise license
2. **Independent Features**: They serve different purposes and can be used independently
3. **Community Fallback**: The community edition throws `UnsupportedOperationException` when these features are requested
4. **Configuration-Based**: Both are configured through XML or programmatic configuration
5. **Integration Points**: Both integrate through the enterprise service pattern with `loadOrFail()` checks
6. **No Direct Coupling**: There is no direct functional relationship between MULTISTAGE_MOVE and Custom Forager in the codebase
