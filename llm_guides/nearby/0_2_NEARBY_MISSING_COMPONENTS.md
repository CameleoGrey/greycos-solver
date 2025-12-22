# OptaPlanner Nearby Feature - Missing Components Analysis

After thorough analysis of the codebase, I have identified several important components that were not initially covered in the documentation. This document provides details on these missing components and their importance.

## Missing Components Identified

### 1. **NearbySelectionDistributionType Enum**

**Location:** `optaplanner-core-impl/src/main/java/org/optaplanner/core/config/heuristic/selector/common/nearby/NearbySelectionDistributionType.java`

**Purpose:** Defines the available probability distributions for nearby selection.

**Key Details:**
- `BLOCK_DISTRIBUTION`: Only the n nearest are selected with equal probability
- `LINEAR_DISTRIBUTION`: Nearest elements selected with linearly decreasing probability
- `PARABOLIC_DISTRIBUTION`: Nearest elements selected with quadratically decreasing probability
- `BETA_DISTRIBUTION`: Selection according to beta distribution (computationally expensive)

**Importance:** This enum is crucial for configuration and must be properly implemented for the nearby feature to work.

### 2. **NearbyRandomFactory**

**Location:** `optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/selector/common/nearby/NearbyRandomFactory.java`

**Purpose:** Factory class for creating NearbyRandom instances based on configuration.

**Key Features:**
- Creates appropriate random distribution based on NearbySelectionConfig
- Handles parameter validation and defaults
- Ensures only one distribution type is configured at a time
- Provides factory method pattern for instantiation

**Critical Methods:**
```java
public static NearbyRandomFactory create(NearbySelectionConfig nearbySelectionConfig)
public NearbyRandom buildNearbyRandom(boolean randomSelection)
```

### 3. **AbstractNearbyEntitySelector**

**Location:** Not found as a separate class - entities use AbstractNearbySelector directly

**Note:** Entity selectors extend AbstractNearbySelector directly rather than having a separate abstract base class.

### 4. **NearEntityNearbyEntitySelector**

**Location:** `optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/selector/entity/nearby/NearEntityNearbyEntitySelector.java`

**Purpose:** Selects nearby entities based on entity proximity.

**Key Features:**
- Extends AbstractNearbySelector for entities
- Uses entities as both origin and destinations
- Handles discardNearbyIndexZero for entity variables
- Implements EntitySelector interface

**Usage:** When selecting entities near a selected entity.

### 5. **NearValueNearbyValueSelector**

**Location:** `optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/selector/value/nearby/NearValueNearbyValueSelector.java`

**Purpose:** Selects nearby values based on value proximity.

**Key Features:**
- Extends AbstractNearbyValueSelector
- Uses values as both origin and destinations
- Requires EntityIndependentValueSelector
- Implements EntityIndependentValueSelector interface

**Usage:** When selecting values near a selected value.

### 6. **NearSubListNearbyDestinationSelector**

**Location:** `optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/selector/list/nearby/NearSubListNearbyDestinationSelector.java`

**Purpose:** Selects nearby destinations based on subList proximity.

**Key Features:**
- Extends AbstractNearbyDestinationSelector
- Uses subLists as origin, destinations as destinations
- Handles subList-specific logic for first element extraction

### 7. **NearSubListNearbySubListSelector**

**Location:** `optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/selector/list/nearby/NearSubListNearbySubListSelector.java`

**Purpose:** Selects nearby subLists based on subList proximity.

**Key Features:**
- Extends AbstractNearbySelector for subLists
- Uses subLists as both origin and destinations
- Implements SubListSelector interface

### 8. **AbstractNearbyDestinationSelector**

**Location:** `optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/heuristic/selector/list/nearby/AbstractNearbyDestinationSelector.java`

**Purpose:** Abstract base class for destination selectors with nearby functionality.

