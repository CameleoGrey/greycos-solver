package greycos.solver.core.api.solver.alns;

/** A stable, named handle to a genuine planning variable. */
public sealed interface AlnsVariable<Solution_> permits AlnsBasicVariable, AlnsListVariable {
  Class<?> entityClass();

  String variableName();

  Class<?> valueClass();

  boolean allowsUnassigned();

  default boolean isList() {
    return this instanceof AlnsListVariable;
  }
}
