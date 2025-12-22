# OptaPlanner Nearby Feature Reimplementation Guide

This guide provides detailed instructions for reimplementing the nearby feature in OptaPlanner. The nearby feature allows for selecting values that are "near" to a given origin entity or value, which is useful for local search optimization.

## Overview

The nearby feature consists of several key components:

1. **Configuration** - Defines how nearby selection should work
2. **Distance Measurement** - Calculates distances between entities/values
3. **Random Selection** - Implements probability distributions for nearby selection
4. **Distance Matrix** - Caches distance calculations for performance
5. **Selectors** - The actual selection logic for different use cases

## Core Components

### 1. Configuration Classes

#### NearbySelectionConfig
Location: `optaplanner-core-impl/src/main/java/org/optaplanner/core/config/heuristic/selector/common/nearby/NearbySelectionConfig.java`

This is the main configuration class that defines:
- Origin selector (entity, subList, or value selector)
- Distance meter class
- Distribution type (linear, block, parabolic, beta)
- Distribution parameters

**Key Methods:**
- `validateNearby()` - Validates configuration
- `inherit()` - Inherits configuration from parent
- `copyConfig()` - Creates a copy

**Required Fields:**
- Exactly one of: `originEntitySelectorConfig`, `originSubListSelectorConfig`, `originValueSelectorConfig`
- `nearbyDistanceMeterClass` - Must implement `NearbyDistanceMeter`
- `nearbySelectionDistributionType` - Distribution type

### 2. Core Interfaces

#### NearbyDistanceMeter
Location: `optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/selector/common/nearby/NearbyDistanceMeter.java`

```java
@FunctionalInterface
public interface NearbyDistanceMeter<O, D> {
    double getNearbyDistance(O origin, D destination);
}
```

**Purpose:** Measures distance between origin and destination objects.

**Implementation Requirements:**
- Stateless (solver may reuse instances)
- Distance can be in any unit (meters, seconds, etc.)
- Can be asymmetrical (distance A→B ≠ distance B→A)
- Should return 0.0 when origin == destination

#### NearbyRandom
Location: `optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/selector/common/nearby/NearbyRandom.java`

```java
public interface NearbyRandom {
    int nextInt(Random random, int nearbySize);
    int getOverallSizeMaximum();
}
```

**Purpose:** Strategy pattern for selecting nearby indices according to probability distributions.

**Implementation Requirements:**
- `nextInt()` returns 0 ≤ x < nearbySize
- `getOverallSizeMaximum()` returns max possible value or Integer.MAX_VALUE

### 3. Distance Matrix

#### NearbyDistanceMatrix
Location: `optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/selector/common/nearby/NearbyDistanceMatrix.java`

**Purpose:** Caches distance calculations for performance optimization.

**Key Features:**
- Stores destinations sorted by distance for each origin
- Uses binary search for efficient insertion
- Implements `Supply` interface for dependency injection

**Construction:**
```java
NearbyDistanceMatrix(NearbyDistanceMeter<Origin, Destination> nearbyDistanceMeter, 
                     int originSize,
                     Function<Origin, Iterator<Destination>> destinationIteratorProvider,
                     ToIntFunction<Origin> destinationSizeFunction)
```

### 4. Random Implementations

#### LinearDistributionNearbyRandom
- Linear probability distribution
- Closer items have higher probability
- Parameters: `linearDistributionSizeMaximum`

#### BlockDistributionNearbyRandom
- Block-based distribution with configurable parameters
- Parameters: `blockDistributionSizeMinimum`, `blockDistributionSizeMaximum`, `blockDistributionSizeRatio`, `blockDistributionUniformDistributionProbability`

#### ParabolicDistributionNearbyRandom
- Parabolic probability distribution
- Parameters: `parabolicDistributionSizeMaximum`

#### BetaDistributionNearbyRandom
- Beta distribution for more complex probability curves
- Parameters: `betaDistributionAlpha`, `betaDistributionBeta`

### 5. Selector Implementations

#### AbstractNearbySelector
Location: `optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/selector/common/nearby/AbstractNearbySelector.java`

**Purpose:** Base class for all nearby selectors.

**Key Features:**
- Manages distance matrix lifecycle
- Handles phase events (started/ended)
- Provides equality based on configuration

#### NearEntityNearbyValueSelector
Location: `optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/selector/value/nearby/NearEntityNearbyValueSelector.java`

**Purpose:** Selects nearby values based on entity proximity.

**Usage:** When you want to select values that are "near" to a selected entity.

#### NearValueNearbyValueSelector
Location: `optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/selector/value/nearby/NearValueNearbyValueSelector.java`

**Purpose:** Selects nearby values based on value proximity.

**Usage:** When you want to select values that are "near" to a selected value.

### 6. Distance Matrix Demands

#### AbstractNearbyDistanceMatrixDemand
Location: `optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/selector/common/nearby/AbstractNearbyDistanceMatrixDemand.java`

**Purpose:** Demand for distance matrix supply with equality-based caching.

