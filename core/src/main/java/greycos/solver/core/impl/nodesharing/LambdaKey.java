package greycos.solver.core.impl.nodesharing;

import java.util.List;
import java.util.Objects;

/** Immutable identity of a lambda call site, including its complete linkage contract. */
public final class LambdaKey {

  private final String functionalInterfaceType;
  private final String implementationMethod;
  private final String implementationMethodType;
  private final List<Object> capturedArguments;
  private final Object identity;

  public LambdaKey(
      String functionalInterfaceType,
      String implementationMethod,
      String implementationMethodType,
      List<Object> capturedArguments) {
    this.functionalInterfaceType = Objects.requireNonNull(functionalInterfaceType);
    this.implementationMethod = Objects.requireNonNull(implementationMethod);
    this.implementationMethodType = Objects.requireNonNull(implementationMethodType);
    this.capturedArguments = List.copyOf(capturedArguments);
    this.identity =
        List.of(
            this.functionalInterfaceType,
            this.implementationMethod,
            this.implementationMethodType,
            this.capturedArguments);
  }

  LambdaKey(
      String functionalInterfaceType,
      String implementationMethod,
      String implementationMethodType,
      Object callSiteIdentity) {
    this.functionalInterfaceType = Objects.requireNonNull(functionalInterfaceType);
    this.implementationMethod = Objects.requireNonNull(implementationMethod);
    this.implementationMethodType = Objects.requireNonNull(implementationMethodType);
    this.capturedArguments = List.of();
    this.identity = Objects.requireNonNull(callSiteIdentity);
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
    return identity.equals(lambdaKey.identity);
  }

  @Override
  public int hashCode() {
    return identity.hashCode();
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
