package ai.greycos.solver.core.impl.nodesharing;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Debug test to verify invokedynamic detection works. */
class LambdaDetectionDebugTest {

  @Test
  void debugLambdaDetection() throws IOException {
    System.out.println("=== Debugging Lambda Detection ===");

    String className = TestConstraintProvider.class.getName().replace('.', '/');
    String classFileName = className + ".class";

    try (InputStream is =
        TestConstraintProvider.class.getClassLoader().getResourceAsStream(classFileName)) {
      if (is == null) {
        throw new IllegalStateException(
            "Cannot find class file for " + TestConstraintProvider.class.getName());
      }

      ClassReader reader = new ClassReader(is);

      reader.accept(
          new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
              System.out.println("Visiting method: " + name + descriptor);
              if ((access & Opcodes.ACC_SYNTHETIC) != 0 || (access & Opcodes.ACC_BRIDGE) != 0) {
                System.out.println("  -> Skipping synthetic/bridge method");
                return null;
              }

              return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitInvokeDynamicInsn(
                    String name,
                    String descriptor,
                    Handle bootstrapMethodHandle,
                    Object... bootstrapMethodArguments) {
                  System.out.println("  -> Found invokedynamic: " + name + descriptor);
                  System.out.println(
                      "     Bootstrap: "
                          + bootstrapMethodHandle.getOwner()
                          + "."
                          + bootstrapMethodHandle.getName());
                  System.out.println(
                      "     Is LambdaMetafactory: "
                          + "java/lang/invoke/LambdaMetafactory"
                              .equals(bootstrapMethodHandle.getOwner()));
                  super.visitInvokeDynamicInsn(
                      name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                }
              };
            }
          },
          0); // Use 0 flags to visit everything
    }
  }
}