**Key Features:**
- Implements equality based on configuration
- Enables reuse of pre-computed distance matrices
- Extends `Demand<NearbyDistanceMatrix<Origin_, Destination_>>`

#### ValueNearbyDistanceMatrixDemand
Location: `optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/selector/value/nearby/ValueNearbyDistanceMatrixDemand.java`

**Purpose:** Specific demand for value-based nearby selection.

## Implementation Steps

### Step 1: Create Configuration Classes

1. **NearbySelectionConfig**
   - Extend `SelectorConfig<NearbySelectionConfig>`
   - Add XML annotations for configuration
   - Implement validation logic
   - Add with methods for fluent API

2. **NearbySelectionDistributionType**
   - Enum for distribution types
   - Add new enum values as needed

### Step 2: Implement Core Interfaces

1. **NearbyDistanceMeter**
   - Create concrete implementations for your domain
   - Ensure stateless design
   - Implement distance calculation logic

2. **NearbyRandom**
   - Implement probability distributions
   - Create factory class for instantiation
   - Handle parameter validation

### Step 3: Create Distance Matrix

1. **NearbyDistanceMatrix**
   - Implement distance caching
   - Use efficient data structures
   - Implement binary search for insertion

2. **AbstractNearbyDistanceMatrixDemand**
   - Implement equality-based caching
   - Extend for specific use cases

### Step 4: Implement Selectors

1. **AbstractNearbySelector**
   - Handle lifecycle management
   - Implement equality and hashing
   - Manage distance matrix supply

2. **Specific Selector Implementations**
   - NearEntityNearbyValueSelector
   - NearValueNearbyValueSelector
   - List-specific selectors as needed

### Step 5: Integrate with Selector Factories

1. **ValueSelectorFactory**
   - Add nearby selection support
   - Handle configuration validation
   - Create appropriate selector instances

2. **EntitySelectorFactory**
   - Add entity-based nearby selection
   - Handle entity-specific logic

### Step 6: Add Tests

1. **Configuration Tests**
   - Test validation logic
   - Test inheritance and copying

2. **Distance Meter Tests**
   - Test distance calculations
   - Test edge cases

3. **Selector Tests**
   - Test selection logic
   - Test performance characteristics

## Usage Examples

### Basic Configuration

```xml
<valueSelector>
    <nearbySelection>
        <originEntitySelector>
            <mimicSelectorRef>entitySelector</mimicSelectorRef>
        </originEntitySelector>
        <nearbyDistanceMeterClass>com.example.MyDistanceMeter</nearbyDistanceMeterClass>
        <nearbySelectionDistributionType>LINEAR</nearbySelectionDistributionType>
        <linearDistributionSizeMaximum>10</linearDistributionSizeMaximum>
    </nearbySelection>
</valueSelector>
```

### Java Configuration

```java
NearbySelectionConfig nearbySelectionConfig = new NearbySelectionConfig()
    .withOriginEntitySelectorConfig(EntitySelectorConfig.newMimicSelectorConfig("entitySelector"))
    .withNearbyDistanceMeterClass(MyDistanceMeter.class)
    .withNearbySelectionDistributionType(NearbySelectionDistributionType.LINEAR)
    .withLinearDistributionSizeMaximum(10);
```

## Performance Considerations

1. **Distance Matrix Caching**
   - Distance matrices are expensive to compute
   - Use equality-based caching for reuse
   - Consider memory usage for large problems

2. **Distribution Selection**
   - Linear distribution is most efficient
   - Beta distribution is most flexible but computationally expensive
   - Choose appropriate distribution for your use case

3. **Memory Usage**
   - Distance matrices store all destinations for each origin
   - Use `getOverallSizeMaximum()` to limit memory usage
   - Consider problem size when configuring distributions

## Common Pitfalls

1. **Missing Origin Selector**
   - Must specify exactly one origin selector
   - Must use mimic selectors for proper integration

2. **Incorrect Distance Meter**
   - Must implement `NearbyDistanceMeter` interface
   - Must be stateless
   - Must return valid distance values

3. **Cache Type Conflicts**
   - Nearby selection requires `SelectionOrder.ORIGINAL` or `SelectionOrder.RANDOM`
   - Cannot use cached selection types
   - Must validate configuration properly

4. **Distribution Parameter Conflicts**
   - Cannot specify multiple distribution types
   - Must provide required parameters for chosen distribution
   - Must validate parameter ranges

## Integration Points

1. **Move Selectors**
   - Nearby selectors integrate with move selectors
   - Used in local search for neighborhood exploration
   - Affects move generation performance

2. **Phase Lifecycle**
   - Distance matrices created at phase start
   - Cleaned up at phase end
   - Proper lifecycle management required

3. **Dependency Injection**
   - Uses OptaPlanner's supply system
   - Distance matrices supplied on demand
   - Equality-based caching for performance

This guide provides the foundation for implementing the nearby feature. Adapt the implementation based on your specific requirements and domain characteristics.