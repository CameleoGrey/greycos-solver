package ai.greycos.solver.core.impl.constructionheuristic.decider.forager;

import java.util.Map;

import ai.greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import ai.greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicPickEarlyType;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;

/**
 * Factory for creating construction heuristic foragers. Supports built-in foragers via
 * pickEarlyType and custom foragers via foragerClass. Custom properties are injected via setter
 * methods on forager instances.
 */
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
    if (foragerConfig.getForagerClass() != null) {
      return buildCustomForager(configPolicy);
    }

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

    validateCustomForagerClass(foragerClass);

    var customProperties = foragerConfig.getCustomProperties();
    try {
      try {
        var constructor = foragerClass.getConstructor(HeuristicConfigPolicy.class);
        return (ConstructionHeuristicForager<Solution_>) constructor.newInstance(configPolicy);
      } catch (NoSuchMethodException e) {
        var constructor = foragerClass.getConstructor();
        var forager = (ConstructionHeuristicForager<Solution_>) constructor.newInstance();

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
          "The foragerClass ("
              + foragerClass.getName()
              + ") is a built-in forager. Use pickEarlyType configuration instead.");
    }

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
          "The custom forager class ("
              + foragerClass.getName()
              + ") must have either a no-arg constructor or a constructor "
              + "that accepts HeuristicConfigPolicy.");
    }
  }

  private void injectCustomProperties(
      ConstructionHeuristicForager<?> forager, Map<String, String> customProperties) {
    for (var entry : customProperties.entrySet()) {
      var propertyName = entry.getKey();
      var propertyValue = entry.getValue();

      try {
        var setterName =
            "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        var setter = forager.getClass().getMethod(setterName, String.class);
        setter.invoke(forager, propertyValue);
      } catch (Exception e) {
      }
    }
  }
}
