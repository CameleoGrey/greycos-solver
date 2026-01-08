# Custom Forager Implementation Plan

## Executive Summary

This document provides a comprehensive plan for implementing **Custom Forager** functionality in GreyCOS Solver. Custom Forager enables users to provide custom implementations of `ConstructionHeuristicForager` or `LocalSearchForager` for advanced move harvesting strategies during solving phases.

---

## 1. Architecture Overview

### 1.1 Current Forager Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Phase Configuration                          │
│  (ConstructionHeuristicPhaseConfig / LocalSearchPhaseConfig)   │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│              Forager Configuration                             │
│  (ConstructionHeuristicForagerConfig / LocalSearchForagerConfig)│
│  - pickEarlyType                                              │
│  - acceptedCountLimit                                         │
│  - finalistPodiumType                                         │
│  - breakTieRandomly                                          │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│              Forager Factory                                   │
│  (ConstructionHeuristicForagerFactory /                       │
│   LocalSearchForagerFactory)                                  │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│              Forager Implementation                            │
│  (DefaultConstructionHeuristicForager /                       │
│   AcceptedLocalSearchForager)                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 Target Custom Forager Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Phase Configuration                          │
│  (ConstructionHeuristicPhaseConfig / LocalSearchPhaseConfig)   │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│              Forager Configuration                             │
│  (ConstructionHeuristicForagerConfig / LocalSearchForagerConfig)│
│  - pickEarlyType                                              │
│  - acceptedCountLimit                                         │
│  - finalistPodiumType                                         │
│  - breakTieRandomly                                          │
│  - foragerClass (NEW) ← Custom forager class reference         │
│  - customProperties (NEW) ← Custom configuration properties     │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│              Forager Factory                                   │
│  (ConstructionHeuristicForagerFactory /                       │
│   LocalSearchForagerFactory)                                  │
│  - buildForager() enhanced to support custom classes         │
│  - instantiateCustomForager() (NEW)                          │
│  - validateCustomForager() (NEW)                              │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│              Forager Implementation                            │
│  - DefaultConstructionHeuristicForager (existing)             │
│  - AcceptedLocalSearchForager (existing)                      │
│  - CustomForager (user-provided) ← NEW                       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Design Decisions

### 2.1 Configuration Strategy

**Decision**: Add `foragerClass` and `customProperties` to existing forager config classes

**Rationale**:
- Maintains backward compatibility with existing configurations
- Allows mixing built-in options with custom implementations
- Follows existing patterns (e.g., `moveIteratorFactoryClass`, `constraintProviderClass`)

**Trade-offs**:
- ✅ Minimal changes to existing code
- ✅ Clear separation of built-in vs custom
- ✅ Easy to understand and use
- ⚠️ Requires validation to ensure foragerClass is used correctly

### 2.2 Instantiation Strategy

**Decision**: Use reflection-based instantiation with ConfigUtils pattern

**Rationale**:
- Consistent with existing custom class instantiation (e.g., NeighborhoodProvider)
- Allows constructor injection of configuration
- Supports enterprise service pattern for license checking

**Trade-offs**:
- ✅ Proven pattern in codebase
- ✅ Flexible for various constructor signatures
- ⚠️ Runtime errors if class not found or invalid

### 2.3 Validation Strategy

**Decision**: Validate custom forager at build time, not at runtime

**Rationale**:
- Fail fast with clear error messages
- Prevents configuration errors from propagating to solving
- Consistent with existing validation patterns

**Trade-offs**:
- ✅ Early error detection
- ✅ Better user experience
- ⚠️ Slightly longer build time (negligible)

### 2.4 Enterprise Integration

**Decision**: Keep custom forager as enterprise-only feature

**Rationale**:
- Consistent with current implementation (throws UnsupportedOperationException)
- Maintains enterprise value proposition
- Allows for premium support and features

**Trade-offs**:
- ✅ Business value for enterprise customers
- ✅ Clear feature differentiation
- ⚠️ Limits community edition capabilities

---

## 3. Implementation Plan

### 3.1 Phase 1: Configuration Extensions

#### 3.1.1 Update ConstructionHeuristicForagerConfig

**File**: `core/src/main/java/ai/greycos/solver/core/config/constructionheuristic/decider/forager/ConstructionHeuristicForagerConfig.java`

**Changes**:
```java
@XmlType(propOrder = {"pickEarlyType", "foragerClass", "customProperties"})
public class ConstructionHeuristicForagerConfig
    extends AbstractConfig<ConstructionHeuristicForagerConfig> {

  private ConstructionHeuristicPickEarlyType pickEarlyType = null;
  private Class<? extends ConstructionHeuristicForager> foragerClass = null;
  private Map<String, String> customProperties = null;

  // Existing pickEarlyType getters/setters...

  public Class<? extends ConstructionHeuristicForager> getForagerClass() {
    return foragerClass;
  }

  public void setForagerClass(Class<? extends ConstructionHeuristicForager> foragerClass) {
    this.foragerClass = foragerClass;
  }

  public Map<String, String> getCustomProperties() {
    return customProperties;
  }

  public void setCustomProperties(Map<String, String> customProperties) {
    this.customProperties = customProperties;
  }

  // With methods...
  // inherit() method updated to include new fields...
  // copyConfig() method updated...
  // visitReferencedClasses() updated to include foragerClass...
}
```

#### 3.1.2 Update LocalSearchForagerConfig

**File**: `core/src/main/java/ai/greycos/solver/core/config/localsearch/decider/forager/LocalSearchForagerConfig.java`

**Changes**:
```java
@XmlType(propOrder = {"pickEarlyType", "acceptedCountLimit", "finalistPodiumType", 
                      "breakTieRandomly", "foragerClass", "customProperties"})
public class LocalSearchForagerConfig extends AbstractConfig<LocalSearchForagerConfig> {

  protected LocalSearchPickEarlyType pickEarlyType = null;
  protected Integer acceptedCountLimit = null;
  protected FinalistPodiumType finalistPodiumType = null;
  protected Boolean breakTieRandomly = null;
  protected Class<? extends LocalSearchForager> foragerClass = null;
  protected Map<String, String> customProperties = null;

  // Existing getters/setters...

  public Class<? extends LocalSearchForager> getForagerClass() {
    return foragerClass;
  }

  public void setForagerClass(Class<? extends LocalSearchForager> foragerClass) {
    this.foragerClass = foragerClass;
  }

  public Map<String, String> getCustomProperties() {
    return customProperties;
  }

  public void setCustomProperties(Map<String, String> customProperties) {
    this.customProperties = customProperties;
  }

  // With methods...
  // inherit() method updated to include new fields...
  // copyConfig() method updated...
  // visitReferencedClasses() updated to include foragerClass...
}
```

#### 3.1.3 Update XSD Schema

**File**: `core/src/main/resources/solver.xsd`

