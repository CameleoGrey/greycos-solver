package greycos.solver.core.impl.nodesharing;

import java.util.List;
import java.util.Objects;

/**
 * Metadata about a lambda expression found in ConstraintProvider bytecode.
 *
 * <p>Why: Need to capture lambda properties to identify which can be deduplicated. How: Extracts
 * functional interface, implementation method, and captured arguments. What: Provides data for
 * grouping identical lambdas during analysis.
 */
public final class LambdaInfo {

  private final String methodName;
  private final int instructionOffset;
  private final LambdaKey key;

  public LambdaInfo(
      String methodName,
      int instructionOffset,
      String functionalInterfaceType,
      String implementationMethod,
      String implementationMethodType,
      List<Object> capturedArguments) {
    this(
        methodName,
        instructionOffset,
        new LambdaKey(
            functionalInterfaceType,
            implementationMethod,
            implementationMethodType,
            capturedArguments));
  }

  public LambdaInfo(String methodName, int instructionOffset, LambdaKey key) {
    this.methodName = Objects.requireNonNull(methodName);
    this.instructionOffset = instructionOffset;
    this.key = Objects.requireNonNull(key);
  }

  public String getMethodName() {
    return methodName;
  }

  public int getInstructionOffset() {
    return instructionOffset;
  }

  public String getFunctionalInterfaceType() {
    return key.getFunctionalInterfaceType();
  }

  public String getImplementationMethod() {
    return key.getImplementationMethod();
  }

  public String getImplementationMethodType() {
    return key.getImplementationMethodType();
  }

  public List<Object> getCapturedArguments() {
    return key.getCapturedArguments();
  }

  public LambdaKey getKey() {
    return key;
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
        && key.equals(lambdaInfo.key);
  }

  @Override
  public int hashCode() {
    return Objects.hash(methodName, instructionOffset, key);
  }

  @Override
  public String toString() {
    return "LambdaInfo{"
        + "methodName='"
        + methodName
        + '\''
        + ", instructionOffset="
        + instructionOffset
        + ", key="
        + key
        + '}';
  }
}
