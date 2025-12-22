# OptaPlanner Nearby Feature - Complete Implementation Details

This document provides the complete implementation details for all classes in the nearby feature, including their relationships and dependencies.

## Package Structure

```
org.optaplanner.core.config.heuristic.selector.common.nearby
├── NearbySelectionConfig.java
├── NearbySelectionDistributionType.java

org.optaplanner.core.impl.heuristic.selector.common.nearby
├── AbstractNearbySelector.java
├── AbstractNearbyDistanceMatrixDemand.java
├── NearbyDistanceMeter.java
├── NearbyDistanceMatrix.java
├── NearbyRandom.java
├── NearbyRandomFactory.java
├── RandomNearbyIterator.java
├── LinearDistributionNearbyRandom.java
├── BlockDistributionNearbyRandom.java
├── ParabolicDistributionNearbyRandom.java
├── BetaDistributionNearbyRandom.java

org.optaplanner.core.impl.heuristic.selector.value.nearby
├── AbstractNearbyValueSelector.java
├── NearEntityNearbyValueSelector.java
├── NearValueNearbyValueSelector.java
├── ValueNearbyDistanceMatrixDemand.java
├── OriginalNearbyValueIterator.java
├── ListValueNearbyDistanceMatrixDemand.java

org.optaplanner.core.impl.heuristic.selector.entity.nearby
├── AbstractNearbyEntitySelector.java
├── NearEntityNearbyEntitySelector.java
├── EntityNearbyDistanceMatrixDemand.java
├── OriginalNearbyEntityIterator.java

org.optaplanner.core.impl.heuristic.selector.list.nearby
├── AbstractNearbyDestinationSelector.java
├── NearValueNearbyDestinationSelector.java
├── NearSubListNearbyDestinationSelector.java
├── NearSubListNearbySubListSelector.java
├── ListNearbyDistanceMatrixDemand.java
├── SubListNearbyDistanceMatrixDemand.java
├── SubListNearbySubListMatrixDemand.java
├── OriginalNearbyDestinationIterator.java
├── RandomNearbyDestinationIterator.java
```

## Core Configuration Classes

### NearbySelectionConfig

**Purpose:** Main configuration class for nearby selection

**Key Fields:**
```java
@EntitySelectorConfig originEntitySelectorConfig;
@SubListSelectorConfig originSubListSelectorConfig;
@ValueSelectorConfig originValueSelectorConfig;
Class<? extends NearbyDistanceMeter> nearbyDistanceMeterClass;
NearbySelectionDistributionType nearbySelectionDistributionType;
```

**Validation Rules:**
- Exactly one origin selector must be specified
- Origin selectors must have mimicSelectorRef
- nearbyDistanceMeterClass must be specified
- Selection order must be ORIGINAL or RANDOM
- Cache type must not be cached

**Distribution Parameters:**
- Block: sizeMinimum, sizeMaximum, sizeRatio, uniformDistributionProbability
- Linear: sizeMaximum
- Parabolic: sizeMaximum
- Beta: alpha, beta

### NearbySelectionDistributionType

**Enum Values:**
```java
BLOCK, LINEAR, PARABOLIC, BETA
```

## Core Interfaces

### NearbyDistanceMeter<O, D>

**Purpose:** Measures distance between origin and destination

**Key Method:**
```java
double getNearbyDistance(O origin, D destination);
```

**Requirements:**
- Stateless implementation
- Distance >= 0.0
- Distance(origin, origin) usually returns 0.0
- Can be asymmetrical

### NearbyRandom

**Purpose:** Strategy pattern for probability distributions

**Key Methods:**
```java
int nextInt(Random random, int nearbySize);
int getOverallSizeMaximum();
```

**Requirements:**
- Returns 0 <= x < nearbySize
- getOverallSizeMaximum() returns max value or Integer.MAX_VALUE

## Distance Matrix System

### NearbyDistanceMatrix<Origin, Destination>

**Purpose:** Caches distance calculations for performance

**Key Features:**
- Stores destinations sorted by distance for each origin
- Uses binary search for efficient insertion
- Implements Supply interface

**Construction:**
```java
NearbyDistanceMatrix(NearbyDistanceMeter<Origin, Destination> nearbyDistanceMeter,
                     int originSize,
                     Function<Origin, Iterator<Destination>> destinationIteratorProvider,
                     ToIntFunction<Origin> destinationSizeFunction)
```

**Key Methods:**
```java
void addAllDestinations(Origin origin);
Object getDestination(Origin origin, int nearbyIndex);
```

### AbstractNearbyDistanceMatrixDemand

**Purpose:** Demand for distance matrix with equality-based caching

**Key Features:**
- Implements equality based on configuration
- Enables reuse of pre-computed distance matrices
- Abstract base for specific demands

**Equality Criteria:**
- meter instances are equal
- nearby randoms represent the same distribution
- child selectors are equal
- replaying selectors are equal

## Random Distribution Implementations

### LinearDistributionNearbyRandom

**Purpose:** Linear probability distribution (closer = higher probability)

**Parameters:** linearDistributionSizeMaximum

**Implementation:**
```java
public int nextInt(Random random, int nearbySize) {
    int sizeMaximum = Math.min(nearbySize, linearDistributionSizeMaximum);
    return random.nextInt(sizeMaximum);
}
```

### BlockDistributionNearbyRandom

**Purpose:** Block-based distribution with configurable parameters

**Parameters:**
- blockDistributionSizeMinimum
- blockDistributionSizeMaximum
- blockDistributionSizeRatio
- blockDistributionUniformDistributionProbability

**Implementation:** Complex block-based selection logic

### ParabolicDistributionNearbyRandom

**Purpose:** Parabolic probability distribution

