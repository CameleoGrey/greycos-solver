package greycos.solver.core.api.solver.alns;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A basic entity/variable binding or a list element/variable binding. Identity uses working-object
 * identity, never business equality or coordinates. Entity and value describe the placement when
 * this handle was obtained; use the context for current placement.
 */
public final class AlnsTarget<Solution_> {
  private final AlnsVariable<Solution_> variable;
  private final @Nullable Object entity;
  private final @Nullable Object value;

  public AlnsTarget(
      AlnsVariable<Solution_> variable, @Nullable Object entity, @Nullable Object value) {
    this.variable = Objects.requireNonNull(variable);
    this.entity = entity;
    this.value = value;
    Objects.requireNonNull(
        variable.isList() ? value : entity, "The target identity must not be null.");
  }

  public AlnsVariable<Solution_> variable() {
    return variable;
  }

  public @Nullable Object entity() {
    return entity;
  }

  public @Nullable Object value() {
    return value;
  }

  public boolean isList() {
    return variable.isList();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AlnsTarget<?> target
        && variable.equals(target.variable)
        && (isList() ? value == target.value : entity == target.entity);
  }

  @Override
  public int hashCode() {
    return 31 * variable.hashCode() + System.identityHashCode(isList() ? value : entity);
  }

  @Override
  public String toString() {
    return variable.variableName() + "(" + (isList() ? value : entity) + ")";
  }
}
