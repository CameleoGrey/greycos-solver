package ai.greycos.solver.core.impl.nodesharing;

import java.util.List;
import java.util.Objects;

/**
 * Metadata about a lambda expression found in ConstraintProvider bytecode.
 *
 * <p>Why: Need to capture lambda properties to identify which can be deduplicated.
 * How: Extracts functional interface, implementation method, and captured arguments.
 * What: Provides data for grouping identical lambdas during analysis.
 */
public final class LambdaInfo {

  private final String methodName;
  private final int instructionOffset;
  private final String functionalInterfaceType;
  private final String implementationMethod;
  private final String implementationMethodType;
  private final List<Object> capturedArguments;

  public LambdaInfo(
      String methodName,
      int instructionOffset,
      String functionalInterfaceType,
      String implementationMethod,
      String implementationMethodType,
      List<Object> capturedArguments) {
    this.methodName = Objects.requireNonNull(methodName);
    this.instructionOffset = instructionOffset;
    this.functionalInterfaceType = Objects.requireNonNull(functionalInterfaceType);
    this.implementationMethod = Objects.requireNonNull(implementationMethod);
    this.implementationMethodType = Objects.requireNonNull(implementationMethodType);
    this.capturedArguments = List.copyOf(capturedArguments);
  }

  public String getMethodName() {
    return methodName;
  }

  public int getInstructionOffset() {
    return instructionOffset;
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

  public LambdaKey getKey() {
    return new LambdaKey(
        functionalInterfaceType, implementationMethod, implementationMethodType, capturedArguments);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LambdaInfo lambdaInfo = (LambdaInfo) o;
    return instructionOffset == lambdaInfo.instructionOffset
        && Objects.equals(methodName, lambdaInfo.methodName)
        && Objects.equals(functionalInterfaceType, lambdaInfo.functionalInterfaceType)
        && Objects.equals(implementationMethod, lambdaInfo.implementationMethod)
        && Objects.equals(implementationMethodType, lambdaInfo.implementationMethodType)
        && Objects.equals(capturedArguments, lambdaInfo.capturedArguments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        methodName,
        instructionOffset,
        functionalInterfaceType,
        implementationMethod,
        implementationMethodType,
        capturedArguments);
  }

  @Override
  public String toString() {
    return "LambdaInfo{"
        + "methodName='"
        + methodName
        + '\''
        + ", instructionOffset="
        + instructionOffset
        + ", functionalInterfaceType='"
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
