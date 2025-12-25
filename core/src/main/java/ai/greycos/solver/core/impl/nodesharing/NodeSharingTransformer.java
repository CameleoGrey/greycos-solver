package ai.greycos.solver.core.impl.nodesharing;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Transforms a ConstraintProvider class to enable automatic node sharing.
 *
 * <p>The transformation:
 *
 * <ol>
 *   <li>Analyzes class to find lambda expressions
 *   <li>Groups identical lambdas together
 *   <li>Adds static final fields for each lambda group
 *   <li>Keeps first occurrence of each lambda and stores it in a field
 *   <li>Replaces subsequent occurrences with field references
 * </ol>
 *
 * <p>This enables node sharing by ensuring identical lambdas use the same reference.
 */
public final class NodeSharingTransformer {

  private final Class<?> constraintProviderClass;

  public NodeSharingTransformer(Class<?> constraintProviderClass) {
    this.constraintProviderClass = constraintProviderClass;
  }

  /**
   * Transforms the ConstraintProvider class bytecode.
   *
   * @return transformed bytecode, or original if no shareable lambdas found
   */
  public byte[] transform() {
    // Read original class bytecode
    byte[] originalBytecode = readOriginalBytecode();

    // Analyze for lambdas
    ConstraintProviderAnalyzer analyzer = new ConstraintProviderAnalyzer(constraintProviderClass);
    LambdaAnalysis analysis = analyzer.analyze();

    // If no shareable lambdas, return original bytecode
    if (!analysis.hasShareableLambdas()) {
      return originalBytecode;
    }

    // Create deduplicator
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    // Transform bytecode
    return transformBytecode(originalBytecode, deduplicator);
  }

  /** Reads the original class bytecode from classpath. */
  private byte[] readOriginalBytecode() {
    String className = constraintProviderClass.getName().replace('.', '/');
    String classFileName = className + ".class";

    try (InputStream is =
        constraintProviderClass.getClassLoader().getResourceAsStream(classFileName)) {
      if (is == null) {
        throw new IllegalStateException(
            "Cannot find class file for " + constraintProviderClass.getName());
      }

      return is.readAllBytes();
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to read class file for " + constraintProviderClass.getName(), e);
    }
  }

  /** Transforms bytecode by adding fields and replacing lambda references. */
  private byte[] transformBytecode(byte[] originalBytecode, LambdaDeduplicator deduplicator) {

    String className = constraintProviderClass.getName().replace('.', '/');

    ClassReader reader = new ClassReader(originalBytecode);
    ClassWriter writer =
        new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

    // Chain visitors: field adding & lambda replacing -> writer
    // Track initialized lambdas globally across all methods
    Set<LambdaKey> initializedLambdas = new HashSet<>();
    ClassVisitor transformer =
        new NodeSharingClassVisitor(writer, className, deduplicator, initializedLambdas);

    // Visit and transform
    reader.accept(transformer, 0);

    return writer.toByteArray();
  }

  /**
   * Class visitor that adds static final fields for shared lambdas and replaces lambda creation
   * with field references.
   */
  private static class NodeSharingClassVisitor extends ClassVisitor {

    private final String className;
    private final LambdaDeduplicator deduplicator;
    private final Set<LambdaKey> initializedLambdas;

    public NodeSharingClassVisitor(
        ClassVisitor cv,
        String className,
        LambdaDeduplicator deduplicator,
        Set<LambdaKey> initializedLambdas) {
      super(Opcodes.ASM9, cv);
      this.className = className;
      this.deduplicator = deduplicator;
      this.initializedLambdas = initializedLambdas;
    }

