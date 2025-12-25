# Nearby Selection Feature: Greycos vs Tech Spec Differences

## Executive Summary

The nearby selection feature in greycos differs significantly from the technical specification in several key areas:

1. **Enterprise Feature**: Nearby selection is gated as an enterprise feature in greycos
2. **Incomplete Implementation**: Core functionality is not fully implemented in the community edition
3. **Architecture**: Uses enterprise service pattern instead of direct integration
4. **Enhanced Thread Safety**: Greycos adds thread safety improvements
5. **Additional Origin Types**: Support for sub-list selectors not in spec

---

## Detailed Differences

### 1. Enterprise Feature Gating

**Tech Spec**: Nearby selection is a standard feature available in the core implementation.

**Greycos Implementation**: Nearby selection is marked as an **enterprise feature** that requires a commercial license.

```java
// In DefaultGreycosSolverEnterpriseService.java (lines 252-290)
@Override
public <Solution_> ValueSelector<Solution_> applyNearbySelection(
    ValueSelectorConfig valueSelectorConfig,
    HeuristicConfigPolicy<Solution_> configPolicy,
    EntityDescriptor<Solution_> entityDescriptor,
    SelectionCacheType minimumCacheType,
    SelectionOrder resolvedSelectionOrder,
    ValueSelector<Solution_> valueSelector) {
    throw new UnsupportedOperationException("Nearby selection is an enterprise feature.");
}
```

**Impact**: Users without an enterprise license cannot use nearby selection, even though the core classes exist.

---

### 2. Architecture: Enterprise Service Pattern

**Tech Spec**: Direct integration in [`ValueSelectorFactory.buildValueSelector()`](text_utils/features/nearby/Nearby-Selection-Implementation-Guide.md:896-963):

```java
// Tech spec shows direct implementation
if (config.getNearbySelectionConfig() != null) {
    valueSelector = applyNearbySelection(configPolicy, entityDescriptor, minimumCacheType,
            resolvedSelectionOrder, valueSelector);
}
```

**Greycos Implementation**: Delegates to enterprise service via [`GreycosSolverEnterpriseService`](core/src/main/java/ai/greycos/solver/core/enterprise/GreycosSolverEnterpriseService.java:186-192):

```java
// In ValueSelectorFactory.java (lines 690-705)
private ValueSelector<Solution_> applyNearbySelection(
    HeuristicConfigPolicy<Solution_> configPolicy,
    EntityDescriptor<Solution_> entityDescriptor,
    SelectionCacheType minimumCacheType,
    SelectionOrder resolvedSelectionOrder,
    ValueSelector<Solution_> valueSelector) {
    return GreycosSolverEnterpriseService.loadOrFail(
            GreycosSolverEnterpriseService.Feature.NEARBY_SELECTION)
        .applyNearbySelection(
            config,
            configPolicy,
            entityDescriptor,
            minimumCacheType,
            resolvedSelectionOrder,
            valueSelector);
}
```

**Impact**: Adds complexity and requires enterprise license for functionality.

---

### 3. NearbyDistanceMatrix: Thread Safety Enhancements

**Tech Spec**: Uses `HashMap` (line 796 in spec):

```java
// Tech spec (line 796)
private final Map<Origin, Destination[]> originToDestinationsMap;
```

**Greycos Implementation**: Uses `ConcurrentHashMap` for thread-safe lazy initialization (lines 57-58, 115):

```java
// In NearbyDistanceMatrix.java (lines 57-58)
this.originToDestinationsMap =
    new ConcurrentHashMap<>(originSize, 0.75f, Runtime.getRuntime().availableProcessors());

// Thread-safe lazy initialization (line 115)
destinations = originToDestinationsMap.computeIfAbsent(origin, this::computeDestinations);
```

**Impact**: Greycos implementation is more robust for multithreaded solving environments.

---

### 4. NearbyDistanceMeter: Enhanced Documentation

**Tech Spec**: Basic functional interface with minimal documentation (lines 268-296):

```java
@FunctionalInterface
public interface NearbyDistanceMeter<O, D> {
    double getNearbyDistance(O origin, D destination);
}
```

**Greycos Implementation**: Comprehensive thread safety documentation (lines 1-49):

