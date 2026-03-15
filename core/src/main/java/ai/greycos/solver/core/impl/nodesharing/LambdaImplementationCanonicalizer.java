package ai.greycos.solver.core.impl.nodesharing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Canonicalizes lambda implementation methods so identical synthetic lambda bodies can be shared.
 */
final class LambdaImplementationCanonicalizer {

  private final String className;
  private final Map<String, String> syntheticImplementationFingerprintMap = new HashMap<>();

  LambdaImplementationCanonicalizer(String className, byte[] classBytecode) {
    this.className = className;
    buildSyntheticMethodFingerprintMap(classBytecode);
  }

  public LambdaKey buildKey(String invokedynamicDescriptor, Object[] bootstrapMethodArguments) {
    if (bootstrapMethodArguments.length < 3) {
      return null;
    }
    // Captured lambdas depend on instance state or runtime values. Replacing them with
    // class-level fields would break semantics, so we leave them untouched.
    if (Type.getArgumentTypes(invokedynamicDescriptor).length > 0) {
      return null;
    }

    Handle implementationMethodHandle = (Handle) bootstrapMethodArguments[1];
    Type implementationMethodType = (Type) bootstrapMethodArguments[2];
    Type returnType = Type.getReturnType(invokedynamicDescriptor);

    return new LambdaKey(
        returnType.getClassName(),
        canonicalizeImplementationMethod(implementationMethodHandle),
        implementationMethodType.getDescriptor(),
        java.util.List.of());
  }

  private String canonicalizeImplementationMethod(Handle methodHandle) {
    String rawImplementationMethod =
        methodHandle.getOwner() + "." + methodHandle.getName() + methodHandle.getDesc();
    if (!className.equals(methodHandle.getOwner())
        || !methodHandle.getName().startsWith("lambda$")) {
      return rawImplementationMethod;
    }
    return syntheticImplementationFingerprintMap.getOrDefault(
        rawImplementationMethod, rawImplementationMethod);
  }

  private void buildSyntheticMethodFingerprintMap(byte[] classBytecode) {
    new ClassReader(classBytecode)
        .accept(
            new org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                if (!name.startsWith("lambda$")) {
                  return null;
                }
                return new FingerprintingMethodVisitor(name, descriptor);
              }
            },
            ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
  }

  private final class FingerprintingMethodVisitor extends MethodVisitor {

    private final String methodName;
    private final String descriptor;
    private final StringBuilder fingerprintBuilder = new StringBuilder();
    private final Map<Label, Integer> labelIdMap = new IdentityHashMap<>();
    private int nextLabelId = 0;

    private FingerprintingMethodVisitor(String methodName, String descriptor) {
      super(Opcodes.ASM9);
      this.methodName = methodName;
      this.descriptor = descriptor;
    }

    @Override
    public void visitInsn(int opcode) {
      append("INSN", opcode);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
      append("INT", opcode, operand);
    }

    @Override
    public void visitVarInsn(int opcode, int varIndex) {
      append("VAR", opcode, varIndex);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
      append("TYPE", opcode, type);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
      append("FIELD", opcode, owner, name, descriptor);
    }

    @Override
    public void visitMethodInsn(
        int opcode, String owner, String name, String descriptor, boolean isInterface) {
      append("METHOD", opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitInvokeDynamicInsn(
        String name,
        String descriptor,
        Handle bootstrapMethodHandle,
        Object... bootstrapMethodArguments) {
      append(
          "INDY",
          name,
          descriptor,
          bootstrapMethodHandle.getOwner(),
          bootstrapMethodHandle.getName());
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
      append("JUMP", opcode, labelId(label));
    }

    @Override
    public void visitLdcInsn(Object value) {
      append("LDC", normalizeConstant(value));
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
      append("IINC", varIndex, increment);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
      append("TSWITCH", min, max, labelId(dflt), labelIds(labels));
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
      append("LSWITCH", labelId(dflt), java.util.Arrays.toString(keys), labelIds(labels));
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
      append("MULTIANEWARRAY", descriptor, numDimensions);
    }

    @Override
    public void visitEnd() {
      syntheticImplementationFingerprintMap.put(
          className + "." + methodName + descriptor,
          className + ".lambda-body:" + digest(fingerprintBuilder.toString()) + descriptor);
    }

    private int labelId(Label label) {
      return labelIdMap.computeIfAbsent(label, ignored -> nextLabelId++);
    }

    private String labelIds(Label[] labels) {
      int[] ids = new int[labels.length];
      for (int i = 0; i < labels.length; i++) {
        ids[i] = labelId(labels[i]);
      }
      return java.util.Arrays.toString(ids);
    }

    private void append(Object... parts) {
      for (Object part : parts) {
        fingerprintBuilder.append(part).append('|');
      }
      fingerprintBuilder.append('\n');
    }

    private Object normalizeConstant(Object value) {
      if (value instanceof Type type) {
        return "TYPE:" + type.getDescriptor();
      }
      return String.valueOf(value);
    }
  }

  private static String digest(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(Character.forDigit((b >>> 4) & 0xF, 16));
        hex.append(Character.forDigit(b & 0xF, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Missing SHA-256 support.", e);
    }
  }
}
