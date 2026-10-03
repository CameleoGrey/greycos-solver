package greycos.solver.core.api.solver.multistage;

import java.util.Objects;

/**
 * A typed reference to a planning list variable. Equal references identify the same declaration;
 * obtaining a view does not require the same reference instance used in configuration.
 */
public record ListVariableReference<Entity_, Value_>(
    Class<Entity_> entityClass, String variableName, Class<Value_> valueClass)
    implements MultistageVariableReference<Entity_, Value_> {

  public ListVariableReference {
    Objects.requireNonNull(entityClass, "The entityClass must not be null.");
    Objects.requireNonNull(variableName, "The variableName must not be null.");
    Objects.requireNonNull(valueClass, "The valueClass must not be null.");
    if (variableName.isBlank()) {
      throw new IllegalArgumentException(
          "The list variable reference for entityClass (%s) has blank variableName (%s)."
              .formatted(entityClass.getName(), variableName));
    }
  }

  public static <Entity_, Value_> ListVariableReference<Entity_, Value_> of(
      Class<Entity_> entityClass, String variableName, Class<Value_> valueClass) {
    return new ListVariableReference<>(entityClass, variableName, valueClass);
  }
}