```java
/**
 * Functional interface for calculating distance between an origin and a destination.
 *
 * <p><b>Thread Safety:</b> Implementations MUST be thread-safe and stateless.
 * The solver may reuse a single instance across multiple threads in multithreaded
 * solving mode. Implementations should not maintain any mutable state between calls
 * to {@link #getNearbyDistance(Object, Object)}.
 *
 * <p><b>Examples of thread-safe implementations:</b>
 * <ul>
 *   <li>Pure mathematical calculations (e.g., Euclidean distance)
 *   <li>Read-only lookups in immutable data structures
 *   <li>Thread-safe cache with concurrent access
 * </ul>
 *
 * <p><b>Examples of thread-unsafe implementations:</b>
 * <ul>
 *   <li>Mutable instance fields modified during distance calculation
 *   <li>Non-thread-safe caches (e.g., HashMap) without synchronization
 *   <li>External API calls with shared mutable state
 * </ul>
 */
@FunctionalInterface
public interface NearbyDistanceMeter<O, D> {
    /**
     * Measures distance from origin to destination.
     *
     * <p><b>Thread Safety:</b> This method may be called concurrently from multiple threads.
     * Implementations must ensure thread-safe behavior without external synchronization.
     */
    double getNearbyDistance(O origin, D destination);
}
```

**Impact**: Better guidance for implementers on thread safety requirements.

---

### 5. NearbySelectionConfig: Additional Origin Type

**Tech Spec**: Only supports entity and value origin selectors (lines 77-79):

```java
@XmlElement(name = "originEntitySelector")
protected EntitySelectorConfig originEntitySelectorConfig = null;

@XmlElement(name = "originValueSelector")
protected ValueSelectorConfig originValueSelectorConfig = null;
```

**Greycos Implementation**: Adds support for sub-list selectors (lines 45-46, 76-83):

```java
@XmlElement(name = "originSubListSelector")
protected SubListSelectorConfig originSubListSelectorConfig = null;

public @Nullable SubListSelectorConfig getOriginSubListSelectorConfig() {
    return originSubListSelectorConfig;
}

public void setOriginSubListSelectorConfig(
    @Nullable SubListSelectorConfig originSubListSelectorConfig) {
    this.originSubListSelectorConfig = originSubListSelectorConfig;
}
```

**Impact**: Greycos supports list variable scenarios not covered in the spec.

---

### 6. Validation: Enhanced Error Messages

**Tech Spec**: Basic validation with simple error messages (lines 201-227):

```java
if (originSelectorCount == 0) {
    throw new IllegalArgumentException("The nearbySelectorConfig (" + this
            + ") is nearby selection but lacks an origin selector config."
            + " Set one of originEntitySelectorConfig or originValueSelectorConfig.");
}
```

**Greycos Implementation**: Uses modern Java text blocks with detailed messages (lines 274-286):

```java
if (originSelectorCount == 0) {
    throw new IllegalArgumentException(
        """
                The nearbySelectorConfig (%s) is nearby selection but lacks an origin selector config.
                Set one of originEntitySelectorConfig, originSubListSelectorConfig or originValueSelectorConfig."""
            .formatted(this));
} else if (originSelectorCount > 1) {
    throw new IllegalArgumentException(
        """
                The nearbySelectorConfig (%s) has multiple origin selector configs but exactly one is expected.
                Set one of originEntitySelectorConfig, originSubListSelectorConfig or originValueSelectorConfig."""
            .formatted(this));
}
```

**Impact**: Better user experience with clearer error messages.

---

### 7. Mimic Selector Validation

**Tech Spec**: Does not explicitly validate mimic selector references.

**Greycos Implementation**: Validates that origin selectors have mimic selector refs (lines 287-319):

```java
if (originEntitySelectorConfig != null
    && originEntitySelectorConfig.getMimicSelectorRef() == null) {
    throw new IllegalArgumentException(
        """
                The nearbySelectorConfig (%s) has an originEntitySelectorConfig (%s) which has no mimicSelectorRef (%s).
                Nearby selection's original entity should always be the same as an entity selected earlier in the move."""
            .formatted(
                this,
                originEntitySelectorConfig,
                originEntitySelectorConfig.getMimicSelectorRef()));
}
```

**Impact**: Catches configuration errors earlier with more helpful messages.

---

### 8. Nearby Selector Implementation: Incomplete

**Tech Spec**: Shows full implementation with distance matrix integration (lines 975-1056):

```java
private class RandomNearbyValueIterator extends AbstractRandomIterator<Object> {
    private final Random workingRandom;
    private Object origin;
    private int nearbySize;

    @Override
    public Object next() {
        origin = originEntitySelector.next();
        nearbySize = childValueSelector.getSize();
        int nearbyIndex = nearbyRandom.nextInt(workingRandom, nearbySize);
        // Use distance matrix to get nearby destination
        return childValueSelector.iterator().next();
    }
}
```

**Greycos Implementation**: Basic iterator without distance matrix integration (lines 82-116):