**Parameters:** parabolicDistributionSizeMaximum

**Implementation:** Uses parabolic curve for probability distribution

### BetaDistributionNearbyRandom

**Purpose:** Beta distribution for complex probability curves

**Parameters:** betaDistributionAlpha, betaDistributionBeta

**Implementation:** Uses beta distribution from Apache Commons Math

## Selector Implementations

### AbstractNearbySelector

**Purpose:** Base class for all nearby selectors

**Key Features:**
- Manages distance matrix lifecycle
- Handles phase events (started/ended)
- Provides equality based on configuration

**Lifecycle:**
```java
public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    nearbyDistanceMatrix = phaseScope.getScoreDirector().getSupplyManager()
        .demand(nearbyDistanceMatrixDemand);
}

public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    phaseScope.getScoreDirector().getSupplyManager()
        .cancel(nearbyDistanceMatrixDemand);
    nearbyDistanceMatrix = null;
}
```

### NearEntityNearbyValueSelector

**Purpose:** Selects nearby values based on entity proximity

**Usage:** When selecting values near a selected entity

**Key Features:**
- Extends AbstractNearbyValueSelector
- Uses entity as origin, values as destinations
- Handles discardNearbyIndexZero for entity variables

### NearValueNearbyValueSelector

**Purpose:** Selects nearby values based on value proximity

**Usage:** When selecting values near a selected value

**Key Features:**
- Extends AbstractNearbyValueSelector
- Uses values as both origin and destinations
- Requires EntityIndependentValueSelector

### NearEntityNearbyEntitySelector

**Purpose:** Selects nearby entities based on entity proximity

**Usage:** When selecting entities near a selected entity

**Key Features:**
- Extends AbstractNearbyEntitySelector
- Uses entities as both origin and destinations

## Iterator Implementations

### RandomNearbyIterator

**Purpose:** Iterator for random nearby selection

**Key Features:**
- Uses NearbyRandom for index selection
- Reuses origin from replaying iterator
- Supports discarding index zero

### OriginalNearbyValueIterator

**Purpose:** Iterator for original order nearby selection

**Key Features:**
- Iterates through destinations in distance order
- Reuses origin from replaying iterator
- Maintains original selection order

## Integration with Selector Factories

### ValueSelectorFactory

**Nearby Integration:**
```java
if (config.getNearbySelectionConfig() != null) {
    config.getNearbySelectionConfig().validateNearby(resolvedCacheType, resolvedSelectionOrder);
    valueSelector = applyNearbySelection(configPolicy, entityDescriptor, minimumCacheType,
        resolvedSelectionOrder, valueSelector);
}
```

**Nearby Selection Application:**
```java
private ValueSelector<Solution_> applyNearbySelection(HeuristicConfigPolicy<Solution_> configPolicy,
        EntityDescriptor<Solution_> entityDescriptor, SelectionCacheType minimumCacheType,
        SelectionOrder resolvedSelectionOrder, ValueSelector<Solution_> valueSelector) {
    NearbySelectionConfig nearbySelectionConfig = config.getNearbySelectionConfig();
    NearbyDistanceMeter<?, ?> nearbyDistanceMeter = configPolicy.getClassInstanceCache()
        .newInstance(nearbySelectionConfig, "nearbyDistanceMeterClass", 
                     nearbySelectionConfig.getNearbyDistanceMeterClass());
    NearbyRandom nearbyRandom = NearbyRandomFactory.create(nearbySelectionConfig)
        .buildNearbyRandom(randomSelection);
    
    if (nearbySelectionConfig.getOriginEntitySelectorConfig() != null) {
        return new NearEntityNearbyValueSelector<>(valueSelector, originEntitySelector, 
            nearbyDistanceMeter, nearbyRandom, randomSelection);
    } else if (nearbySelectionConfig.getOriginValueSelectorConfig() != null) {
        return new NearValueNearbyValueSelector<>(valueSelector, originValueSelector, 
            nearbyDistanceMeter, nearbyRandom, randomSelection);
    }
}
```

## Test Classes

### NearbySelectionConfigTest

**Purpose:** Tests configuration validation and factory methods

**Key Tests:**
- Validation of required fields
- Multiple origin selector detection
- Distribution parameter validation
- Factory method testing

### Distribution Random Tests

**Purpose:** Tests probability distribution implementations

**Test Classes:**
- LinearDistributionNearbyRandomTest
- BlockDistributionNearbyRandomTest
- ParabolicDistributionNearbyRandomTest
- BetaDistributionNearbyRandomTest

### NearbyDistanceMatrixTest

**Purpose:** Tests distance matrix functionality

**Key Tests:**
- Distance calculation and caching
- Binary search insertion
- Memory usage optimization

## Performance Characteristics

### Memory Usage
- Distance matrices store all destinations for each origin
- Memory usage: O(origins × destinations)
- Use getOverallSizeMaximum() to limit memory usage

### Computation Time
- Distance matrix construction: O(origins × destinations × log(destinations))
- Distance lookup: O(1) after construction
- Selection: O(1) for random, O(n) for original order

### Distribution Performance
- Linear: Fastest (simple random)
- Block: Medium (block calculation)
- Parabolic: Medium (mathematical function)
- Beta: Slowest (statistical distribution)

## Error Handling

### Configuration Errors
- Missing origin selector
- Invalid distance meter class
- Conflicting distribution parameters
- Invalid cache type/selection order combinations

### Runtime Errors
- Distance matrix construction failures
- Invalid distance calculations
- Memory allocation issues

### Validation
- Configuration validation in validateNearby()
- Parameter validation in distribution factories
- Type safety through generics

This complete implementation provides a robust foundation for nearby selection in OptaPlanner, with proper separation of concerns, performance optimization, and comprehensive error handling.