package ai.greycos.solver.core.impl.nodesharing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * ASM visitor that finds lambda expressions in ConstraintProvider methods.
 *
 * <p>Scans for {@code invokedynamic} instructions with lambda metafactory bootstrap method,
 * capturing metadata needed to identify identical lambdas.
 */
public class LambdaFindingVisitor extends MethodVisitor {

  private static final String LAMBDA_METAFACTORY = "java/lang/invoke/LambdaMetafactory";
  private static final String METAFACTORY = "metafactory";
  private static final String ALT_METAFACTORY = "altMetafactory";

  private final String className;
  private final String methodName;
  private final List<LambdaInfo> lambdas = new ArrayList<>();

  public LambdaFindingVisitor(String className, String methodName) {
    super(Opcodes.ASM9);
    this.className = Objects.requireNonNull(className);
    this.methodName = Objects.requireNonNull(methodName);
  }

  @Override
  public void visitInvokeDynamicInsn(
      String name,
      String descriptor,
      Handle bootstrapMethodHandle,
      Object... bootstrapMethodArguments) {

    // Check if this is a lambda metafactory call
    if (isLambdaMetafactory(bootstrapMethodHandle)) {
      LambdaInfo lambdaInfo = extractLambdaInfo(name, descriptor, bootstrapMethodArguments);
      if (lambdaInfo != null) {
        lambdas.add(lambdaInfo);
      }
    }
  }

  private boolean isLambdaMetafactory(Handle bootstrapMethodHandle) {
    return LAMBDA_METAFACTORY.equals(bootstrapMethodHandle.getOwner())
        && (METAFACTORY.equals(bootstrapMethodHandle.getName())
            || ALT_METAFACTORY.equals(bootstrapMethodHandle.getName()));
  }

  private LambdaInfo extractLambdaInfo(
      String invokedynamicName, String invokedynamicDescriptor, Object[] bootstrapMethodArguments) {

    // Lambda metafactory arguments:
    // [0] - MethodType of functional interface
    // [1] - MethodHandle of implementation method
    // [2] - MethodType of implementation method
    // [3+] - Captured arguments (for altMetafactory)

    if (bootstrapMethodArguments.length < 3) {
      return null;
    }

    Handle implementationMethodHandle = (Handle) bootstrapMethodArguments[1];
    Type implementationMethodType = (Type) bootstrapMethodArguments[2];

    // Extract functional interface class from invokedynamic descriptor
    // The descriptor is something like "(Ljava/lang/String;)Z" for Predicate<String>
    // The return type of invokedynamic tells us the functional interface
    Type returnType = Type.getReturnType(invokedynamicDescriptor);
    String functionalInterfaceClass = returnType.getClassName();

    // Extract captured arguments
    List<Object> capturedArgs = new ArrayList<>();
    for (int i = 3; i < bootstrapMethodArguments.length; i++) {
      Object arg = bootstrapMethodArguments[i];
      if (!(arg instanceof ConstantDynamic)) {
        // Skip Condys for now - they're dynamic constants
        capturedArgs.add(arg);
      }
    }

    return new LambdaInfo(
        methodName,
        lambdas.size(),
        functionalInterfaceClass,
        getImplementationMethodName(implementationMethodHandle),
        implementationMethodType.getDescriptor(),
        capturedArgs);
  }

  private String getImplementationMethodName(Handle methodHandle) {
    // Handle format: owner.name.descriptor
    return methodHandle.getOwner() + "." + methodHandle.getName() + methodHandle.getDesc();
  }

  public List<LambdaInfo> getLambdas() {
    return List.copyOf(lambdas);
  }
}
