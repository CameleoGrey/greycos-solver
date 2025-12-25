package ai.greycos.solver.core.impl.constructionheuristic.decider.forager;

import java.util.Map;

import ai.greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import ai.greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicPickEarlyType;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;

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