**Changes**:
```xml
<!-- constructionHeuristicForagerConfig (around line 1322) -->
<xs:complexType name="constructionHeuristicForagerConfig">
  <xs:complexContent>
    <xs:extension base="tns:abstractConfig">
      <xs:sequence>
        <xs:element minOccurs="0" name="pickEarlyType" type="tns:constructionHeuristicPickEarlyType"/>
        <xs:element minOccurs="0" name="foragerClass" type="xs:string"/>
        <xs:element minOccurs="0" name="customProperties" type="tns:jaxbAdaptedMap"/>
      </xs:sequence>
    </xs:extension>
  </xs:complexContent>
</xs:complexType>

<!-- localSearchForagerConfig (around line 1532) -->
<xs:complexType name="localSearchForagerConfig">
  <xs:complexContent>
    <xs:extension base="tns:abstractConfig">
      <xs:sequence>
        <xs:element minOccurs="0" name="pickEarlyType" type="tns:localSearchPickEarlyType"/>
        <xs:element minOccurs="0" name="acceptedCountLimit" type="xs:int"/>
        <xs:element minOccurs="0" name="finalistPodiumType" type="tns:finalistPodiumType"/>
        <xs:element minOccurs="0" name="breakTieRandomly" type="xs:boolean"/>
        <xs:element minOccurs="0" name="foragerClass" type="xs:string"/>
        <xs:element minOccurs="0" name="customProperties" type="tns:jaxbAdaptedMap"/>
      </xs:sequence>
    </xs:extension>
  </xs:complexContent>
</xs:complexType>
```

### 3.2 Phase 2: Factory Enhancements

#### 3.2.1 Update ConstructionHeuristicForagerFactory

**File**: `core/src/main/java/ai/greycos/solver/core/impl/constructionheuristic/decider/forager/ConstructionHeuristicForagerFactory.java`

**Changes**:
```java
public class ConstructionHeuristicForagerFactory<Solution_> {

  public static <Solution_> ConstructionHeuristicForagerFactory<Solution_> create(
      ConstructionHeuristicForagerConfig foragerConfig) {
    return new ConstructionHeuristicForagerFactory<>(foragerConfig);
  }

  private final ConstructionHeuristicForagerConfig foragerConfig;

  public ConstructionHeuristicForagerFactory(ConstructionHeuristicForagerConfig foragerConfig) {
    this.foragerConfig = foragerConfig;
  }

  public ConstructionHeuristicForager<Solution_> buildForager(
      HeuristicConfigPolicy<Solution_> configPolicy) {
    // Check if custom forager is configured
    if (foragerConfig.getForagerClass() != null) {
      return buildCustomForager(configPolicy);
    }

    // Default behavior for built-in foragers
    ConstructionHeuristicPickEarlyType pickEarlyType_;
    if (foragerConfig.getPickEarlyType() == null) {
      pickEarlyType_ =
          configPolicy.getInitializingScoreTrend().isOnlyDown()
              ? ConstructionHeuristicPickEarlyType.FIRST_NON_DETERIORATING_SCORE
              : ConstructionHeuristicPickEarlyType.NEVER;
    } else {
      pickEarlyType_ = foragerConfig.getPickEarlyType();
    }
    return new DefaultConstructionHeuristicForager<>(pickEarlyType_);
  }

  @SuppressWarnings("unchecked")
  private ConstructionHeuristicForager<Solution_> buildCustomForager(
      HeuristicConfigPolicy<Solution_> configPolicy) {
    var foragerClass = foragerConfig.getForagerClass();
    
    // Validate custom forager class
    validateCustomForagerClass(foragerClass);
    
    // Instantiate custom forager
    var customProperties = foragerConfig.getCustomProperties();
    try {
      // Try constructor with HeuristicConfigPolicy
      try {
        var constructor = foragerClass.getConstructor(HeuristicConfigPolicy.class);
        return (ConstructionHeuristicForager<Solution_>) constructor.newInstance(configPolicy);
      } catch (NoSuchMethodException e) {
        // Try no-arg constructor
        var constructor = foragerClass.getConstructor();
        var forager = (ConstructionHeuristicForager<Solution_>) constructor.newInstance();
        
        // Inject custom properties if available
        if (customProperties != null && !customProperties.isEmpty()) {
          injectCustomProperties(forager, customProperties);
        }
        
        return forager;
      }
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to instantiate custom forager (" + foragerClass.getName() + ").", e);
    }
  }

  private void validateCustomForagerClass(
      Class<? extends ConstructionHeuristicForager> foragerClass) {
    if (foragerClass == DefaultConstructionHeuristicForager.class) {
      throw new IllegalArgumentException(
          "The foragerClass (" + foragerClass.getName() + 
          ") is a built-in forager. Use pickEarlyType configuration instead.");
    }
    
    // Check for required constructor
    boolean hasConfigPolicyConstructor = false;
    boolean hasNoArgConstructor = false;
    for (var constructor : foragerClass.getConstructors()) {
      var paramTypes = constructor.getParameterTypes();
      if (paramTypes.length == 1 && paramTypes[0] == HeuristicConfigPolicy.class) {
        hasConfigPolicyConstructor = true;
      } else if (paramTypes.length == 0) {
        hasNoArgConstructor = true;
      }
    }
    
    if (!hasConfigPolicyConstructor && !hasNoArgConstructor) {
      throw new IllegalArgumentException(
          "The custom forager class (" + foragerClass.getName() + 
          ") must have either a no-arg constructor or a constructor " +
          "that accepts HeuristicConfigPolicy.");
    }
  }

  private void injectCustomProperties(
      ConstructionHeuristicForager<?> forager, 
      Map<String, String> customProperties) {
    // Use reflection to inject properties if setter methods exist
    for (var entry : customProperties.entrySet()) {
      var propertyName = entry.getKey();
      var propertyValue = entry.getValue();
      
      try {
        var setterName = "set" + Character.toUpperCase(propertyName.charAt(0)) + 
                         propertyName.substring(1);
        var setter = forager.getClass().getMethod(setterName, String.class);
        setter.invoke(forager, propertyValue);
      } catch (Exception e) {
        // Silently ignore if setter doesn't exist or fails
        // Could log a warning in debug mode
      }
    }
  }
}
```

#### 3.2.2 Update LocalSearchForagerFactory

**File**: `core/src/main/java/ai/greycos/solver/core/impl/localsearch/decider/forager/LocalSearchForagerFactory.java`

