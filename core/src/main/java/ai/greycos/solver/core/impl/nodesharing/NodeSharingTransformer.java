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
 * Transforms ConstraintProvider bytecode to enable automatic lambda node sharing.
 *
 * <p>Why: Identical lambdas should be deduplicated to reduce memory and improve performance. How:
 * Adds static fields for shared lambdas and replaces lambda creation with field references. What:
 * Orchestrates bytecode transformation via ASM visitors.
 */
public final class NodeSharingTransformer {

  private final Class<?> constraintProviderClass;

  public NodeSharingTransformer(Class<?> constraintProviderClass) {
    this.constraintProviderClass = constraintProviderClass;
  }

  public byte[] transform() {
    byte[] originalBytecode = readOriginalBytecode();

    ConstraintProviderAnalyzer analyzer = new ConstraintProviderAnalyzer(constraintProviderClass);
    LambdaAnalysis analysis = analyzer.analyze();

    if (!analysis.hasShareableLambdas()) {
      return originalBytecode;
    }

    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    return transformBytecode(originalBytecode, deduplicator);
  }

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

  private byte[] transformBytecode(byte[] originalBytecode, LambdaDeduplicator deduplicator) {

    String className = constraintProviderClass.getName().replace('.', '/');

    ClassReader reader = new ClassReader(originalBytecode);
    ClassWriter writer =
        new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

    List<LambdaInitialization> lambdaInitializations = new ArrayList<>();
    ClassVisitor transformer =
        new NodeSharingClassVisitor(writer, className, deduplicator, lambdaInitializations);

    reader.accept(transformer, 0);

    if (!lambdaInitializations.isEmpty()) {
      addStaticInitializer(writer, className, lambdaInitializations, deduplicator);
    }

    return writer.toByteArray();
  }

  private void addStaticInitializer(
      ClassWriter writer,
      String className,
      List<LambdaInitialization> lambdaInitializations,
      LambdaDeduplicator deduplicator) {

    MethodVisitor mv = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
    mv.visitCode();

    for (LambdaInitialization init : lambdaInitializations) {
      mv.visitInvokeDynamicInsn(
          init.name(),
          init.descriptor(),
          init.bootstrapMethodHandle(),
          init.bootstrapMethodArguments());
      mv.visitFieldInsn(
          Opcodes.PUTSTATIC,
          className,
          deduplicator.getFieldName(init.key()),
          deduplicator.getFieldDescriptor(init.key()));
    }

    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private record LambdaInitialization(
      LambdaKey key,
      String name,
      String descriptor,
      Handle bootstrapMethodHandle,
      Object[] bootstrapMethodArguments) {}

  private static class NodeSharingClassVisitor extends ClassVisitor {

    private final String className;
    private final LambdaDeduplicator deduplicator;
    private final List<LambdaInitialization> lambdaInitializations;
    private final Set<LambdaKey> seenLambdas;

    public NodeSharingClassVisitor(
        ClassVisitor cv,
        String className,
        LambdaDeduplicator deduplicator,
        List<LambdaInitialization> lambdaInitializations) {
      super(Opcodes.ASM9, cv);
      this.className = className;
      this.deduplicator = deduplicator;
      this.lambdaInitializations = lambdaInitializations;
      this.seenLambdas = new HashSet<>();
    }

    @Override
    public void visitEnd() {
      for (var entry : deduplicator.getAnalysis().getShareableLambdas().entrySet()) {
        LambdaKey key = entry.getKey();
        String fieldName = deduplicator.getFieldName(key);
        String fieldDescriptor = deduplicator.getFieldDescriptor(key);

        if (fieldName != null && fieldDescriptor != null) {
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
        return new LambdaReplacingMethodVisitor(
            mv, className, name, descriptor, deduplicator, seenLambdas, lambdaInitializations);
      }

      return mv;
    }
  }

  private static class LambdaReplacingMethodVisitor extends MethodVisitor {

    private final String className;
    private final String methodName;
    private final LambdaDeduplicator deduplicator;
    private final Set<LambdaKey> seenLambdas;
    private final List<LambdaInitialization> lambdaInitializations;

    public LambdaReplacingMethodVisitor(
        MethodVisitor mv,
        String className,
        String methodName,
        String descriptor,
        LambdaDeduplicator deduplicator,
        Set<LambdaKey> seenLambdas,
        List<LambdaInitialization> lambdaInitializations) {
      super(Opcodes.ASM9, mv);
      this.className = className;
      this.methodName = methodName;
      this.deduplicator = deduplicator;
      this.seenLambdas = seenLambdas;
      this.lambdaInitializations = lambdaInitializations;
    }

    @Override
    public void visitInvokeDynamicInsn(
        String name,
        String descriptor,
        Handle bootstrapMethodHandle,
        Object... bootstrapMethodArguments) {

      if (isLambdaMetafactory(bootstrapMethodHandle)) {
        LambdaKey key = extractLambdaKey(bootstrapMethodArguments, descriptor);
        if (key != null) {
          String fieldName = deduplicator.getFieldName(key);
          if (fieldName != null) {
            String fieldDescriptor = deduplicator.getFieldDescriptor(key);
            if (fieldDescriptor != null) {
              if (seenLambdas.add(key)) {
                lambdaInitializations.add(
                    new LambdaInitialization(
                        key, name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments));
              }
              mv.visitFieldInsn(Opcodes.GETSTATIC, className, fieldName, fieldDescriptor);
              return;
            }
          }
        }
      }

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

      Type returnType = Type.getReturnType(invokedynamicDescriptor);
      String functionalInterfaceClass = returnType.getClassName();

      List<Object> capturedArgs = new ArrayList<>();
      for (int i = 3; i < bootstrapMethodArguments.length; i++) {
        Object arg = bootstrapMethodArguments[i];
        if (!(arg instanceof ConstantDynamic)) {
          capturedArgs.add(arg);
        }
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
  }
}
