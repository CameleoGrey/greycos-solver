package ai.greycos.solver.core.impl.nodesharing;

import java.util.List;
import java.util.Objects;

/**
 * Key for identifying functionally equivalent lambdas.
 *
 * <p>Lambdas with same key can be shared in constraint stream network. The key is based on:
 *
 * <ul>
 *   <li>Functional interface type (e.g., Predicate, Function)
 *   <li>Implementation method (owner.name.descriptor)
 *   <li>Implementation method type signature
 *   <li>Captured arguments (if any)
 * </ul>
 *
 * <p>The implementation method is included to distinguish between lambdas that have the same type
 * signature but different implementations. For example, two lambdas with signature (Vehicle, Integer)
 * -> long could have different implementations like (v,d) -> d - v.getCapacity() vs (v,d) ->
 * Math.max(0,d).
 */
public final class LambdaKey {

  private final String functionalInterfaceType;
  private final String implementationMethod;
  private final String implementationMethodType;
  private final List<Object> capturedArguments;

  public LambdaKey(
      String functionalInterfaceType,
      String implementationMethod,
      String implementationMethodType,
      List<Object> capturedArguments) {
    this.functionalInterfaceType = Objects.requireNonNull(functionalInterfaceType);
    this.implementationMethod = Objects.requireNonNull(implementationMethod);
    this.implementationMethodType = Objects.requireNonNull(implementationMethodType);
    this.capturedArguments = List.copyOf(capturedArguments);
  }

  public String getFunctionalInterfaceType() {
    return functionalInterfaceType;
  }

  public String getImplementationMethod() {
    return implementationMethod;
  }

  public String getImplementationMethodType() {
    return implementationMethodType;
  }

  public List<Object> getCapturedArguments() {
    return capturedArguments;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LambdaKey lambdaKey = (LambdaKey) o;
    return Objects.equals(functionalInterfaceType, lambdaKey.functionalInterfaceType)
        && Objects.equals(implementationMethod, lambdaKey.implementationMethod)
        && Objects.equals(implementationMethodType, lambdaKey.implementationMethodType)
        && Objects.equals(capturedArguments, lambdaKey.capturedArguments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(functionalInterfaceType, implementationMethod, implementationMethodType, capturedArguments);
  }

  @Override
  public String toString() {
    return "LambdaKey{"
        + "functionalInterfaceType='"
        + functionalInterfaceType
        + '\''
        + ", implementationMethod='"
        + implementationMethod
        + '\''
        + ", implementationMethodType='"
        + implementationMethodType
        + '\''
        + ", capturedArguments="
        + capturedArguments
        + '}';
  }
}
