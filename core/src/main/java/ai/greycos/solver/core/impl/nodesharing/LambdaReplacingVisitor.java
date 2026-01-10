package ai.greycos.solver.core.impl.nodesharing;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * ASM visitor that replaces lambda creation with field references.
 *
 * <p>Replaces {@code invokedynamic} instructions for lambdas that have been identified as shareable
 * with {@code GETSTATIC} instructions that reference the shared field.
 */
public class LambdaReplacingVisitor extends MethodVisitor {

  private static final String LAMBDA_METAFACTORY = "java/lang/invoke/LambdaMetafactory";
  private static final String METAFACTORY = "metafactory";
  private static final String ALT_METAFACTORY = "altMetafactory";

  private final String className;
  private final String methodName;
  private final String invokedynamicDescriptor;
  private final LambdaDeduplicator deduplicator;
  private final Map<Integer, LambdaInfo> lambdaAtOffset;

  private boolean skipNextInvokeDynamic = false;
  private String lastFieldName;

  public LambdaReplacingVisitor(
      MethodVisitor mv,
      String className,
      String methodName,
      String invokedynamicDescriptor,
      LambdaDeduplicator deduplicator,
      Map<Integer, LambdaInfo> lambdaAtOffset) {
    super(Opcodes.ASM9, mv);
    this.className = Objects.requireNonNull(className);
    this.methodName = Objects.requireNonNull(methodName);
    this.invokedynamicDescriptor = Objects.requireNonNull(invokedynamicDescriptor);
    this.deduplicator = Objects.requireNonNull(deduplicator);
    this.lambdaAtOffset = new HashMap<>(lambdaAtOffset);
  }

  @Override
  public void visitInvokeDynamicInsn(
      String name,
      String descriptor,
      Handle bootstrapMethodHandle,
      Object... bootstrapMethodArguments) {

    // Check if this is a lambda metafactory call
    if (isLambdaMetafactory(bootstrapMethodHandle)) {
      LambdaKey key = extractLambdaKey(bootstrapMethodArguments, descriptor);
      if (key != null) {
        String fieldName = deduplicator.getFieldName(key);
        if (fieldName != null) {
          // Replace lambda creation with field reference
          replaceLambdaWithFieldReference(fieldName, descriptor);
          return;
        }
      }
    }

    // Not a shareable lambda, keep original instruction
    super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
  }

  private boolean isLambdaMetafactory(Handle bootstrapMethodHandle) {
    return LAMBDA_METAFACTORY.equals(bootstrapMethodHandle.getOwner())
        && (METAFACTORY.equals(bootstrapMethodHandle.getName())
            || ALT_METAFACTORY.equals(bootstrapMethodHandle.getName()));
  }

  private LambdaKey extractLambdaKey(
      Object[] bootstrapMethodArguments, String invokedynamicDescriptor) {
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
    java.util.List<Object> capturedArgs = new java.util.ArrayList<>();
    for (int i = 3; i < bootstrapMethodArguments.length; i++) {
      Object arg = bootstrapMethodArguments[i];
      if (arg instanceof ConstantDynamic) {
        // Skip Condys
        continue;
      }
      capturedArgs.add(arg);
    }

    String implementationMethod = getImplementationMethodName(implementationMethodHandle);

    return new LambdaKey(
        functionalInterfaceClass,
        implementationMethod,
        implementationMethodType.getDescriptor(),
        capturedArgs);
  }

  private String getImplementationMethodName(Handle methodHandle) {
    return methodHandle.getOwner() + "." + methodHandle.getName() + methodHandle.getDesc();
  }

  /**
   * Replaces lambda creation with static field reference.
   *
   * <p>Transforms:
   *
   * <pre>
   *   invokedynamic "apply" ()Ljava/util/function/Predicate;
   * </pre>
   *
   * to:
   *
   * <pre>
   *   GETSTATIC className/fieldName : Ljava/util/function/Predicate;
   * </pre>
   */
  private void replaceLambdaWithFieldReference(String fieldName, String descriptor) {
    // Get field descriptor from deduplicator
    String fieldDescriptor = null;
    for (var entry : deduplicator.getAnalysis().getShareableLambdas().entrySet()) {
      if (deduplicator.getFieldName(entry.getKey()).equals(fieldName)) {
        fieldDescriptor = deduplicator.getFieldDescriptor(entry.getKey());
        break;
      }
    }

    if (fieldDescriptor == null) {
      // Fallback: generate from descriptor
      Type returnType = Type.getReturnType(descriptor);
      fieldDescriptor = returnType.getDescriptor();
    }

    // Replace with GETSTATIC instruction
    mv.visitFieldInsn(Opcodes.GETSTATIC, className, fieldName, fieldDescriptor);
  }
}
