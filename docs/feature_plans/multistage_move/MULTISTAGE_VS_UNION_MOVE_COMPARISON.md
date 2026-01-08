# Multistage Move vs Union Move Comparison

## Executive Summary

**No, multistage move is NOT the same as union move** in GreyCOS Solver. They are fundamentally different features with different purposes, implementations, and availability.

---

## Key Differences

### 1. Purpose

| Aspect | Multistage Move | Union Move |
|--------|----------------|------------|
| **Primary Goal** | Applies different move selection strategies in **stages** during the solving process | Simply **combines** multiple move selectors into one iterator |
| **Approach** | Dynamic, stage-based selection | Static combination of selectors |
| **Use Case** | Adaptive solving that changes strategy based on solving progress | Combining multiple move types (e.g., change + swap) in one selector |

### 2. Availability

| Feature | Community Edition | Enterprise Edition |
|---------|-------------------|-------------------|
| **Multistage Move** | ❌ Not available (throws `UnsupportedOperationException`) | ✅ Available |
| **Union Move** | ✅ Available | ✅ Available |

**Multistage Move Enterprise Restriction**:
- Throws [`UnsupportedOperationException`](../../core/src/main/java/ai/greycos/solver/core/impl/partitionedsearch/DefaultGreyCOSSolverEnterpriseService.java:287-299) in community edition
- Requires valid GreyCOS Enterprise license
- Activated via [`GreyCOSSolverEnterpriseService.loadOrFail()`](../../core/src/main/java/ai/greycos/solver/core/enterprise/GreyCOSSolverEnterpriseService.java:85-112)

### 3. Implementation

#### Multistage Move
- **Configuration Classes**:
  - [`MultistageMoveSelectorConfig`](../../core/src/main/java/ai/greycos/solver/core/config/heuristic/selector/move/generic/MultistageMoveSelectorConfig.java:1-101) - For basic planning variables
  - [`ListMultistageMoveSelectorConfig`](../../core/src/main/java/ai/greycos/solver/core/config/heuristic/selector/move/generic/list/ListMultistageMoveSelectorConfig.java:1-69) - For list planning variables
- **Required**: Custom `stageProviderClass` that defines move selection stages
- **Enterprise Service Methods** (in [`GreyCOSSolverEnterpriseService.java:209-215`](../../core/src/main/java/ai/greycos/solver/core/enterprise/GreyCOSSolverEnterpriseService.java:209-215)):
  ```java
  <Solution_>
      AbstractMoveSelectorFactory<Solution_, MultistageMoveSelectorConfig>
          buildBasicMultistageMoveSelectorFactory(MultistageMoveSelectorConfig moveSelectorConfig);

  <Solution_>
      AbstractMoveSelectorFactory<Solution_, ListMultistageMoveSelectorConfig>
          buildListMultistageMoveSelectorFactory(
              ListMultistageMoveSelectorConfig moveSelectorConfig);
  ```

#### Union Move
- **Configuration Class**: [`UnionMoveSelectorConfig`](../../core/src/main/java/ai/greycos/solver/core/config/heuristic/selector/move/composite/UnionMoveSelectorConfig.java:1-261)
- **Implementation**: [`UnionMoveSelector`](../../core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/composite/UnionMoveSelector.java:1-145)
- **Behavior**: Simple concatenation of child selectors' moves
  ```java
  /**
   * A {@link CompositeMoveSelector} that unions 2 or more {@link MoveSelector}s.
   *
   * <p>For example: a union of {A, B, C} and {X, Y} will result in {A, B, C, X, Y}.
   *
   * <p>Warning: there is no duplicated {@link Move} check, so union of {A, B, C} and {B, D} will
   * result in {A, B, C, B, D}.
   */
  ```

### 4. Behavior

#### Multistage Move
- **Sequential Stage-Based Selection**: Uses different move selection strategies at different points in solving
- **Dynamic Adaptation**: Changes which moves to generate based on current solving stage
- **Custom Logic**: User-defined stages via `stageProviderClass`

#### Union Move
- **Simultaneous Combination**: Provides all moves from all child selectors at once
- **Optional Randomization**: Supports `randomSelection` mode with optional probability weighting
- **Iterator Types**:
  - Sequential iterator (when `randomSelection = false`)
  - [`UniformRandomUnionMoveIterator`](../../core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/composite/UniformRandomUnionMoveIterator.java:1) (when `randomSelection = true` and no weight factory)
  - [`BiasedRandomUnionMoveIterator`](../../core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/composite/BiasedRandomUnionMoveIterator.java:1) (when probability weight factory is provided)

### 5. Configuration

#### Multistage Move Configuration

**XML Configuration**:
```xml
<unionMoveSelector>
  <multistageMoveSelector>
    <stageProviderClass>com.example.MyStageProvider</stageProviderClass>
    <entityClass>com.example.MyEntity</entityClass>
    <variableName>planningVariable</variableName>
  </multistageMoveSelector>
</unionMoveSelector>
```

**Required Properties**:
- `stageProviderClass` (Class<?>) - Custom class that provides move selection stages
- `entityClass` (Class<?>) - Optional: Target entity class
- `variableName` (String) - Optional: Target planning variable name