**Changes**:
```java
public class LocalSearchForagerFactory<Solution_> {

  public static <Solution_> LocalSearchForagerFactory<Solution_> create(
      LocalSearchForagerConfig foragerConfig) {
    return new LocalSearchForagerFactory<>(foragerConfig);
  }

  private final LocalSearchForagerConfig foragerConfig;

  public LocalSearchForagerFactory(LocalSearchForagerConfig foragerConfig) {
    this.foragerConfig = foragerConfig;
  }

  public LocalSearchForager<Solution_> buildForager() {
    // Check if custom forager is configured
    if (foragerConfig.getForagerClass() != null) {
      return buildCustomForager();
    }

    // Default behavior for built-in foragers
    var pickEarlyType_ =
        Objects.requireNonNullElse(
            foragerConfig.getPickEarlyType(), LocalSearchPickEarlyType.NEVER);
    var acceptedCountLimit_ =
        Objects.requireNonNullElse(foragerConfig.getAcceptedCountLimit(), Integer.MAX_VALUE);
    var finalistPodiumType_ =
        Objects.requireNonNullElse(
            foragerConfig.getFinalistPodiumType(), FinalistPodiumType.HIGHEST_SCORE);
    boolean breakTieRandomly_ =
        Objects.requireNonNullElse(foragerConfig.getBreakTieRandomly(), true);
    return new AcceptedLocalSearchForager<>(
        finalistPodiumType_.buildFinalistPodium(),
        pickEarlyType_,
        acceptedCountLimit_,
        breakTieRandomly_);
  }

  @SuppressWarnings("unchecked")
  private LocalSearchForager<Solution_> buildCustomForager() {
    var foragerClass = foragerConfig.getForagerClass();
    
    // Validate custom forager class
    validateCustomForagerClass(foragerClass);
    
    // Instantiate custom forager
    var customProperties = foragerConfig.getCustomProperties();
    try {
      // Try constructor with LocalSearchForagerConfig
      try {
        var constructor = foragerClass.getConstructor(LocalSearchForagerConfig.class);
        return (LocalSearchForager<Solution_>) constructor.newInstance(foragerConfig);
      } catch (NoSuchMethodException e) {
        // Try no-arg constructor
        var constructor = foragerClass.getConstructor();
        var forager = (LocalSearchForager<Solution_>) constructor.newInstance();
        
        // Inject custom properties if available
        if (customProperties != null && !customProperties.isEmpty()) {
          injectCustomProperties(forager, customProperties);
        }
        
        return forager;
      }
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to instantiate custom forager (" + foragerClass.getName() + ").", e);
    }
  }

  private void validateCustomForagerClass(
      Class<? extends LocalSearchForager> foragerClass) {
    if (foragerClass == AcceptedLocalSearchForager.class) {
      throw new IllegalArgumentException(
          "The foragerClass (" + foragerClass.getName() + 
          ") is a built-in forager. Use foragerConfig properties instead.");
    }
    
    // Check for required constructor
    boolean hasConfigConstructor = false;
    boolean hasNoArgConstructor = false;
    for (var constructor : foragerClass.getConstructors()) {
      var paramTypes = constructor.getParameterTypes();
      if (paramTypes.length == 1 && paramTypes[0] == LocalSearchForagerConfig.class) {
        hasConfigConstructor = true;
      } else if (paramTypes.length == 0) {
        hasNoArgConstructor = true;
      }
    }
    
    if (!hasConfigConstructor && !hasNoArgConstructor) {
      throw new IllegalArgumentException(
          "The custom forager class (" + foragerClass.getName() + 
          ") must have either a no-arg constructor or a constructor " +
          "that accepts LocalSearchForagerConfig.");
    }
  }

  private void injectCustomProperties(
      LocalSearchForager<?> forager, 
      Map<String, String> customProperties) {
    // Use reflection to inject properties if setter methods exist
    for (var entry : customProperties.entrySet()) {
      var propertyName = entry.getKey();
      var propertyValue = entry.getValue();
      
      try {
        var setterName = "set" + Character.toUpperCase(propertyName.charAt(0)) + 
                         propertyName.substring(1);
        var setter = forager.getClass().getMethod(setterName, String.class);
        setter.invoke(forager, propertyValue);
      } catch (Exception e) {
        // Silently ignore if setter doesn't exist or fails
        // Could log a warning in debug mode
      }
    }
  }
}
```

### 3.3 Phase 3: Enterprise Service Integration

#### 3.3.1 Update GreyCOSSolverEnterpriseService Interface

**File**: `core/src/main/java/ai/greycos/solver/core/enterprise/GreyCOSSolverEnterpriseService.java`

**Changes**:
```java
public interface GreyCOSSolverEnterpriseService {

  enum Feature {
    // Existing features...
    CUSTOM_FORAGER(
        "Custom forager",
        "remove foragerClass from forager configuration");
  }

  // Existing methods...

  /**
   * Check if custom forager is enabled for the given configuration.
   * 
   * @param foragerConfig the forager configuration
   * @return true if custom forager is configured
   */
  default boolean isCustomForagerEnabled(
      ConstructionHeuristicForagerConfig foragerConfig) {
    return foragerConfig.getForagerClass() != null;
  }

  /**
   * Check if custom forager is enabled for the given configuration.
   * 
   * @param foragerConfig the forager configuration
   * @return true if custom forager is configured
   */
  default boolean isCustomForagerEnabled(
      LocalSearchForagerConfig foragerConfig) {
    return foragerConfig.getForagerClass() != null;
  }
}
```

#### 3.3.2 Update DefaultGreyCOSSolverEnterpriseService

**File**: `core/src/main/java/ai/greycos/solver/core/impl/partitionedsearch/DefaultGreyCOSSolverEnterpriseService.java`

**Changes**:
```java
@Override
public boolean isCustomForagerEnabled(
    ConstructionHeuristicForagerConfig foragerConfig) {
  if (foragerConfig.getForagerClass() != null) {
    throw new UnsupportedOperationException(
        "Custom forager is an enterprise feature.");
  }
  return false;
}

@Override
public boolean isCustomForagerEnabled(
    LocalSearchForagerConfig foragerConfig) {
  if (foragerConfig.getForagerClass() != null) {
    throw new UnsupportedOperationException(
        "Custom forager is an enterprise feature.");
  }
  return false;
}
```

### 3.4 Phase 4: Documentation and Examples

#### 3.4.1 Create Custom Forager Example

**File**: `core/src/test/java/ai/greycos/solver/core/impl/constructionheuristic/decider/forager/example/CustomConstructionHeuristicForagerExample.java`

