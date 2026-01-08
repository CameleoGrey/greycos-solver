# GreyCOS vs OptaPlanner Nearby Selection - Implementation Analysis

**Date**: 2026-01-08
**Status**: Critical Issues Identified
**Priority**: High - Breaks nearby selection functionality

---

## Executive Summary

Analysis of the greycos nearby selection implementation reveals **critical architectural differences** from OptaPlanner that fundamentally break the nearby selection algorithm. The primary issue is that greycos retrieves a new origin for each nearby value selection instead of maintaining a constant origin, causing values to be selected relative to different origins rather than the same one.

**Impact**: Nearby selection does not work as intended - the "nearby" property is lost because each value is selected relative to a different origin entity.

---

## Table of Contents

1. [Critical Issues](#critical-issues)
2. [Medium Priority Issues](#medium-priority-issues)
3. [Root Cause Analysis](#root-cause-analysis)
4. [Detailed Technical Comparison](#detailed-technical-comparison)
5. [Recommended Fixes](#recommended-fixes)
6. [Testing Strategy](#testing-strategy)

---

## Critical Issues

### 🔴 Issue #1: Origin Retrieved on Every next() Call

**Location**: `NearEntityNearbyValueSelector.OriginalNearbyValueIterator` (line 173-179)

**Current Implementation**:
```java
// greycos-solver/core/.../NearEntityNearbyValueSelector.java:173-179
private class OriginalNearbyValueIterator implements Iterator<Object> {
    @Override
    public Object next() {
        // BUG: Retrieves origin on EVERY call to next()
        Object origin = originEntitySelector.iterator().next();
        Object result = distanceMatrix.getDestination(origin, index);
        index++;
        return result;
    }
}
```

**OptaPlanner Reference**:
```java
// optaplanner/.../OriginalNearbyValueIterator.java:47-75
private void selectOrigin() {
    if (originSelected) {
        return;  // Already selected - don't select again
    }
    originIsNotEmpty = replayingIterator.hasNext();
    origin = replayingIterator.next();  // Select ONCE
    originSelected = true;
}

@Override
public Object next() {
    selectOrigin();  // Origin is cached and reused
    Object next = nearbyDistanceMatrix.getDestination(origin, nextNearbyIndex);
    nextNearbyIndex++;
    return next;
}
```

**Problem**:
- GreyCOS calls `originEntitySelector.iterator().next()` on **every invocation** of `next()`
- Creates a new iterator each time via `iterator()` call
- Without a replaying selector, this likely returns a **different origin** each time
- Results in selecting value 1 near origin A, value 2 near origin B, value 3 near origin C...

**Expected Behavior**:
- Select origin **once** when iterator is created or on first `next()` call
- Cache the origin
- All subsequent `next()` calls return values nearby to the **same cached origin**

**Severity**: 🔴 **CRITICAL** - Completely breaks nearby selection semantics

---

### 🔴 Issue #2: Missing Replaying/Mimic Selector Pattern

**Location**: `NearEntityNearbyValueSelector` constructor and field declarations

**Current Implementation**:
```java
// greycos-solver/core/.../NearEntityNearbyValueSelector.java:25-35
public final class NearEntityNearbyValueSelector<Solution_> extends AbstractNearbyValueSelector<Solution_> {
    private final @NonNull EntitySelector<Solution_> originEntitySelector;

    public NearEntityNearbyValueSelector(
        @NonNull IterableValueSelector<Solution_> childValueSelector,
        @NonNull EntitySelector<Solution_> originEntitySelector,
        // ... other params
    ) {
        super(childValueSelector, nearbyDistanceMeter, nearbyRandom, randomSelection, null);
        this.originEntitySelector = originEntitySelector;  // Stored as-is
        phaseLifecycleSupport.addEventListener(originEntitySelector);
    }
}
```

**OptaPlanner Reference**:
```java
// optaplanner/.../AbstractNearbySelector.java:28-54
public abstract class AbstractNearbySelector<Solution_, ChildSelector_, ReplayingSelector_> {
    protected final ChildSelector_ childSelector;
    protected final ReplayingSelector_ replayingSelector;  // Replaying selector

    protected AbstractNearbySelector(
        ChildSelector_ childSelector,
        Object replayingSelector,  // Passed as Object, cast to replaying type
        NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
        NearbyRandom nearbyRandom,
        boolean randomSelection
    ) {
        this.childSelector = childSelector;
        this.replayingSelector = castReplayingSelector(replayingSelector);  // Cast to replaying
        // ...
    }

    protected abstract ReplayingSelector_ castReplayingSelector(Object uncastReplayingSelector);
}

// optaplanner/.../NearEntityNearbyValueSelector.java:52-59
@Override
protected EntitySelector<Solution_> castReplayingSelector(Object uncastReplayingSelector) {
    if (!(uncastReplayingSelector instanceof MimicReplayingEntitySelector)) {
        throw new IllegalStateException("Nearby value selector (" + this +
                ") did not receive a replaying entity selector (" + uncastReplayingSelector + ").");
    }
    return (EntitySelector<Solution_>) uncastReplayingSelector;
}
```

**What is a Replaying Selector?**

From OptaPlanner documentation (nearby-selection-feature.md:549-586):

> The **MimicSelector** pattern ensures coordination between the origin selector and the nearby selector:
> 1. A selector is defined with an ID: `<entitySelector id="entitySelector1"/>`
> 2. A nearby selector references it: `<originEntitySelector mimicSelectorRef="entitySelector1"/>`
> 3. The origin selector is "replayed" to provide origins for distance calculation

**How Replaying Works**:
- **Recording Selector**: The original selector with an ID
- **Replaying Selector**: A mimic that "replays" the same value until the recording selector advances
- When you call `replayingIterator.next()` multiple times **without advancing the recording selector**, it returns the **same value**
- This ensures all nearby values are selected for the **same origin**

**Problem in GreyCOS**:
- No replaying selector - just stores `originEntitySelector` directly
- No validation that the selector is a replaying selector
- No mechanism to ensure origin stays constant during nearby value iteration

**Severity**: 🔴 **CRITICAL** - Required infrastructure missing for proper nearby selection

---

### 🔴 Issue #3: Iterator Construction Pattern Mismatch

**Location**: `NearEntityNearbyValueSelector.iterator()` method

**Current Implementation**:
```java
// greycos-solver/core/.../NearEntityNearbyValueSelector.java:76-82
@Override
public @NonNull Iterator<Object> iterator(@NonNull Object entity) {
    if (randomSelection) {
        return new RandomNearbyValueIterator(workingRandom, entity);
    } else {
        return new OriginalNearbyValueIterator(entity);  // Passes entity
    }
}

// Iterator constructor:
public OriginalNearbyValueIterator(@Nullable Object entity) {
    this.entity = entity;
    this.nearbySize = (int) childValueSelector.getSize(entity);
}
```

**OptaPlanner Reference**:
```java
// optaplanner/.../NearEntityNearbyValueSelector.java:107-116
@Override
public Iterator<Object> iterator(Object entity) {
    Iterator<Object> replayingOriginEntityIterator = replayingSelector.iterator();  // Get replaying iterator
    if (!randomSelection) {
        return new OriginalNearbyValueIterator(nearbyDistanceMatrix,
            replayingOriginEntityIterator,  // Pass replaying iterator
            childSelector.getSize(entity),
            discardNearbyIndexZero);
    } else {
        return new RandomNearbyIterator(nearbyDistanceMatrix, nearbyRandom, workingRandom,
            replayingOriginEntityIterator,  // Pass replaying iterator
            childSelector.getSize(entity),
            discardNearbyIndexZero);
    }
}
```

**Key Differences**:

| Aspect | GreyCOS | OptaPlanner |
|--------|---------|-------------|
| What's passed | `entity` (the entity to find values for) | `replayingOriginEntityIterator` (iterator that provides constant origin) |
| Origin retrieval | `originEntitySelector.iterator().next()` on every call | `replayingIterator.next()` once, then cached |
| Origin caching | None | `originSelected` flag prevents re-selection |
| Iterator type | Regular `EntitySelector.iterator()` | `MimicReplayingEntitySelector.iterator()` |

**Problem**:
- GreyCOS passes the wrong parameter (entity instead of replaying iterator)
- Iterator retrieves origin fresh each time instead of caching
- Directly causes Issue #1

**Severity**: 🔴 **CRITICAL** - Direct architectural mismatch causing incorrect behavior

---

## Medium Priority Issues

### ⚠️ Issue #4: Missing `discardNearbyIndexZero` Flag

**Location**: `NearEntityNearbyValueSelector` class

**OptaPlanner Implementation**:
```java
// optaplanner/.../NearEntityNearbyValueSelector.java:36-44
private final boolean discardNearbyIndexZero;

public NearEntityNearbyValueSelector(
    ValueSelector<Solution_> childValueSelector,
    EntitySelector<Solution_> originEntitySelector,
    // ...
) {
    // If value type is assignable from entity type, discard index 0
    this.discardNearbyIndexZero = childValueSelector.getVariableDescriptor()
        .getVariablePropertyType()
        .isAssignableFrom(originEntitySelector.getEntityDescriptor().getEntityClass());
}

// In iterator construction:
this.nextNearbyIndex = discardNearbyIndexZero ? 1 : 0;
```

**Purpose**:
- In swap moves, the origin entity might appear in the destination list
- The origin entity will always be at index 0 (distance to itself = 0)
- Discarding index 0 prevents selecting the same entity as both origin and destination
- Example: When swapping entity A, don't consider "swap A with A" as a move

**Missing in GreyCOS**:
```java
// greycos-solver/core/.../NearEntityNearbyValueSelector.java
// No discardNearbyIndexZero field
// nextNearbyIndex always starts at 0
```

**Impact**:
- May generate invalid moves that swap an entity with itself
- Wasted computation evaluating self-swaps (always score delta = 0)
- Move selector may include meaningless moves

**Severity**: ⚠️ **MEDIUM** - Affects move quality and efficiency, not correctness of nearby selection itself

**Example Scenario**:
```java
// VRP example: assigning customer to vehicle
// If Customer extends Vehicle (or both extend same type):
Entity: Customer A (origin)
Values: [Customer A, Customer B, Customer C, ...]  // A appears in value list
Distances: [0.0, 5.2, 12.8, ...]  // Distance from A to A is 0

// Without discardNearbyIndexZero:
Index 0 → Customer A (distance 0) - INVALID: assign customer A to itself

// With discardNearbyIndexZero:
Start at index 1 → Customer B (distance 5.2) - Valid move
```

---

### ⚠️ Issue #5: Distance Matrix Lifecycle Management

**Location**: `AbstractNearbyValueSelector` constructor

**Current Implementation**:
```java
// greycos-solver/core/.../AbstractNearbyValueSelector.java:41-73
protected AbstractNearbyValueSelector(
    @NonNull IterableValueSelector<Solution_> childValueSelector,
    @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
    // ...
) {
    // Creates distance matrix directly in constructor
    this.distanceMatrix = new NearbyDistanceMatrix<>(
        castedDistanceMeter,
        100,  // Initial capacity estimate
        origin -> childValueSelector.iterator(origin),
        origin -> (int) childValueSelector.getSize(origin)
    );
}
```

**OptaPlanner Implementation**:
```java
// optaplanner/.../AbstractNearbySelector.java:40-54
protected AbstractNearbySelector(
    ChildSelector_ childSelector,
    Object replayingSelector,
    NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
    // ...
) {
    // Creates a DEMAND for the distance matrix (not the matrix itself)
    this.nearbyDistanceMatrixDemand = createDemand();
    // ...
}

// Matrix is created lazily via SupplyManager:
@Override
public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
    // Demand matrix from SupplyManager (may return existing instance)
    nearbyDistanceMatrix = (NearbyDistanceMatrix<Object, Object>)
        phaseScope.getScoreDirector().getSupplyManager().demand(nearbyDistanceMatrixDemand);
}

@Override
public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);
    // Cancel demand, allow GC to free memory
    phaseScope.getScoreDirector().getSupplyManager().cancel(nearbyDistanceMatrixDemand);
    nearbyDistanceMatrix = null;
}
```

**SupplyManager Pattern Benefits**:

From OptaPlanner documentation (nearby-selection-feature.md:633-663):

> The distance matrix is managed through OptaPlanner's supply/demand system:
> - Distance matrix only exists during phase execution
> - Memory automatically freed when phase ends
> - Multiple nearby selectors can share the same matrix (if compatible)
> - Lazy initialization: matrix built on first use

**Demand/Supply Equality**:
```java
// optaplanner/.../AbstractNearbyDistanceMatrixDemand.java:66-90
@Override
public final boolean equals(Object o) {
    // Two demands are equal if they have the same:
    // - meter (distance function)
    // - random (distribution)
    // - childSelector
    // - replayingSelector

    // If demands are equal, SupplyManager returns the SAME matrix instance
    // This saves memory and computation
}
```

**GreyCOS Approach**:
- Each selector creates its own distance matrix
- No sharing between selectors
- Matrix persists throughout solver lifecycle
- No integration with supply/demand infrastructure

**Impact**:
- **Higher memory usage**: Duplicate matrices for similar selectors
- **Slower initialization**: Each selector computes its own matrix
- **No phase-level lifecycle**: Matrix memory not freed between phases

**Severity**: ⚠️ **LOW-MEDIUM** - Performance and memory issue, not correctness

**Example**:
```java
// If solver config has:
<changeMoveSelector>
    <entitySelector id="e1"/>
    <valueSelector>
        <nearbySelection>
            <originEntitySelector mimicSelectorRef="e1"/>
            <nearbyDistanceMeterClass>CustomerDistanceMeter</nearbyDistanceMeterClass>
        </nearbySelection>
    </valueSelector>
</changeMoveSelector>

<swapMoveSelector>
    <entitySelector id="e2"/>
    <secondaryEntitySelector>
        <nearbySelection>
            <originEntitySelector mimicSelectorRef="e2"/>
            <nearbyDistanceMeterClass>CustomerDistanceMeter</nearbyDistanceMeterClass>
        </nearbySelection>
    </secondaryEntitySelector>
</swapMoveSelector>

// If both use same distance meter and entity type:
// OptaPlanner: 1 shared distance matrix (memory efficient)
// GreyCOS: 2 separate distance matrices (memory duplication)
```

---

## Root Cause Analysis

### Flow Comparison: Selecting 3 Nearby Values

**OptaPlanner (Correct Behavior)**:

```
Step 1: Move selector starts iteration
Step 2: Recording selector selects entity A
Step 3: Create nearby value iterator with replaying iterator
Step 4: Nearby iterator calls selectOrigin():
        - replayingIterator.next() → Entity A (origin selected)
        - Sets originSelected = true
        - Caches origin = Entity A
Step 5: First next() call:
        - selectOrigin() returns immediately (already selected)
        - Returns distanceMatrix.getDestination(A, 0) → Value X (nearest to A)
Step 6: Second next() call:
        - selectOrigin() returns immediately (still Entity A)
        - Returns distanceMatrix.getDestination(A, 1) → Value Y (2nd nearest to A)
Step 7: Third next() call:
        - selectOrigin() returns immediately (still Entity A)
        - Returns distanceMatrix.getDestination(A, 2) → Value Z (3rd nearest to A)

Result: All values (X, Y, Z) selected nearby to Entity A ✅
```

**GreyCOS (Broken Behavior)**:

```
Step 1: Move selector starts iteration
Step 2: Entity selector is configured (but not replaying)
Step 3: Create nearby value iterator with entity parameter
Step 4: First next() call:
        - Calls originEntitySelector.iterator().next()
        - New iterator created, returns first entity → Entity A
        - Returns distanceMatrix.getDestination(A, 0) → Value X (nearest to A)
        - Increments index to 1
Step 5: Second next() call:
        - Calls originEntitySelector.iterator().next() AGAIN
        - New iterator created, returns first entity → Could be Entity B (if selector advanced)
        - Returns distanceMatrix.getDestination(B, 1) → Value Y (2nd nearest to B, NOT A)
        - Increments index to 2
Step 6: Third next() call:
        - Calls originEntitySelector.iterator().next() AGAIN
        - New iterator created, returns first entity → Could be Entity C
        - Returns distanceMatrix.getDestination(C, 2) → Value Z (3rd nearest to C, NOT A or B)
        - Increments index to 3

Result: Values selected nearby to DIFFERENT origins (A, B, C) ❌
```

**Visual Representation**:

```
OptaPlanner:
Origin: A ──┬──> Value X (distance 2.5 from A)
            ├──> Value Y (distance 4.1 from A)
            └──> Value Z (distance 5.7 from A)

GreyCOS:
Origin: A ────> Value X (distance 2.5 from A)
Origin: B ────> Value Y (distance 4.1 from B)  ← Different origin!
Origin: C ────> Value Z (distance 5.7 from C)  ← Different origin!
```

### Why This Breaks Nearby Selection

From OptaPlanner documentation (nearby-selection-feature.md:46-71):

> Nearby selection works on a simple but powerful principle:
>
> **When selecting a candidate for a move, prefer candidates that are "nearby" to a previously selected origin, where "nearby" is defined by a custom distance function.**
>
> The nearby selector:
> 1. Calculates d(O, Di) for all destinations
> 2. Sorts destinations by distance: D1, D2, ..., Dn where d(O, D1) ≤ d(O, D2) ≤ ... ≤ d(O, Dn)
> 3. Selects Di with probability P(i)

**The critical assumption**: All destinations are sorted relative to **the same origin O**.

GreyCOS violates this by using different origins for each destination selection, making the distance-based ranking meaningless.

---

## Detailed Technical Comparison

### Architecture Comparison

```
OptaPlanner Architecture:
┌─────────────────────────────────────────────────────────────┐
│ Move Selector (e.g., ChangeMoveSelector)                    │
└───────┬─────────────────────────────────────────────────────┘
        │
        ├──> Recording Entity Selector (id="entity1")
        │    └──> Selects Entity A, advances on each move
        │
        └──> Nearby Value Selector
             ├──> Child Value Selector (source of candidates)
             │
             ├──> Replaying Entity Selector (mimicSelectorRef="entity1")
             │    └──> Replays Entity A until recording selector advances
             │
             ├──> Distance Matrix (via SupplyManager)
             │    └──> Pre-sorted: A→[V1, V2, V3, ...] (sorted by distance)
             │
             └──> Nearby Iterator
                  ├──> Caches origin = A (from replaying iterator)
                  └──> Returns V1, V2, V3... (all relative to A)

GreyCOS Architecture:
┌─────────────────────────────────────────────────────────────┐
│ Move Selector                                               │
└───────┬─────────────────────────────────────────────────────┘
        │
        └──> Nearby Value Selector
             ├──> Child Value Selector
             │
             ├──> Origin Entity Selector (NOT replaying)
             │    └──> Regular selector, creates new iterator each call
             │
             ├──> Distance Matrix (created in constructor)
             │
             └──> Nearby Iterator
                  ├──> NO origin caching
                  └──> Calls originEntitySelector.iterator().next() each time
                       └──> Returns different origin on each call
```

### Distance Matrix Population Comparison

**OptaPlanner**:
```java
// In ValueNearbyDistanceMatrixDemand.supplyNearbyDistanceMatrix():
NearbyDistanceMatrix<Origin_, Destination_> nearbyDistanceMatrix =
    new NearbyDistanceMatrix<>(meter, (int) originSize, destinationIteratorProvider, destinationSizeFunction);

// Pre-populate matrix for ALL origins
replayingSelector.endingIterator()
    .forEachRemaining(origin -> nearbyDistanceMatrix.addAllDestinations((Origin_) origin));

return nearbyDistanceMatrix;
```

**What this does**:
- Creates matrix for all possible origins upfront
- For each origin entity, computes and sorts all destination distances
- Matrix is fully populated when phase starts
- `getDestination(origin, index)` is then just an array lookup

**GreyCOS**:
```java
// In NearbyDistanceMatrix.getDestination():
public @NonNull Object getDestination(@NonNull Origin origin, int nearbyIndex) {
    Destination[] destinations = originToDestinationsMap.get(origin);
    if (destinations == null) {
        // Lazy: compute on-demand via computeIfAbsent
        destinations = originToDestinationsMap.computeIfAbsent(origin, this::computeDestinations);
    }
    return destinations[nearbyIndex];
}
```

**What this does**:
- Matrix is populated lazily (on first access)
- Uses `ConcurrentHashMap.computeIfAbsent` for thread safety
- First access triggers distance computation

**Comparison**:

| Aspect | OptaPlanner | GreyCOS |
|--------|-------------|---------|
| Population | Eager (all at phase start) | Lazy (on first access) |
| Thread safety | Not needed (populated upfront) | ConcurrentHashMap (thread-safe) |
| Memory | Fixed at phase start | Grows as origins accessed |
| First access latency | O(1) lookup | O(n log n) computation + O(1) lookup |
| Subsequent access | O(1) lookup | O(1) lookup |

**Note**: Both approaches work for the distance matrix itself. The lazy approach in greycos is actually reasonable for memory efficiency. The **real problem** is how the matrix is used in the iterators.

### Iterator State Machine Comparison

**OptaPlanner OriginalNearbyValueIterator State**:
```java
State Variables:
- nearbyDistanceMatrix: NearbyDistanceMatrix<Object, Object>
- replayingIterator: Iterator<Object>  // Replaying iterator
- childSize: long
- originSelected: boolean = false  // State flag
- origin: Object = null  // Cached origin
- nextNearbyIndex: int

State Transitions:
1. Construction: originSelected = false, nextNearbyIndex = 0 or 1
2. First hasNext()/next(): selectOrigin() → origin cached, originSelected = true
3. Subsequent calls: origin remains cached, nextNearbyIndex increments
4. Iterator exhausted: nextNearbyIndex >= childSize
```

**GreyCOS OriginalNearbyValueIterator State**:
```java
State Variables:
- entity: Object  // The entity parameter (not used correctly)
- nearbySize: int
- index: int = 0

State Transitions:
1. Construction: index = 0
2. Each next():
   - Calls originEntitySelector.iterator().next() → NEW ORIGIN each time
   - Returns distanceMatrix.getDestination(new_origin, index)
   - Increments index
3. Iterator exhausted: index >= nearbySize

Missing:
- No origin caching
- No origin selection flag
- No replaying iterator
```

---

## Recommended Fixes

### Priority 1: Fix Origin Caching (Critical)

**File**: `core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/value/nearby/NearEntityNearbyValueSelector.java`

**Required Changes**:

1. **Pass replaying iterator to iterator constructor**:
```java
// Current (WRONG):
@Override
public @NonNull Iterator<Object> iterator(@NonNull Object entity) {
    if (randomSelection) {
        return new RandomNearbyValueIterator(workingRandom, entity);
    } else {
        return new OriginalNearbyValueIterator(entity);
    }
}

// Fixed:
@Override
public @NonNull Iterator<Object> iterator(@NonNull Object entity) {
    // Get replaying iterator that will provide constant origin
    Iterator<Object> replayingOriginIterator = originEntitySelector.iterator();

    if (randomSelection) {
        return new RandomNearbyValueIterator(
            workingRandom,
            replayingOriginIterator,  // Pass iterator, not entity
            entity
        );
    } else {
        return new OriginalNearbyValueIterator(
            replayingOriginIterator,  // Pass iterator, not entity
            entity
        );
    }
}
```

2. **Update OriginalNearbyValueIterator to cache origin**:
```java
// Current (WRONG):
private class OriginalNearbyValueIterator implements Iterator<Object> {
    private final @Nullable Object entity;
    private final int nearbySize;
    private int index = 0;

    @Override
    public Object next() {
        Object origin = originEntitySelector.iterator().next();  // BUG: new origin each time
        Object result = distanceMatrix.getDestination(origin, index);
        index++;
        return result;
    }
}

// Fixed:
private class OriginalNearbyValueIterator implements Iterator<Object> {
    private final Iterator<Object> replayingOriginIterator;
    private final @Nullable Object entity;
    private final int nearbySize;
    private int index = 0;

    // Origin caching state
    private boolean originSelected = false;
    private Object origin = null;

    public OriginalNearbyValueIterator(
        Iterator<Object> replayingOriginIterator,
        @Nullable Object entity
    ) {
        this.replayingOriginIterator = replayingOriginIterator;
        this.entity = entity;
        this.nearbySize = (int) childValueSelector.getSize(entity);
    }

    private void selectOrigin() {
        if (originSelected) {
            return;  // Already selected, use cached origin
        }
        if (replayingOriginIterator.hasNext()) {
            origin = replayingOriginIterator.next();
        }
        originSelected = true;
    }

    @Override
    public boolean hasNext() {
        selectOrigin();
        return origin != null && index < nearbySize;
    }

    @Override
    public Object next() {
        selectOrigin();  // Use cached origin
        Object result = distanceMatrix.getDestination(origin, index);
        index++;
        return result;
    }
}
```

3. **Apply same fix to RandomNearbyValueIterator**:
```java
private class RandomNearbyValueIterator implements Iterator<Object> {
    private final java.util.Random workingRandom;
    private final Iterator<Object> replayingOriginIterator;  // Add this
    private final @Nullable Object entity;
    private final int nearbySize;
    private int count = 0;
    private Object origin = null;  // Cache origin

    public RandomNearbyValueIterator(
        java.util.Random workingRandom,
        Iterator<Object> replayingOriginIterator,  // Add parameter
        @Nullable Object entity
    ) {
        this.workingRandom = workingRandom;
        this.replayingOriginIterator = replayingOriginIterator;
        this.entity = entity;
        this.nearbySize = (int) childValueSelector.getSize(entity);
    }

    @Override
    public boolean hasNext() {
        return (origin != null || replayingOriginIterator.hasNext()) && count < nearbySize;
    }

    @Override
    public Object next() {
        if (nearbyRandom == null) {
            throw new IllegalStateException("nearbyRandom is null but randomSelection is true");
        }

        // Update origin if replaying iterator advanced
        if (replayingOriginIterator.hasNext()) {
            origin = replayingOriginIterator.next();
        }

        int nearbyIndex = nearbyRandom.nextInt(workingRandom, nearbySize);
        count++;
        return distanceMatrix.getDestination(origin, nearbyIndex);  // Use cached/updated origin
    }
}
```

**Impact**: This fixes the core issue - values will now be selected relative to the same origin.

---

### Priority 2: Implement Replaying Selector Pattern (Critical)

**Required Infrastructure**:

1. **Create MimicReplayingEntitySelector interface/class**:
   - Similar to OptaPlanner's implementation
   - Replays the same entity until the recording selector advances
   - See: `optaplanner/core/.../entity/mimic/MimicReplayingEntitySelector.java`

2. **Update AbstractNearbyValueSelector signature**:
```java
// Change from:
abstract class AbstractNearbyValueSelector<Solution_>
    extends AbstractDemandEnabledSelector<Solution_>
    implements IterableValueSelector<Solution_>

// To:
abstract class AbstractNearbyValueSelector<Solution_, ReplayingSelector_ extends EntitySelector<Solution_>>
    extends AbstractDemandEnabledSelector<Solution_>
    implements IterableValueSelector<Solution_>
```

3. **Add replaying selector validation**:
```java
protected abstract ReplayingSelector_ castReplayingSelector(Object uncastReplayingSelector);

// In NearEntityNearbyValueSelector:
@Override
protected EntitySelector<Solution_> castReplayingSelector(Object uncastReplayingSelector) {
    if (!(uncastReplayingSelector instanceof MimicReplayingEntitySelector)) {
        throw new IllegalStateException(
            "Nearby value selector (" + this +
            ") did not receive a replaying entity selector (" + uncastReplayingSelector + ")."
        );
    }
    return (EntitySelector<Solution_>) uncastReplayingSelector;
}
```

4. **Update selector factory**:
   - Ensure origin selectors are created as replaying selectors
   - Wire up recording/replaying relationship
   - See: `optaplanner/core/.../ValueSelectorFactory.java`

**Impact**: Ensures origin selector behaves correctly as a replaying selector.

---

### Priority 3: Add discardNearbyIndexZero Flag (Medium)

**File**: `NearEntityNearbyValueSelector.java`

**Changes**:

1. **Add field**:
```java
public final class NearEntityNearbyValueSelector<Solution_> extends AbstractNearbyValueSelector<Solution_> {
    private final @NonNull EntitySelector<Solution_> originEntitySelector;
    private final boolean discardNearbyIndexZero;  // Add this

    // ...
}
```

2. **Compute in constructor**:
```java
public NearEntityNearbyValueSelector(
    @NonNull IterableValueSelector<Solution_> childValueSelector,
    @NonNull EntitySelector<Solution_> originEntitySelector,
    // ...
) {
    super(childValueSelector, nearbyDistanceMeter, nearbyRandom, randomSelection, null);
    this.originEntitySelector = originEntitySelector;

    // Compute discardNearbyIndexZero
    this.discardNearbyIndexZero = childValueSelector.getVariableDescriptor()
        .getVariablePropertyType()
        .isAssignableFrom(originEntitySelector.getEntityDescriptor().getEntityClass());

    phaseLifecycleSupport.addEventListener(originEntitySelector);
}
```

3. **Use in iterators**:
```java
// In OriginalNearbyValueIterator constructor:
this.index = discardNearbyIndexZero ? 1 : 0;  // Start at 1 if discarding

// In getSize():
@Override
public long getSize(Object entity) {
    return childValueSelector.getSize(entity) - (discardNearbyIndexZero ? 1 : 0);
}

// In RandomNearbyValueIterator:
@Override
public Object next() {
    // ...
    int nearbyIndex = nearbyRandom.nextInt(workingRandom, nearbySize);
    if (discardNearbyIndexZero) {
        nearbyIndex++;  // Skip index 0
    }
    return distanceMatrix.getDestination(origin, nearbyIndex);
}
```

**Impact**: Prevents invalid self-swap moves, improves move quality.

---

### Priority 4: SupplyManager Integration (Optional)

**Benefit**: Memory efficiency, matrix sharing between selectors.

**Complexity**: Requires implementing full demand/supply infrastructure.

**Recommendation**: Address after Priorities 1-3 are fixed. Current approach with lazy initialization is acceptable for now.

**If implementing**:
1. Create `AbstractNearbyDistanceMatrixDemand` base class
2. Create specific demand classes (e.g., `ValueNearbyDistanceMatrixDemand`)
3. Integrate with SupplyManager in phase lifecycle
4. See: `optaplanner/core/.../nearby/AbstractNearbyDistanceMatrixDemand.java`

---

## Testing Strategy

### Unit Tests

**Test 1: Origin Caching**
```java
@Test
void testOriginIsCachedDuringIteration() {
    // Setup: Create nearby selector with mock replaying iterator
    MimicReplayingEntitySelector<TestSolution> replayingSelector = mock(MimicReplayingEntitySelector.class);
    Iterator<Object> replayingIterator = mock(Iterator.class);
    when(replayingSelector.iterator()).thenReturn(replayingIterator);
    when(replayingIterator.hasNext()).thenReturn(true);
    when(replayingIterator.next()).thenReturn(entityA);  // Always return entityA

    NearEntityNearbyValueSelector<TestSolution> selector = new NearEntityNearbyValueSelector<>(
        childValueSelector, replayingSelector, distanceMeter, nearbyRandom, false
    );

    // Execute: Iterate through 5 values
    Iterator<Object> iterator = selector.iterator(entityA);
    List<Object> values = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
        values.add(iterator.next());
    }

    // Verify: replayingIterator.next() called only ONCE
    verify(replayingIterator, times(1)).next();

    // Verify: All values are sorted by distance from entityA
    for (int i = 0; i < values.size() - 1; i++) {
        double dist1 = distanceMeter.getNearbyDistance(entityA, values.get(i));
        double dist2 = distanceMeter.getNearbyDistance(entityA, values.get(i + 1));
        assertTrue(dist1 <= dist2, "Values should be sorted by distance from origin");
    }
}
```

**Test 2: Verify Values Are Nearby to Same Origin**
```java
@Test
void testAllValuesNearbyToSameOrigin() {
    // Setup with real distance meter
    CustomerNearbyDistanceMeter distanceMeter = new CustomerNearbyDistanceMeter();

    Customer origin = new Customer(0, 0);  // At (0, 0)
    List<Customer> destinations = Arrays.asList(
        new Customer(1, 1),   // Distance ~1.4
        new Customer(10, 10), // Distance ~14.1
        new Customer(2, 2),   // Distance ~2.8
        new Customer(5, 5)    // Distance ~7.1
    );

    // Execute
    Iterator<Object> iterator = selector.iterator(origin);
    List<Customer> selectedValues = new ArrayList<>();
    while (iterator.hasNext()) {
        selectedValues.add((Customer) iterator.next());
    }

    // Verify: Values are sorted by distance from origin
    assertEquals(4, selectedValues.size());
    assertEquals(1.4, distanceMeter.getNearbyDistance(origin, selectedValues.get(0)), 0.1);
    assertEquals(2.8, distanceMeter.getNearbyDistance(origin, selectedValues.get(1)), 0.1);
    assertEquals(7.1, distanceMeter.getNearbyDistance(origin, selectedValues.get(2)), 0.1);
    assertEquals(14.1, distanceMeter.getNearbyDistance(origin, selectedValues.get(3)), 0.1);
}
```

**Test 3: discardNearbyIndexZero**
```java
@Test
void testDiscardNearbyIndexZeroWhenEntityIsValue() {
    // Setup: Entity type is assignable from value type
    // (e.g., swapping entities)

    Entity entityA = new Entity("A");
    Entity entityB = new Entity("B");
    Entity entityC = new Entity("C");

    // Execute
    Iterator<Object> iterator = selector.iterator(entityA);
    List<Entity> selectedValues = new ArrayList<>();
    while (iterator.hasNext()) {
        selectedValues.add((Entity) iterator.next());
    }

    // Verify: entityA (origin) is NOT in the selected values
    assertFalse(selectedValues.contains(entityA), "Origin should be discarded");

    // Verify: First value is the nearest OTHER entity
    assertEquals(entityB, selectedValues.get(0));  // Assuming B is nearest to A
}
```

### Integration Tests

**Test 4: VRP Nearby Selection**
```java
@Test
void testVehicleRoutingNearbySelection() {
    // Load VRP problem
    VehicleRoutingSolution problem = loadTestProblem();

    // Configure solver with nearby selection
    SolverFactory<VehicleRoutingSolution> solverFactory = SolverFactory.create(
        new SolverConfig()
            .withSolutionClass(VehicleRoutingSolution.class)
            .withEntityClasses(Customer.class)
            .withPhases(new LocalSearchPhaseConfig()
                .withMoveSelectorConfig(new ChangeMoveSelectorConfig()
                    .withValueSelectorConfig(new ValueSelectorConfig()
                        .withNearbySelectionConfig(new NearbySelectionConfig()
                            .withNearbyDistanceMeterClass(CustomerNearbyDistanceMeter.class)
                            .withNearbySelectionDistributionType(PARABOLIC_DISTRIBUTION)
                        )
                    )
                )
            )
    );

    // Solve
    Solver<VehicleRoutingSolution> solver = solverFactory.buildSolver();
    VehicleRoutingSolution solution = solver.solve(problem);

    // Verify: Solution is valid and score improved
    assertTrue(solution.getScore().isFeasible());
    assertTrue(solution.getScore().compareTo(problem.getScore()) > 0);
}
```

### Benchmarking

**Compare greycos vs OptaPlanner on same VRP dataset**:
```java
@Test
void benchmarkNearbySelectionAgainstOptaPlanner() {
    // Run both solvers on identical problem
    VehicleRoutingSolution problem = loadBenchmarkProblem();

    // GreyCOS solver
    long greycosStart = System.currentTimeMillis();
    VehicleRoutingSolution greycosSolution = greycosSolver.solve(problem.clone());
    long greycosTime = System.currentTimeMillis() - greycosStart;

    // OptaPlanner solver
    long optaStart = System.currentTimeMillis();
    VehicleRoutingSolution optaSolution = optaPlannerSolver.solve(problem.clone());
    long optaTime = System.currentTimeMillis() - optaStart;

    // Compare results
    System.out.println("GreyCOS: " + greycosSolution.getScore() + " in " + greycosTime + "ms");
    System.out.println("OptaPlanner: " + optaSolution.getScore() + " in " + optaTime + "ms");

    // Scores should be comparable (within 10%)
    double scoreDiff = Math.abs(
        greycosSolution.getScore().toDouble() - optaSolution.getScore().toDouble()
    ) / optaSolution.getScore().toDouble();

    assertTrue(scoreDiff < 0.10, "Scores should be within 10%: " + scoreDiff);
}
```

---

## References

### Documentation
- `docs/feature_plans/nearby/nearby-selection-feature.md` - Complete OptaPlanner nearby selection guide

### OptaPlanner Reference Implementation
- `optaplanner/core/.../nearby/AbstractNearbySelector.java` - Base selector with SupplyManager integration
- `optaplanner/core/.../value/nearby/NearEntityNearbyValueSelector.java` - Entity-origin value selector
- `optaplanner/core/.../value/nearby/OriginalNearbyValueIterator.java` - Iterator with origin caching
- `optaplanner/core/.../common/nearby/RandomNearbyIterator.java` - Random nearby iterator
- `optaplanner/core/.../common/nearby/NearbyDistanceMatrix.java` - Distance matrix implementation
- `optaplanner/core/.../entity/mimic/MimicReplayingEntitySelector.java` - Replaying selector pattern

### GreyCOS Current Implementation
- `core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/value/nearby/AbstractNearbyValueSelector.java`
- `core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/value/nearby/NearEntityNearbyValueSelector.java`
- `core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/value/nearby/NearValueNearbyValueSelector.java`
- `core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/common/nearby/NearbyDistanceMatrix.java`

---

## Conclusion

The greycos nearby selection implementation has **critical architectural differences** from OptaPlanner that prevent it from working correctly:

1. **Origin is not cached** - retrieved fresh on every `next()` call
2. **No replaying selector pattern** - cannot maintain constant origin
3. **Iterator construction mismatch** - doesn't receive replaying iterator

**Bottom line**: Values are selected "nearby" to different origins instead of the same origin, completely breaking the nearby selection algorithm.

**Priority fixes** (in order):
1. ✅ Implement origin caching in iterators (CRITICAL)
2. ✅ Pass replaying iterator to iterator constructors (CRITICAL)
3. ✅ Implement replaying selector pattern (CRITICAL)
4. ⚙️ Add `discardNearbyIndexZero` flag (MEDIUM)
5. 💡 Consider SupplyManager integration (OPTIONAL)

Once these fixes are implemented, greycos nearby selection should produce results comparable to OptaPlanner.

---

**Document Version**: 1.0
**Last Updated**: 2026-01-08
**Author**: Analysis of greycos-solver implementation vs OptaPlanner reference
