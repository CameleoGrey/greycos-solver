package greycos.solver.core.api.solver.alns;

import java.util.Objects;

/** Typed handle to a planning list variable. */
public record AlnsListVariable<Solution_, Entity_, Value_>(
    Class<Entity_> entityClass,
    String variableName,
    Class<Value_> valueClass,
    boolean allowsUnassigned)
    implements AlnsVariable<Solution_> {
  public AlnsListVariable {
    Objects.requireNonNull(entityClass);
    Objects.requireNonNull(variableName);
    Objects.requireNonNull(valueClass);
    if (variableName.isBlank())
      throw new IllegalArgumentException("Variable name must not be blank.");
  }
}