```java
private class RandomNearbyValueIterator implements Iterator<Object> {
    private final java.util.Random workingRandom;
    private final @Nullable Object entity;
    private final int nearbySize;
    private int count = 0;

    @Override
    public Object next() {
        if (nearbyRandom == null) {
            throw new IllegalStateException("nearbyRandom is null but randomSelection is true");
        }
        int nearbyIndex = nearbyRandom.nextInt(workingRandom, nearbySize);
        count++;
        // Get nearbyIndex-th value from child selector
        // For now, we iterate through child selector to get value at index
        Iterator<Object> childIterator = childValueSelector.iterator(entity);
        Object result = null;
        for (int i = 0; i <= nearbyIndex && childIterator.hasNext(); i++) {
            result = childIterator.next();
        }
        return result;
    }
}
```

**Critical Difference**: Greycos implementation does NOT:
- Use `NearbyDistanceMatrix` to sort destinations by distance
- Actually select based on proximity to origin
- Implement the core nearby selection logic

**Impact**: The feature is non-functional even in enterprise edition without proper implementation.

---

### 9. JSpecify Annotations

**Tech Spec**: Uses traditional nullability annotations (none shown).

**Greycos Implementation**: Uses JSpecify annotations throughout (`@NonNull`, `@Nullable`):

```java
public @Nullable EntitySelectorConfig getOriginEntitySelectorConfig() {
    return originEntitySelectorConfig;
}

public void setOriginEntitySelectorConfig(
    @Nullable EntitySelectorConfig originEntitySelectorConfig) {
    this.originEntitySelectorConfig = originEntitySelectorConfig;
}
```

**Impact**: Better null safety with modern Java tooling support.

---

### 10. NearbyRandomFactory: Improved Validation

**Tech Spec**: Basic validation (lines 739-764):

```java
if (blockDistributionEnabled && linearDistributionEnabled) {
    throw new IllegalArgumentException("The nearbySelectorConfig (" + nearbySelectionConfig
            + ") has both blockDistribution and linearDistribution parameters.");
}
```

**Greycos Implementation**: More sophisticated validation with counter (lines 62-81):

```java
// Validate only one distribution is enabled
int enabledCount = 0;
if (blockDistributionEnabled) {
    enabledCount++;
}
if (linearDistributionEnabled) {
    enabledCount++;
}
if (parabolicDistributionEnabled) {
    enabledCount++;
}
if (betaDistributionEnabled) {
    enabledCount++;
}
if (enabledCount > 1) {
    throw new IllegalArgumentException(
        "The nearbySelectorConfig ("
                + nearbySelectionConfig
                + ") has multiple distribution types enabled. Only one is allowed.");
}
```

**Impact**: More comprehensive validation catches more configuration errors.

---

## Summary Table

| Aspect | Tech Spec | Greycos | Status |
|---------|-------------|-----------|--------|
| **Availability** | Standard feature | Enterprise only | ⚠️ Restricted |
| **Architecture** | Direct integration | Enterprise service pattern | 🔀 Different |
| **Thread Safety** | HashMap | ConcurrentHashMap | ✅ Improved |
| **Documentation** | Basic | Comprehensive thread safety docs | ✅ Enhanced |
| **Origin Types** | Entity, Value | Entity, Value, SubList | ✅ Extended |
| **Validation** | Basic | Enhanced with mimic refs | ✅ Improved |
| **Error Messages** | String concatenation | Text blocks | ✅ Modern |
| **Selector Logic** | Full distance matrix | Basic iteration | ❌ Incomplete |
| **Null Safety** | None | JSpecify annotations | ✅ Modern |
| **Validation Logic** | Simple if statements | Counter-based | ✅ Robust |

---

## Critical Issues

### 1. Non-Functional Implementation
The nearby selector implementations (`NearEntityNearbyValueSelector`, `NearValueNearbyValueSelector`) do not actually implement the nearby selection logic:
- No use of `NearbyDistanceMatrix`
- No sorting by distance
- No proximity-based selection
- Just iterates through child selector with index offset

### 2. Enterprise Gating
Core functionality exists but is locked behind enterprise license, making it unavailable to community users.

### 3. Missing Integration
Even if enterprise license is available, the actual nearby selection logic is not implemented in the selectors.

---

## Recommendations

### For Greycos Team

1. **Complete the implementation**: Integrate `NearbyDistanceMatrix` into the selector iterators
2. **Make it a community feature**: Nearby selection is fundamental to solving spatial problems
3. **Add comprehensive tests**: Test distance matrix integration and proximity selection
4. **Document the feature**: Explain when and how to use nearby selection effectively

### For Users

1. **Use enterprise edition**: Required for nearby selection functionality
2. **Wait for implementation**: The feature is not yet functional even with enterprise license
3. **Consider alternatives**: Implement custom move selectors with proximity logic if needed

---

## Conclusion

The greycos implementation of nearby selection is architecturally different from the tech spec and currently incomplete. While it includes several improvements (thread safety, documentation, extended origin types), the core functionality is gated as an enterprise feature and the actual proximity selection logic is not implemented. The feature requires significant development work to match the specification's functionality.
