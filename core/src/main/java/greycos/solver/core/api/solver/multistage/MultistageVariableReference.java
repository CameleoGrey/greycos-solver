package greycos.solver.core.api.solver.multistage;

/**
 * An immutable declaration of a genuine planning variable and its permitted entity scope.
 * References contain no working objects and may be shared between providers and threads. The
 * enclosing cross-variable selector must declare every reference used by its stages.
 *
 * <p>The entity class may declare or inherit the variable. It limits the entities a stage may
 * change through this reference, including when the variable is declared on a superclass. The value
 * class must match the declared variable type, or the declared element type for a list variable.
 * Integer planning variables therefore use {@code Integer.class}.
 *
 * @param <Entity_> the configured entity scope, or list owner scope
 * @param <Value_> the basic variable type or list element type
 */
public sealed interface MultistageVariableReference<Entity_, Value_>
    permits BasicVariableReference, ListVariableReference {

  Class<Entity_> entityClass();

  String variableName();

  Class<Value_> valueClass();
}
