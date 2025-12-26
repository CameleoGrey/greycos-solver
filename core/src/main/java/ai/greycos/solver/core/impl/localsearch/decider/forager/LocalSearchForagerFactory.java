package ai.greycos.solver.core.impl.localsearch.decider.forager;

import java.util.Map;
import java.util.Objects;

import ai.greycos.solver.core.config.localsearch.decider.forager.FinalistPodiumType;
import ai.greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import ai.greycos.solver.core.config.localsearch.decider.forager.LocalSearchPickEarlyType;

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
    // Breaking ties randomly leads to better results statistically
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

  private void validateCustomForagerClass(Class<? extends LocalSearchForager> foragerClass) {
    if (foragerClass == AcceptedLocalSearchForager.class) {
      throw new IllegalArgumentException(
          "The foragerClass ("
              + foragerClass.getName()
              + ") is a built-in forager. Use foragerConfig properties instead.");
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
          "The custom forager class ("
              + foragerClass.getName()
              + ") must have either a no-arg constructor or a constructor "
              + "that accepts LocalSearchForagerConfig.");
    }
  }

  private void injectCustomProperties(
      LocalSearchForager<?> forager, Map<String, String> customProperties) {
    // Use reflection to inject properties if setter methods exist
    for (var entry : customProperties.entrySet()) {
      var propertyName = entry.getKey();
      var propertyValue = entry.getValue();

      try {
        var setterName =
            "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        var setter = forager.getClass().getMethod(setterName, String.class);
        setter.invoke(forager, propertyValue);
      } catch (Exception e) {
        // Silently ignore if setter doesn't exist or fails
        // Could log a warning in debug mode
      }
    }
  }
}