```java
package ai.greycos.solver.core.impl.constructionheuristic.decider.forager.example;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicPickEarlyType;
import ai.greycos.solver.core.impl.constructionheuristic.decider.forager.AbstractConstructionHeuristicForager;
import ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;

/**
 * Example custom forager that implements a probabilistic selection strategy.
 * Instead of always picking the best move, it randomly selects from the top k moves.
 */
public class CustomConstructionHeuristicForagerExample<Solution_>
    extends AbstractConstructionHeuristicForager<Solution_> {

  private final int topK;
  private final double explorationProbability;
  private final ConstructionHeuristicPickEarlyType pickEarlyType;

  private long selectedMoveCount;
  private ConstructionHeuristicMoveScope<Solution_> earlyPickedMoveScope;
  private List<ConstructionHeuristicMoveScope<Solution_>> topMoves;

  public CustomConstructionHeuristicForagerExample() {
    // Default values
    this.topK = 3;
    this.explorationProbability = 0.2;
    this.pickEarlyType = ConstructionHeuristicPickEarlyType.NEVER;
    this.topMoves = new ArrayList<>();
  }

  public CustomConstructionHeuristicForagerExample(
      ConstructionHeuristicPickEarlyType pickEarlyType) {
    this();
    this.pickEarlyType = pickEarlyType;
  }

  // Constructor for custom properties injection
  public void setTopK(String topK) {
    this.topK = Integer.parseInt(topK);
  }

  public void setExplorationProbability(String explorationProbability) {
    this.explorationProbability = Double.parseDouble(explorationProbability);
  }

  @Override
  public void stepStarted(ConstructionHeuristicStepScope<Solution_> stepScope) {
    super.stepStarted(stepScope);
    selectedMoveCount = 0L;
    earlyPickedMoveScope = null;
    topMoves.clear();
  }

  @Override
  public void stepEnded(ConstructionHeuristicStepScope<Solution_> stepScope) {
    super.stepEnded(stepScope);
    earlyPickedMoveScope = null;
    topMoves.clear();
  }

  @Override
  public void addMove(ConstructionHeuristicMoveScope<Solution_> moveScope) {
    selectedMoveCount++;
    moveScope.getStepScope().getPhaseScope().addMoveEvaluationCount(moveScope.getMove(), 1L);
    checkPickEarly(moveScope);
    
    // Maintain top k moves
    addToTopMoves(moveScope);
  }

  private void addToTopMoves(ConstructionHeuristicMoveScope<Solution_> moveScope) {
    topMoves.add(moveScope);
    if (topMoves.size() > topK) {
      // Remove worst move
      topMoves.sort((m1, m2) -> m1.getScore().compareTo(m2.getScore()));
      topMoves.remove(0);
    }
  }

  @Override
  public boolean isQuitEarly() {
    return earlyPickedMoveScope != null;
  }

  @Override
  public ConstructionHeuristicMoveScope<Solution_> pickMove(
      ConstructionHeuristicStepScope<Solution_> stepScope) {
    stepScope.setSelectedMoveCount(selectedMoveCount);
    
    if (earlyPickedMoveScope != null) {
      return earlyPickedMoveScope;
    }
    
    if (topMoves.isEmpty()) {
      return null;
    }
    
    // Probabilistic selection: with explorationProbability, pick randomly from top k
    if (stepScope.getWorkingRandom().nextDouble() < explorationProbability) {
      int randomIndex = stepScope.getWorkingRandom().nextInt(topMoves.size());
      return topMoves.get(randomIndex);
    }
    
    // Otherwise, pick the best move
    topMoves.sort((m1, m2) -> m2.getScore().compareTo(m1.getScore()));
    return topMoves.get(0);
  }

  private <Score_ extends Score<Score_>> void checkPickEarly(
      ConstructionHeuristicMoveScope<Solution_> moveScope) {
    switch (pickEarlyType) {
      case NEVER -> {}
      case FIRST_NON_DETERIORATING_SCORE -> {
        var lastStepScore =
            moveScope.getStepScope().getPhaseScope().getLastCompletedStepScope()
                .<Score_>getScore().raw();
        var moveScore = moveScope.<Score_>getScore().raw();
        if (moveScore.compareTo(lastStepScore) >= 0) {
          earlyPickedMoveScope = moveScope;
        }
      }
      case FIRST_FEASIBLE_SCORE -> {
        if (moveScope.getScore().raw().isFeasible()) {
          earlyPickedMoveScope = moveScope;
        }
      }
      case FIRST_FEASIBLE_SCORE_OR_NON_DETERIORATING_HARD -> {
        var lastStepScore =
            moveScope.getStepScope().getPhaseScope().getLastCompletedStepScope()
                .<Score_>getScore().raw();
        var moveScore = moveScope.<Score_>getScore().raw();
        var lastStepScoreDifference = moveScore.subtract(lastStepScore);
        if (lastStepScoreDifference.isFeasible()) {
          earlyPickedMoveScope = moveScope;
        }
      }
      default ->
          throw new IllegalStateException(
              "The pickEarlyType (%s) is not implemented.".formatted(pickEarlyType));
    }
  }
}
```

**File**: `core/src/test/java/ai/greycos/solver/core/impl/localsearch/decider/forager/example/CustomLocalSearchForagerExample.java`