#### Union Move Configuration

**XML Configuration**:
```xml
<unionMoveSelector>
  <changeMoveSelector/>
  <swapMoveSelector/>
  <pillarSwapMoveSelector/>
</unionMoveSelector>
```

**Properties**:
- `moveSelectorList` - List of child move selector configurations
- `selectorProbabilityWeightFactoryClass` - Optional: Custom probability weight factory for biased random selection

### 6. Use Cases

#### Multistage Move Use Cases
- **Adaptive Solving**: Start with aggressive moves, then switch to finer-grained moves
- **Multi-Phase Optimization**: Different optimization strategies for different problem regions
- **Time-Based Stages**: Change move selection based on solving time or progress
- **Enterprise-Only**: Requires advanced, adaptive move selection strategies

#### Union Move Use Cases
- **Move Type Combination**: Combine change, swap, and pillar moves in one selector
- **Comprehensive Neighborhood**: Explore multiple move types simultaneously
- **Flexible Configuration**: Easy to add/remove move types from neighborhood
- **Community-Friendly**: Available to all users without enterprise license

---

## Code Examples

### Multistage Move (Enterprise Only)

```java
// Custom stage provider
public class MyStageProvider implements StageProvider<MySolution> {
    @Override
    public List<MoveSelector<MySolution>> createStages(
            HeuristicConfigPolicy<MySolution> configPolicy) {
        List<MoveSelector<MySolution>> stages = new ArrayList<>();
        
        // Stage 1: Aggressive exploration
        stages.add(new ChangeMoveSelector(...));
        
        // Stage 2: Refinement
        stages.add(new SwapMoveSelector(...));
        
        return stages;
    }
}

// Configuration
MultistageMoveSelectorConfig config = new MultistageMoveSelectorConfig()
    .withStageProviderClass(MyStageProvider.class)
    .withEntityClass(MyEntity.class)
    .withVariableName("planningVariable");
```

### Union Move (Available to All)

```java
// Configuration
UnionMoveSelectorConfig config = new UnionMoveSelectorConfig()
    .withMoveSelectors(
        new ChangeMoveSelectorConfig(),
        new SwapMoveSelectorConfig(),
        new PillarSwapMoveSelectorConfig()
    );

// With probability weighting
UnionMoveSelectorConfig weightedConfig = new UnionMoveSelectorConfig()
    .withMoveSelectors(
        new ChangeMoveSelectorConfig(),
        new SwapMoveSelectorConfig()
    )
    .withSelectorProbabilityWeightFactoryClass(
        MyProbabilityWeightFactory.class);
```

---

## Integration Points

### Multistage Move Integration
- **Enterprise Service**: Activated via [`GreyCOSSolverEnterpriseService.loadOrFail(Feature.MULTISTAGE_MOVE)`](../../core/src/main/java/ai/greycos/solver/core/enterprise/GreyCOSSolverEnterpriseService.java:85-112)
- **Factory Method**: [`MoveSelectorFactory.create()`](../../core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/MoveSelectorFactory.java:104-116) checks for `MultistageMoveSelectorConfig` and loads enterprise service

### Union Move Integration
- **Standard Factory**: Built via [`UnionMoveSelectorFactory`](../../core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/composite/UnionMoveSelectorFactory.java:1-90)
- **No License Check**: Available in both community and enterprise editions
- **Composite Pattern**: Extends [`CompositeMoveSelector`](../../core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/composite/CompositeMoveSelector.java:1)

---

## Performance Considerations

### Multistage Move
- **Adaptive Performance**: Can optimize by using appropriate moves for each stage
- **Stage Overhead**: Requires stage transition logic and state management
- **Enterprise Optimization**: Potentially optimized for enterprise workloads

### Union Move
- **Simple Overhead**: Minimal overhead for combining iterators
- **No Duplication Check**: May generate duplicate moves (as documented in warning)
- **Randomization Cost**: Optional randomization adds small overhead for random selection

---

## Summary Table

| Aspect | Multistage Move | Union Move |
|--------|----------------|------------|
| **Edition** | Enterprise only | Community + Enterprise |
| **Purpose** | Dynamic, staged move selection | Static combination of move selectors |
| **Configuration** | `stageProviderClass` required | List of child move selectors |
| **Behavior** | Changes strategy per stage | Combines all moves simultaneously |
| **Randomization** | Depends on stage configuration | Optional uniform/biased random |
| **Complexity** | High (custom stages) | Low (simple concatenation) |
| **Use Case** | Adaptive solving | Comprehensive neighborhood |
| **License Requirement** | Enterprise license required | No license required |

---

## Conclusion

Multistage move and union move are **fundamentally different features**:

- **Multistage Move**: A sophisticated, dynamic move selection strategy for enterprise users that adapts move selection based on solving stages
- **Union Move**: A basic composite selector available to all users for combining multiple move selectors into one

They serve completely different purposes in the solving process and are not interchangeable. Multistage move provides adaptive, stage-based move selection (enterprise-only), while union move provides static combination of multiple move types (available to all).
