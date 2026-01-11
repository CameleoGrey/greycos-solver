package ai.greycos.solver.core.impl.nodesharing;

import java.util.List;
import java.util.Objects;

/**
 * Key for identifying functionally equivalent lambdas to enable deduplication.
 *
 * <p>Why: Need to determine which lambdas are identical and can be shared. How: Combines functional
 * interface type, implementation method, and captured arguments. What: Used as map key to group
 * identical lambdas for node sharing.
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
    return Objects.hash(
        functionalInterfaceType, implementationMethod, implementationMethodType, capturedArguments);
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
