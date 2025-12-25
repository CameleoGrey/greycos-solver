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
 *   <li>Implementation method type signature
 *   <li>Captured arguments (if any)
 * </ul>
 *
 * <p>Note: We don't include the implementation method name in the key because the Java compiler
 * generates different synthetic method names for identical lambdas (e.g.,
 * lambda$defineConstraints$0 and lambda$defineConstraints$1). Two lambdas with identical bytecode
 * but different synthetic method names are still functionally equivalent and should be shared.
 */
public final class LambdaKey {

  private final String functionalInterfaceType;
  private final String implementationMethodType;
  private final List<Object> capturedArguments;

  public LambdaKey(
      String functionalInterfaceType,
      String implementationMethodType,
      List<Object> capturedArguments) {
    this.functionalInterfaceType = Objects.requireNonNull(functionalInterfaceType);
    this.implementationMethodType = Objects.requireNonNull(implementationMethodType);
    this.capturedArguments = List.copyOf(capturedArguments);
  }

  public String getFunctionalInterfaceType() {
    return functionalInterfaceType;
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
        && Objects.equals(implementationMethodType, lambdaKey.implementationMethodType)
        && Objects.equals(capturedArguments, lambdaKey.capturedArguments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(functionalInterfaceType, implementationMethodType, capturedArguments);
  }

  @Override
  public String toString() {
    return "LambdaKey{"
        + "functionalInterfaceType='"
        + functionalInterfaceType
        + '\''
        + ", implementationMethodType='"
        + implementationMethodType
        + '\''
        + ", capturedArguments="
        + capturedArguments
        + '}';
  }
}