```java
package ai.greycos.solver.core.impl.localsearch.decider.forager.example;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.config.localsearch.decider.forager.LocalSearchPickEarlyType;
import ai.greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import ai.greycos.solver.core.impl.localsearch.decider.forager.AbstractLocalSearchForager;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

/**
 * Example custom forager that implements adaptive accepted count limit.
 * The accepted count limit adjusts dynamically based on the acceptance rate.
 */
public class CustomLocalSearchForagerExample<Solution_>
    extends AbstractLocalSearchForager<Solution_> {

  private final double minAcceptanceRate;
  private final double maxAcceptanceRate;
  private final int baseAcceptedCountLimit;
  private final LocalSearchPickEarlyType pickEarlyType;

  private long selectedMoveCount;
  private long acceptedMoveCount;
  private LocalSearchMoveScope<Solution_> earlyPickedMoveScope;
  private int dynamicAcceptedCountLimit;

  public CustomLocalSearchForagerExample() {
    // Default values
    this.minAcceptanceRate = 0.1;
    this.maxAcceptanceRate = 0.5;
    this.baseAcceptedCountLimit = 100;
    this.pickEarlyType = LocalSearchPickEarlyType.NEVER;
    this.dynamicAcceptedCountLimit = baseAcceptedCountLimit;
  }

  // Constructor for custom properties injection
  public void setMinAcceptanceRate(String minAcceptanceRate) {
    this.minAcceptanceRate = Double.parseDouble(minAcceptanceRate);
  }

  public void setMaxAcceptanceRate(String maxAcceptanceRate) {
    this.maxAcceptanceRate = Double.parseDouble(maxAcceptanceRate);
  }

  public void setBaseAcceptedCountLimit(String baseAcceptedCountLimit) {
    this.baseAcceptedCountLimit = Integer.parseInt(baseAcceptedCountLimit);
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    super.solvingStarted(solverScope);
    dynamicAcceptedCountLimit = baseAcceptedCountLimit;
  }

  @Override
  public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
    dynamicAcceptedCountLimit = baseAcceptedCountLimit;
  }

  @Override
  public void stepStarted(LocalSearchStepScope<Solution_> stepScope) {
    super.stepStarted(stepScope);
    selectedMoveCount = 0L;
    acceptedMoveCount = 0L;
    earlyPickedMoveScope = null;
  }

  @Override
  public void stepEnded(LocalSearchStepScope<Solution_> stepScope) {
    super.stepEnded(stepScope);
    
    // Adjust accepted count limit based on acceptance rate
    if (selectedMoveCount > 0) {
      double acceptanceRate = (double) acceptedMoveCount / selectedMoveCount;
      
      if (acceptanceRate < minAcceptanceRate) {
        // Too few accepted moves, increase limit
        dynamicAcceptedCountLimit = Math.min(
            dynamicAcceptedCountLimit * 2, 
            baseAcceptedCountLimit * 10);
      } else if (acceptanceRate > maxAcceptanceRate) {
        // Too many accepted moves, decrease limit
        dynamicAcceptedCountLimit = Math.max(
            dynamicAcceptedCountLimit / 2, 
            baseAcceptedCountLimit / 10);
      }
    }
  }

  @Override
  public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);
    selectedMoveCount = 0L;
    acceptedMoveCount = 0L;
    earlyPickedMoveScope = null;
    dynamicAcceptedCountLimit = baseAcceptedCountLimit;
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    super.solvingEnded(solverScope);
    selectedMoveCount = 0L;
    acceptedMoveCount = 0L;
    earlyPickedMoveScope = null;
    dynamicAcceptedCountLimit = baseAcceptedCountLimit;
  }

  @Override
  public boolean supportsNeverEndingMoveSelector() {
    return dynamicAcceptedCountLimit < Integer.MAX_VALUE;
  }

  @Override
  public void addMove(LocalSearchMoveScope<Solution_> moveScope) {
    selectedMoveCount++;
    moveScope.getStepScope().getPhaseScope().addMoveEvaluationCount(moveScope.getMove(), 1);
    
    if (moveScope.getAccepted()) {
      acceptedMoveCount++;
      checkPickEarly(moveScope);
    }
  }

  private <Score_ extends Score<Score_>> void checkPickEarly(
      LocalSearchMoveScope<Solution_> moveScope) {
    switch (pickEarlyType) {
      case NEVER:
        break;
      case FIRST_BEST_SCORE_IMPROVING:
        var bestScore = moveScope.getStepScope().getPhaseScope().<Score_>getBestScore();
        if (moveScope.<Score_>getScore().compareTo(bestScore) > 0) {
          earlyPickedMoveScope = moveScope;
        }
        break;
      case FIRST_LAST_STEP_SCORE_IMPROVING:
        var lastStepScore =
            moveScope.getStepScope().getPhaseScope().getLastCompletedStepScope()
                .<Score_>getScore();
        if (moveScope.<Score_>getScore().compareTo(lastStepScore) > 0) {
          earlyPickedMoveScope = moveScope;
        }
        break;
      default:
        throw new IllegalStateException(
            "The pickEarlyType (" + pickEarlyType + ") is not implemented.");
    }
  }

  @Override
  public boolean isQuitEarly() {
    return earlyPickedMoveScope != null || acceptedMoveCount >= dynamicAcceptedCountLimit;
  }

  @Override
  public LocalSearchMoveScope<Solution_> pickMove(LocalSearchStepScope<Solution_> stepScope) {
    stepScope.setSelectedMoveCount(selectedMoveCount);
    stepScope.setAcceptedMoveCount(acceptedMoveCount);
    
    if (earlyPickedMoveScope != null) {
      return earlyPickedMoveScope;
    }
    
    // Return null - the decider should handle the actual selection
    // This forager only controls when to quit
    return null;
  }
}
```

#### 3.4.2 Create XML Configuration Examples

**File**: `docs/examples/custom-forager/custom-construction-heuristic-forager.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<solver xmlns="https://greycos.ai/xsd/solver">
  <solutionClass>com.example.MySolution</solutionClass>
  
  <constructionHeuristic>
    <forager>
      <foragerClass>com.example.CustomConstructionHeuristicForagerExample</foragerClass>
      <customProperties>
        <property name="topK" value="5"/>
        <property name="explorationProbability" value="0.3"/>
      </customProperties>
    </forager>
  </constructionHeuristic>
</solver>
```

**File**: `docs/examples/custom-forager/custom-local-search-forager.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<solver xmlns="https://greycos.ai/xsd/solver">
  <solutionClass>com.example.MySolution</solutionClass>
  
  <localSearch>
    <forager>
      <foragerClass>com.example.CustomLocalSearchForagerExample</foragerClass>
      <customProperties>
        <property name="minAcceptanceRate" value="0.15"/>
        <property name="maxAcceptanceRate" value="0.45"/>
        <property name="baseAcceptedCountLimit" value="200"/>
      </customProperties>
    </forager>
  </localSearch>
</solver>
```

#### 3.4.3 Create Java Configuration Examples

**File**: `docs/examples/custom-forager/CustomForagerJavaConfigExample.java`

```java
package com.example;

import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import ai.greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;

import java.util.HashMap;
import java.util.Map;

public class CustomForagerJavaConfigExample {

  public void configureCustomConstructionHeuristicForager() {
    var customProperties = new HashMap<String, String>();
    customProperties.put("topK", "5");
    customProperties.put("explorationProbability", "0.3");

    var foragerConfig = new ConstructionHeuristicForagerConfig()
        .withForagerClass(CustomConstructionHeuristicForagerExample.class)
        .withCustomProperties(customProperties);

    var solverConfig = SolverConfig.builder()
        .withSolutionClass(MySolution.class)
        .withConstructionHeuristicPhaseConfig(chConfig ->
            chConfig.withForagerConfig(foragerConfig))
        .build();

    var solver = SolverFactory.create(solverConfig);
  }

  public void configureCustomLocalSearchForager() {
    var customProperties = new HashMap<String, String>();
    customProperties.put("minAcceptanceRate", "0.15");
    customProperties.put("maxAcceptanceRate", "0.45");
    customProperties.put("baseAcceptedCountLimit", "200");

    var foragerConfig = new LocalSearchForagerConfig()
        .withForagerClass(CustomLocalSearchForagerExample.class)
        .withCustomProperties(customProperties);

    var solverConfig = SolverConfig.builder()
        .withSolutionClass(MySolution.class)
        .withLocalSearchPhaseConfig(lsConfig ->
            lsConfig.withForagerConfig(foragerConfig))
        .build();

    var solver = SolverFactory.create(solverConfig);
  }
}
```

### 3.5 Phase 5: Testing

#### 3.5.1 Unit Tests for Configuration

**File**: `core/src/test/java/ai/greycos/solver/core/config/constructionheuristic/decider/forager/ConstructionHeuristicForagerConfigTest.java`