**Key Features:**
- Extends AbstractNearbySelector
- Provides common functionality for destination-based nearby selection
- Handles ElementDestinationSelector and subList selectors

### 9. **TestNearbyRandom**

**Location:** `optaplanner-core-impl/src/test/java/org/optaplanner/core/impl/testutil/TestNearbyRandom.java`

**Purpose:** Test implementation of NearbyRandom for testing purposes.

**Key Features:**
- Simple implementation that delegates to working random
- Supports configurable overallSizeMaximum
- Implements proper equals() and hashCode() for testing

### 10. **NearbyDistanceMatrixTest**

**Location:** `optaplanner-core-impl/src/test/java/org/optaplanner/core/impl/heuristic/selector/common/nearby/NearbyDistanceMatrixTest.java`

**Purpose:** Comprehensive tests for NearbyDistanceMatrix functionality.

**Key Test Cases:**
- Basic distance matrix construction and lookup
- Handling of same distances (deterministic ordering)
- Missing item computation on demand
- Memory optimization scenarios

## Updated Implementation Checklist

Based on this analysis, here are the components that must be implemented:

### Core Configuration (Priority 1)
- [x] NearbySelectionConfig
- [x] NearbySelectionDistributionType enum

### Core Interfaces (Priority 1)
- [x] NearbyDistanceMeter
- [x] NearbyRandom
- [x] NearbyRandomFactory

### Distance Matrix System (Priority 2)
- [x] NearbyDistanceMatrix
- [x] AbstractNearbyDistanceMatrixDemand
- [x] ValueNearbyDistanceMatrixDemand
- [x] EntityNearbyDistanceMatrixDemand
- [x] ListNearbyDistanceMatrixDemand
- [x] SubListNearbyDistanceMatrixDemand
- [x] SubListNearbySubListMatrixDemand

### Selector Implementations (Priority 2)
- [x] AbstractNearbySelector
- [x] NearEntityNearbyValueSelector
- [x] NearValueNearbyValueSelector
- [x] NearEntityNearbyEntitySelector
- [x] AbstractNearbyDestinationSelector
- [x] NearValueNearbyDestinationSelector
- [x] NearSubListNearbyDestinationSelector
- [x] NearSubListNearbySubListSelector

### Iterator Implementations (Priority 3)
- [x] RandomNearbyIterator
- [x] OriginalNearbyValueIterator
- [x] OriginalNearbyEntityIterator
- [x] OriginalNearbyDestinationIterator
- [x] RandomNearbyDestinationIterator

### Distribution Implementations (Priority 3)
- [x] LinearDistributionNearbyRandom
- [x] BlockDistributionNearbyRandom
- [x] ParabolicDistributionNearbyRandom
- [x] BetaDistributionNearbyRandom

### Integration (Priority 4)
- [x] ValueSelectorFactory integration
- [x] EntitySelectorFactory integration
- [x] DestinationSelectorFactory integration
- [x] SubListSelectorFactory integration

### Testing (Priority 5)
- [x] Configuration tests
- [x] Distance meter tests
- [x] Random distribution tests
- [x] Distance matrix tests
- [x] Selector tests
- [x] Performance tests
- [x] Integration tests

## Critical Implementation Notes

1. **Distribution Type Validation**: The NearbyRandomFactory must ensure only one distribution type is configured at a time.

2. **Parameter Defaults**: Each distribution type has specific default values that must be handled correctly.

3. **Memory Optimization**: The overallSizeMaximum parameter is crucial for limiting memory usage in large problems.

4. **Equality Implementation**: All selector classes must implement proper equals() and hashCode() for caching.

5. **Phase Lifecycle**: Distance matrices must be properly managed through phase lifecycle events.

6. **Mimic Selectors**: All nearby selectors require properly configured mimic selectors as origins.

The documentation has been updated to include all these components. The implementation should follow the priority order above, starting with core configuration and interfaces, then moving to the distance matrix system and selectors.