    @Override
    public void visitEnd() {
      // Add static final fields for each lambda group
      for (var entry : deduplicator.getAnalysis().getShareableLambdas().entrySet()) {
        LambdaKey key = entry.getKey();
        String fieldName = deduplicator.getFieldName(key);
        String fieldDescriptor = deduplicator.getFieldDescriptor(key);

        if (fieldName != null && fieldDescriptor != null) {
          // Create field: private static final Predicate<String> $predicate1;
          var fv =
              cv.visitField(
                  Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                  fieldName,
                  fieldDescriptor,
                  null,
                  null);
          if (fv != null) {
            fv.visitEnd();
          }
        }
      }

      super.visitEnd();
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {

      MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

      if (mv != null && !"<clinit>".equals(name) && !"<init>".equals(name)) {
        // Wrap method visitor with lambda replacing visitor
        return new LambdaReplacingMethodVisitor(
            mv, className, name, descriptor, deduplicator, initializedLambdas);
      }

      return mv;
    }
  }

  /**
   * Method visitor that replaces lambda creation with field references.
   *
   * <p>The first occurrence of each lambda group is kept and stored in a static field. Subsequent
   * occurrences are replaced with GETSTATIC instructions.
   */
  private static class LambdaReplacingMethodVisitor extends MethodVisitor {

    private final String className;
    private final String methodName;
    private final LambdaDeduplicator deduplicator;
    private final Set<LambdaKey> initializedLambdas;

    public LambdaReplacingMethodVisitor(
        MethodVisitor mv,
        String className,
        String methodName,
        String descriptor,
        LambdaDeduplicator deduplicator,
        Set<LambdaKey> initializedLambdas) {
      super(Opcodes.ASM9, mv);
      this.className = className;
      this.methodName = methodName;
      this.deduplicator = deduplicator;
      this.initializedLambdas = initializedLambdas;
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
            String fieldDescriptor = deduplicator.getFieldDescriptor(key);
            if (fieldDescriptor != null) {
              // Check if we've already initialized this lambda
              if (initializedLambdas.contains(key)) {
                // Replace with field reference
                mv.visitFieldInsn(Opcodes.GETSTATIC, className, fieldName, fieldDescriptor);
                return;
              } else {
                // First occurrence: keep original and store in field
                // Generate: original_lambda; DUP; PUTSTATIC field
                initializedLambdas.add(key);
                super.visitInvokeDynamicInsn(
                    name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                mv.visitInsn(Opcodes.DUP);
                mv.visitFieldInsn(Opcodes.PUTSTATIC, className, fieldName, fieldDescriptor);
                return;
              }
            }
          }
        }
      }

      // Not a shareable lambda, keep original instruction
      super.visitInvokeDynamicInsn(
          name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
    }

    private boolean isLambdaMetafactory(Handle bootstrapMethodHandle) {
      return "java/lang/invoke/LambdaMetafactory".equals(bootstrapMethodHandle.getOwner())
          && ("metafactory".equals(bootstrapMethodHandle.getName())
              || "altMetafactory".equals(bootstrapMethodHandle.getName()));
    }

    private LambdaKey extractLambdaKey(
        Object[] bootstrapMethodArguments, String invokedynamicDescriptor) {
      if (bootstrapMethodArguments.length < 3) {
        return null;
      }

      Handle implementationMethodHandle = (Handle) bootstrapMethodArguments[1];
      Type implementationMethodType = (Type) bootstrapMethodArguments[2];

      // Extract functional interface class from invokedynamic descriptor
      Type returnType = Type.getReturnType(invokedynamicDescriptor);
      String functionalInterfaceClass = returnType.getClassName();

      // Extract captured arguments
      List<Object> capturedArgs = new ArrayList<>();
      for (int i = 3; i < bootstrapMethodArguments.length; i++) {
        Object arg = bootstrapMethodArguments[i];
        if (!(arg instanceof ConstantDynamic)) {
          capturedArgs.add(arg);
        }
      }

      // Note: We don't include implementation method name in LambdaKey because
      // Java compiler generates different synthetic method names for identical lambdas
      return new LambdaKey(
          functionalInterfaceClass, implementationMethodType.getDescriptor(), capturedArgs);
    }
  }
}