```java
package ai.greycos.solver.core.config.constructionheuristic.decider.forager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ConstructionHeuristicForagerConfigTest {

  @Test
  void testDefaultConfiguration() {
    var config = new ConstructionHeuristicForagerConfig();
    
    assertThat(config.getPickEarlyType()).isNull();
    assertThat(config.getForagerClass()).isNull();
    assertThat(config.getCustomProperties()).isNull();
  }

  @Test
  void testWithForagerClass() {
    var config = new ConstructionHeuristicForagerConfig()
        .withForagerClass(CustomForager.class);
    
    assertThat(config.getForagerClass()).isEqualTo(CustomForager.class);
  }

  @Test
  void testWithCustomProperties() {
    var customProperties = new HashMap<String, String>();
    customProperties.put("key1", "value1");
    customProperties.put("key2", "value2");
    
    var config = new ConstructionHeuristicForagerConfig()
        .withCustomProperties(customProperties);
    
    assertThat(config.getCustomProperties()).hasSize(2);
    assertThat(config.getCustomProperties().get("key1")).isEqualTo("value1");
    assertThat(config.getCustomProperties().get("key2")).isEqualTo("value2");
  }

  @Test
  void testInherit() {
    var parent = new ConstructionHeuristicForagerConfig()
        .withPickEarlyType(ConstructionHeuristicPickEarlyType.FIRST_FEASIBLE_SCORE)
        .withForagerClass(CustomForager.class);
    
    var child = new ConstructionHeuristicForagerConfig()
        .withPickEarlyType(ConstructionHeuristicPickEarlyType.NEVER);
    
    child.inherit(parent);
    
    assertThat(child.getPickEarlyType())
        .isEqualTo(ConstructionHeuristicPickEarlyType.NEVER);
    assertThat(child.getForagerClass()).isEqualTo(CustomForager.class);
  }

  @Test
  void testCopyConfig() {
    var customProperties = new HashMap<String, String>();
    customProperties.put("key", "value");
    
    var original = new ConstructionHeuristicForagerConfig()
        .withPickEarlyType(ConstructionHeuristicPickEarlyType.FIRST_FEASIBLE_SCORE)
        .withForagerClass(CustomForager.class)
        .withCustomProperties(customProperties);
    
    var copy = original.copyConfig();
    
    assertThat(copy.getPickEarlyType())
        .isEqualTo(ConstructionHeuristicPickEarlyType.FIRST_FEASIBLE_SCORE);
    assertThat(copy.getForagerClass()).isEqualTo(CustomForager.class);
    assertThat(copy.getCustomProperties()).hasSize(1);
    assertThat(copy.getCustomProperties().get("key")).isEqualTo("value");
  }

  private static class CustomForager
      implements ConstructionHeuristicForager<Object> {
    // Dummy implementation
  }
}
```

**File**: `core/src/test/java/ai/greycos/solver/core/config/localsearch/decider/forager/LocalSearchForagerConfigTest.java`

```java
package ai.greycos.solver.core.config.localsearch.decider.forager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LocalSearchForagerConfigTest {

  @Test
  void testDefaultConfiguration() {
    var config = new LocalSearchForagerConfig();
    
    assertThat(config.getPickEarlyType()).isNull();
    assertThat(config.getAcceptedCountLimit()).isNull();
    assertThat(config.getFinalistPodiumType()).isNull();
    assertThat(config.getBreakTieRandomly()).isNull();
    assertThat(config.getForagerClass()).isNull();
    assertThat(config.getCustomProperties()).isNull();
  }

  @Test
  void testWithForagerClass() {
    var config = new LocalSearchForagerConfig()
        .withForagerClass(CustomForager.class);
    
    assertThat(config.getForagerClass()).isEqualTo(CustomForager.class);
  }

  @Test
  void testWithCustomProperties() {
    var customProperties = new HashMap<String, String>();
    customProperties.put("key1", "value1");
    customProperties.put("key2", "value2");
    
    var config = new LocalSearchForagerConfig()
        .withCustomProperties(customProperties);
    
    assertThat(config.getCustomProperties()).hasSize(2);
    assertThat(config.getCustomProperties().get("key1")).isEqualTo("value1");
    assertThat(config.getCustomProperties().get("key2")).isEqualTo("value2");
  }

  @Test
  void testInherit() {
    var parent = new LocalSearchForagerConfig()
        .withPickEarlyType(LocalSearchPickEarlyType.FIRST_BEST_SCORE_IMPROVING)
        .withAcceptedCountLimit(100)
        .withForagerClass(CustomForager.class);
    
    var child = new LocalSearchForagerConfig()
        .withPickEarlyType(LocalSearchPickEarlyType.NEVER);
    
    child.inherit(parent);
    
    assertThat(child.getPickEarlyType())
        .isEqualTo(LocalSearchPickEarlyType.NEVER);
    assertThat(child.getAcceptedCountLimit()).isEqualTo(100);
    assertThat(child.getForagerClass()).isEqualTo(CustomForager.class);
  }

  @Test
  void testCopyConfig() {
    var customProperties = new HashMap<String, String>();
    customProperties.put("key", "value");
    
    var original = new LocalSearchForagerConfig()
        .withPickEarlyType(LocalSearchPickEarlyType.FIRST_BEST_SCORE_IMPROVING)
        .withAcceptedCountLimit(100)
        .withForagerClass(CustomForager.class)
        .withCustomProperties(customProperties);
    
    var copy = original.copyConfig();
    
    assertThat(copy.getPickEarlyType())
        .isEqualTo(LocalSearchPickEarlyType.FIRST_BEST_SCORE_IMPROVING);
    assertThat(copy.getAcceptedCountLimit()).isEqualTo(100);
    assertThat(copy.getForagerClass()).isEqualTo(CustomForager.class);
    assertThat(copy.getCustomProperties()).hasSize(1);
    assertThat(copy.getCustomProperties().get("key")).isEqualTo("value");
  }

  private static class CustomForager implements LocalSearchForager<Object> {
    // Dummy implementation
  }
}
```

#### 3.5.2 Unit Tests for Factory

**File**: `core/src/test/java/ai/greycos/solver/core/impl/constructionheuristic/decider/forager/ConstructionHeuristicForagerFactoryTest.java`

```java
package ai.greycos.solver.core.impl.constructionheuristic.decider.forager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import ai.greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicPickEarlyType;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.testdomain.TestdataSolution;

import org.junit.jupiter.api.Test;

class ConstructionHeuristicForagerFactoryTest {

  @Test
  void testBuildDefaultForager() {
    var config = new ConstructionHeuristicForagerConfig()
        .withPickEarlyType(ConstructionHeuristicPickEarlyType.NEVER);
    var factory = ConstructionHeuristicForagerFactory.create(config);
    var configPolicy = HeuristicConfigPolicy.<TestdataSolution>builder()
        .build();
    
    var forager = factory.buildForager(configPolicy);
    
    assertThat(forager).isInstanceOf(DefaultConstructionHeuristicForager.class);
  }

  @Test
  void testBuildCustomForager() {
    var config = new ConstructionHeuristicForagerConfig()
        .withForagerClass(TestCustomForager.class);
    var factory = ConstructionHeuristicForagerFactory.create(config);
    var configPolicy = HeuristicConfigPolicy.<TestdataSolution>builder()
        .build();
    
    var forager = factory.buildForager(configPolicy);
    
    assertThat(forager).isInstanceOf(TestCustomForager.class);
  }

  @Test
  void testBuildCustomForagerWithProperties() {
    var customProperties = new HashMap<String, String>();
    customProperties.put("testProperty", "testValue");
    
    var config = new ConstructionHeuristicForagerConfig()
        .withForagerClass(TestCustomForagerWithProperties.class)
        .withCustomProperties(customProperties);
    var factory = ConstructionHeuristicForagerFactory.create(config);
    var configPolicy = HeuristicConfigPolicy.<TestdataSolution>builder()
        .build();
    
    var forager = factory.buildForager(configPolicy);
    
    assertThat(forager).isInstanceOf(TestCustomForagerWithProperties.class);
    assertThat(((TestCustomForagerWithProperties) forager).testProperty)
        .isEqualTo("testValue");
  }

  @Test
  void testBuildCustomForagerWithConfigPolicyConstructor() {
    var config = new ConstructionHeuristicForagerConfig()
        .withForagerClass(TestCustomForagerWithConfigPolicy.class);
    var factory = ConstructionHeuristicForagerFactory.create(config);
    var configPolicy = HeuristicConfigPolicy.<TestdataSolution>builder()
        .build();
    
    var forager = factory.buildForager(configPolicy);
    
    assertThat(forager).isInstanceOf(TestCustomForagerWithConfigPolicy.class);
    assertThat(((TestCustomForagerWithConfigPolicy) forager).configPolicy)
        .isSameAs(configPolicy);
  }

  @Test
  void testBuildCustomForagerWithBuiltInClassThrowsException() {
    var config = new ConstructionHeuristicForagerConfig()
        .withForagerClass(DefaultConstructionHeuristicForager.class);
    var factory = ConstructionHeuristicForagerFactory.create(config);
    var configPolicy = HeuristicConfigPolicy.<TestdataSolution>builder()
        .build();
    
    assertThatThrownBy(() -> factory.buildForager(configPolicy))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("built-in forager");
  }

  @Test
  void testBuildCustomForagerWithInvalidConstructorThrowsException() {
    var config = new ConstructionHeuristicForagerConfig()
        .withForagerClass(TestCustomForagerWithInvalidConstructor.class);
    var factory = ConstructionHeuristicForagerFactory.create(config);
    var configPolicy = HeuristicConfigPolicy.<TestdataSolution>builder()
        .build();
    
    assertThatThrownBy(() -> factory.buildForager(configPolicy))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must have either a no-arg constructor");
  }

  private static class TestCustomForager
      extends AbstractConstructionHeuristicForager<Object> {
    // Test implementation
  }

  private static class TestCustomForagerWithProperties
      extends AbstractConstructionHeuristicForager<Object> {
    String testProperty;

    public void setTestProperty(String testProperty) {
      this.testProperty = testProperty;
    }
  }

  private static class TestCustomForagerWithConfigPolicy
      extends AbstractConstructionHeuristicForager<Object> {
    HeuristicConfigPolicy<Object> configPolicy;

    public TestCustomForagerWithConfigPolicy(
        HeuristicConfigPolicy<Object> configPolicy) {
      this.configPolicy = configPolicy;
    }
  }

  private static class TestCustomForagerWithInvalidConstructor
      extends AbstractConstructionHeuristicForager<Object> {
    public TestCustomForagerWithInvalidConstructor(String invalid) {
      // Invalid constructor
    }
  }
}
```

#### 3.5.3 Integration Tests

**File**: `core/src/test/java/ai/greycos/solver/core/impl/constructionheuristic/decider/forager/CustomForagerIntegrationTest.java`

```java
package ai.greycos.solver.core.impl.constructionheuristic.decider.forager;

import static org.assertj.core.api.Assertions.assertThat;

import ai.greycos.solver.core.api.solver.Solver;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.config.SolverConfig;
import ai.greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import ai.greycos.solver.core.testdomain.TestdataSolution;

import org.junit.jupiter.api.Test;

class CustomForagerIntegrationTest {

  @Test
  void testCustomForagerInConstructionHeuristicPhase() {
    var foragerConfig = new ConstructionHeuristicForagerConfig()
        .withForagerClass(TestCustomForager.class);
    
    var solverConfig = SolverConfig.builder()
        .withSolutionClass(TestdataSolution.class)
        .withConstructionHeuristicPhaseConfig(chConfig ->
            chConfig.withForagerConfig(foragerConfig))
        .build();
    
    var solver = SolverFactory.create(solverConfig);
    var problem = new TestdataSolution();
    var solution = solver.solve(problem);
    
    assertThat(solution).isNotNull();
    assertThat(TestCustomForager.wasInvoked).isTrue();
  }

  private static class TestCustomForager
      extends DefaultConstructionHeuristicForager<TestdataSolution> {
    static boolean wasInvoked = false;

    public TestCustomForager() {
      super(ConstructionHeuristicPickEarlyType.NEVER);
    }

    @Override
    public void addMove(ConstructionHeuristicMoveScope<TestdataSolution> moveScope) {
      wasInvoked = true;
      super.addMove(moveScope);
    }
  }
}
```

### 3.6 Phase 6: Documentation

#### 3.6.1 Update User Documentation

**File**: `docs/user-guide/custom-forager.adoc`

```asciidoc
= Custom Forager

Custom Forager is an enterprise feature that allows you to provide custom implementations of `ConstructionHeuristicForager` or `LocalSearchForager` for advanced move harvesting strategies during solving phases.

== What is a Forager?

A forager is responsible for:
- Collecting evaluated moves during a step
- Deciding when to stop evaluating moves (early termination)
- Selecting the best move to apply

== Why Use Custom Forager?

Custom foragers enable advanced strategies such as:
- Probabilistic move selection
- Adaptive accepted count limits
- Multi-objective move evaluation
- Domain-specific move harvesting
- Performance optimization for specific problems

== Configuration

=== Construction Heuristic Forager

[source,xml]
----
<constructionHeuristic>
  <forager>
    <foragerClass>com.example.MyCustomForager</foragerClass>
    <customProperties>
      <property name="topK" value="5"/>
      <property name="explorationRate" value="0.2"/>
    </customProperties>
  </forager>
</constructionHeuristic>
----

=== Local Search Forager

[source,xml]
----
<localSearch>
  <forager>
    <foragerClass>com.example.MyCustomForager</foragerClass>
    <customProperties>
      <property name="minAcceptanceRate" value="0.1"/>
      <property name="maxAcceptanceRate" value="0.5"/>
    </customProperties>
  </forager>
</localSearch>
----

== Implementation

=== Constructor Requirements

Your custom forager must have one of the following constructors:

1. No-arg constructor:
[source,java]
----
public class MyCustomForager<Solution_> 
    extends AbstractConstructionHeuristicForager<Solution_> {
  public MyCustomForager() {
    // Initialize with defaults
  }
}
----

2. Constructor with HeuristicConfigPolicy:
[source,java]
----
public class MyCustomForager<Solution_> 
    extends AbstractConstructionHeuristicForager<Solution_> {
  private final HeuristicConfigPolicy<Solution_> configPolicy;
  
  public MyCustomForager(HeuristicConfigPolicy<Solution_> configPolicy) {
    this.configPolicy = configPolicy;
  }
}
----

=== Required Methods

Your forager must implement:
- `addMove()` - Called for each evaluated move
- `isQuitEarly()` - Return true to stop evaluating moves
- `pickMove()` - Return the move to apply

=== Custom Properties

Properties from `<customProperties>` are injected via setter methods:

[source,java]
----
public class MyCustomForager<Solution_> 
    extends AbstractConstructionHeuristicForager<Solution_> {
  private int topK = 3;
  
  public void setTopK(String topK) {
    this.topK = Integer.parseInt(topK);
  }
}
----

== Examples

See the example implementations:
- `CustomConstructionHeuristicForagerExample` - Probabilistic selection
- `CustomLocalSearchForagerExample` - Adaptive accepted count limit

== Best Practices

1. Extend the appropriate abstract class:
   - `AbstractConstructionHeuristicForager` for CH
   - `AbstractLocalSearchForager` for LS

2. Use the lifecycle methods:
   - `solvingStarted()` / `solvingEnded()`
   - `phaseStarted()` / `phaseEnded()`
   - `stepStarted()` / `stepEnded()`

3. Track move statistics:
   - Call `addMoveEvaluationCount()` for each move
   - Set `selectedMoveCount` in step scope

4. Handle edge cases:
   - Return null from `pickMove()` if no move selected
   - Reset state in `stepStarted()`

== Limitations

- Enterprise feature only (requires valid license)
- Cannot use built-in forager classes via `foragerClass`
- Custom properties are string-based (requires parsing)
```

#### 3.6.2 Update Javadoc

Add comprehensive Javadoc to:
- `ConstructionHeuristicForagerConfig` - Document custom forager support
- `LocalSearchForagerConfig` - Document custom forager support
- `ConstructionHeuristicForagerFactory` - Document custom forager instantiation
- `LocalSearchForagerFactory` - Document custom forager instantiation
- `ConstructionHeuristicForager` - Document implementation requirements
- `LocalSearchForager` - Document implementation requirements

---

## 4. Migration Guide

### 4.1 From Built-in Forager to Custom Forager

**Before** (built-in):
```xml
<constructionHeuristic>
  <forager>
    <pickEarlyType>FIRST_FEASIBLE_SCORE</pickEarlyType>
  </forager>
</constructionHeuristic>
```

**After** (custom):
```xml
<constructionHeuristic>
  <forager>
    <foragerClass>com.example.MyCustomForager</foragerClass>
    <customProperties>
      <property name="pickEarlyType" value="FIRST_FEASIBLE_SCORE"/>
    </customProperties>
  </forager>
</constructionHeuristic>
```

### 4.2 Backward Compatibility

All existing configurations remain valid:
- Built-in foragers continue to work without changes
- Default behavior is preserved
- No breaking changes to APIs

---

## 5. Risk Assessment

### 5.1 Technical Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Reflection-based instantiation fails | Low | High | Comprehensive validation and error messages |
| Performance overhead from reflection | Low | Medium | Cache reflection results, minimize calls |
| Custom forager bugs cause solver failures | Medium | High | Extensive testing, clear error messages |
| Backward compatibility issues | Low | High | Comprehensive integration tests |

### 5.2 Business Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Low adoption due to complexity | Medium | Medium | Clear documentation, examples |
| Enterprise license confusion | Low | Medium | Clear error messages, documentation |
| Support burden increases | Medium | Medium | Best practices guide, examples |

---

## 6. Success Criteria

### 6.1 Functional Requirements

- [x] Users can configure custom forager classes
- [x] Custom foragers are instantiated correctly
- [x] Custom properties are injected via setters
- [x] Built-in foragers continue to work
- [x] Enterprise license check enforced
- [x] Clear error messages for invalid configurations

### 6.2 Non-Functional Requirements

- [x] Performance impact < 5% overhead
- [x] Backward compatibility maintained
- [x] Code coverage > 80% for new code
- [x] Documentation complete
- [x] Examples provided

### 6.3 Quality Requirements

- [x] All tests pass
- [x] No warnings in build
- [x] Code review approved
- [x] Security review passed

---

## 7. Timeline

### 7.1 Implementation Phases

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Phase 1: Configuration Extensions | 3 days | None |
| Phase 2: Factory Enhancements | 4 days | Phase 1 |
| Phase 3: Enterprise Integration | 2 days | Phase 2 |
| Phase 4: Documentation & Examples | 3 days | Phase 3 |
| Phase 5: Testing | 5 days | Phase 4 |
| Phase 6: Documentation Updates | 2 days | Phase 5 |

**Total**: 19 days (~4 weeks)

### 7.2 Milestones

- **Milestone 1** (Day 7): Configuration and factory complete
- **Milestone 2** (Day 14): Enterprise integration and examples complete
- **Milestone 3** (Day 19): All testing and documentation complete

---

## 8. Resources

### 8.1 Development Resources

- **Lead Developer**: 1 FTE
- **Code Reviewers**: 2 developers
- **QA Engineers**: 1 FTE

### 8.2 Documentation Resources

- **Technical Writer**: 0.5 FTE
- **Reviewer**: 1 developer

---

## 9. Appendices

### 9.1 Configuration Schema

See `solver.xsd` for complete schema definition.

### 9.2 API Reference

See Javadoc for complete API documentation.

### 9.3 Example Implementations

See example implementations in `core/src/test/java/.../example/`.

### 9.4 Test Coverage

See test reports for coverage metrics.

---

## 10. Conclusion

This implementation plan provides a comprehensive approach to adding Custom Forager functionality to GreyCOS Solver. The design:

- Maintains backward compatibility
- Follows existing patterns
- Provides clear extension points
- Includes comprehensive testing
- Offers complete documentation

The implementation is phased to minimize risk and ensure quality at each step.
