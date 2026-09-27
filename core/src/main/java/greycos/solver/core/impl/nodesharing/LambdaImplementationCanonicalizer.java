package greycos.solver.core.impl.nodesharing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Compares the executable instructions and linkage contracts of stateless lambdas. */
final class LambdaImplementationCanonicalizer {

  private static final String LOOKUP = "Ljava/lang/invoke/MethodHandles$Lookup;";
  private static final String STRING = "Ljava/lang/String;";
  private static final String METHOD_TYPE = "Ljava/lang/invoke/MethodType;";
  private static final String CALL_SITE = "Ljava/lang/invoke/CallSite;";
  private static final Handle METAFACTORY =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          "java/lang/invoke/LambdaMetafactory",
          "metafactory",
          "("
              + LOOKUP
              + STRING
              + METHOD_TYPE
              + METHOD_TYPE
              + "Ljava/lang/invoke/MethodHandle;"
              + METHOD_TYPE
              + ")"
              + CALL_SITE,
          false);
  private static final Handle ALT_METAFACTORY =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          "java/lang/invoke/LambdaMetafactory",
          "altMetafactory",
          "(" + LOOKUP + STRING + METHOD_TYPE + "[Ljava/lang/Object;)" + CALL_SITE,
          false);
  private static final Handle MAKE_CONCAT =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          "java/lang/invoke/StringConcatFactory",
          "makeConcat",
          "(" + LOOKUP + STRING + METHOD_TYPE + ")" + CALL_SITE,
          false);
  private static final Handle MAKE_CONCAT_WITH_CONSTANTS =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          "java/lang/invoke/StringConcatFactory",
          "makeConcatWithConstants",
          "(" + LOOKUP + STRING + METHOD_TYPE + STRING + "[Ljava/lang/Object;)" + CALL_SITE,
          false);

  private final String className;
  private final StackObservationAnalyzer stackObservationAnalyzer;
  private final Map<String, MethodBody> syntheticMethodBodies = new HashMap<>();

  LambdaImplementationCanonicalizer(String className, byte[] classBytecode) {
    this(className, classBytecode, null);
  }

  LambdaImplementationCanonicalizer(Class<?> providerClass, byte[] classBytecode) {
    this(
        Type.getInternalName(providerClass),
        classBytecode,
        new StackObservationAnalyzer(providerClass, classBytecode));
  }

  private LambdaImplementationCanonicalizer(
      String className, byte[] classBytecode, StackObservationAnalyzer stackObservationAnalyzer) {
    this.className = className;
    this.stackObservationAnalyzer = stackObservationAnalyzer;
    new ClassReader(classBytecode)
        .accept(
            new ClassVisitor(Opcodes.ASM9) {
              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                int requiredAccess = Opcodes.ACC_SYNTHETIC | Opcodes.ACC_STATIC;
                if (!name.startsWith("lambda$")
                    || (access & requiredAccess) != requiredAccess
                    || (access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_BRIDGE))
                        != 0) {
                  return null;
                }
                return new BodyVisitor(access, name, descriptor);
              }
            },
            ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
  }

  /** Returns null for call sites whose creation or interface contract must remain unchanged. */
  public LambdaKey buildKey(String name, String descriptor, Handle bootstrap, Object[] arguments) {
    // altMetafactory carries serialization, marker-interface and bridge contracts. Leave it intact.
    if (!METAFACTORY.equals(bootstrap)
        || Type.getArgumentTypes(descriptor).length != 0
        || Type.getReturnType(descriptor).getSort() != Type.OBJECT
        || arguments.length != 3
        || !(arguments[0] instanceof Type samType)
        || samType.getSort() != Type.METHOD
        || !(arguments[1] instanceof Handle implementation)
        || !(arguments[2] instanceof Type instantiatedType)
        || instantiatedType.getSort() != Type.METHOD) {
      return null;
    }
    Object implementationIdentity = implementation;
    if (implementation.getTag() == Opcodes.H_INVOKESTATIC
        && className.equals(implementation.getOwner())) {
      MethodBody body =
          syntheticMethodBodies.get(implementation.getName() + implementation.getDesc());
      if (body != null
          && (stackObservationAnalyzer == null
              || stackObservationAnalyzer
                  .inspectImplementation(implementation)
                  .permitsStructuralIdentity())) {
        implementationIdentity =
            new ImplementationBody(
                implementation.getTag(),
                implementation.getOwner(),
                implementation.getDesc(),
                implementation.isInterface(),
                body);
      }
    }
    return new LambdaKey(
        Type.getReturnType(descriptor).getClassName(),
        implementation.getOwner() + "." + implementation.getName() + implementation.getDesc(),
        instantiatedType.getDescriptor(),
        new CallSite(
            name, descriptor, bootstrap, samType, implementationIdentity, instantiatedType));
  }

  private record CallSite(
      String name,
      String descriptor,
      Handle bootstrap,
      Type samType,
      Object implementation,
      Type instantiatedType) {}

  private record ImplementationBody(
      int tag, String owner, String descriptor, boolean isInterface, MethodBody body) {}

  private record MethodBody(int access, String descriptor, List<Instruction> instructions) {}

  private record Instruction(String kind, List<Object> operands) {}

  private record Constant(String kind, Object value) {}

  static boolean inspectsStack(String owner, String name, String descriptor) {
    if (owner.equals("java/lang/StackWalker") || owner.startsWith("java/lang/StackWalker$")) {
      return true;
    }
    // Match inherited Throwable methods by their signatures, without resolving application types.
    // Otherwise an invocation whose owner is a Throwable subclass could bypass the check.
    if (name.equals("getStackTrace") && descriptor.equals("()[Ljava/lang/StackTraceElement;")) {
      return true;
    }
    if (name.equals("fillInStackTrace") && descriptor.equals("()Ljava/lang/Throwable;")) {
      return true;
    }
    if (name.equals("printStackTrace")
        && (descriptor.equals("()V")
            || descriptor.equals("(Ljava/io/PrintStream;)V")
            || descriptor.equals("(Ljava/io/PrintWriter;)V"))) {
      return true;
    }
    // Static Thread methods can also be referenced through a subclass owner.
    return (name.equals("getAllStackTraces") && descriptor.equals("()Ljava/util/Map;"))
        || (name.equals("dumpStack") && descriptor.equals("()V"));
  }

  private final class BodyVisitor extends MethodVisitor {

    private final int access;
    private final String name;
    private final String descriptor;
    private final List<Instruction> instructions = new ArrayList<>();
    private final Map<Label, Integer> labels = new IdentityHashMap<>();
    private boolean supported = true;

    private BodyVisitor(int access, String name, String descriptor) {
      super(Opcodes.ASM9);
      this.access = access;
      this.name = name;
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
      supported &= opcode != Opcodes.RET;
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
      // Equal instructions can observe different synthetic implementation names in their stacks.
      supported &= !inspectsStack(owner, name, descriptor);
      append("METHOD", opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitInvokeDynamicInsn(
        String name, String descriptor, Handle bootstrap, Object... arguments) {
      // An arbitrary bootstrap can link identical instructions to different per-call-site state.
      supported &=
          METAFACTORY.equals(bootstrap)
              || ALT_METAFACTORY.equals(bootstrap)
              || MAKE_CONCAT.equals(bootstrap)
              || MAKE_CONCAT_WITH_CONSTANTS.equals(bootstrap);
      List<Constant> constants = new ArrayList<>(arguments.length);
      for (Object argument : arguments) {
        constants.add(constant(argument));
      }
      append("INDY", name, descriptor, bootstrap, List.copyOf(constants));
    }

    @Override
    public void visitLabel(Label label) {
      append("LABEL", labelId(label));
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
      supported &= opcode != Opcodes.JSR;
      append("JUMP", opcode, labelId(label));
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
      // The order of exception table entries is significant. Empty type represents catch-all.
      append("CATCH", labelId(start), labelId(end), labelId(handler), type == null ? "" : type);
    }

    @Override
    public void visitLdcInsn(Object value) {
      append("LDC", constant(value));
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
      append("IINC", varIndex, increment);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... targets) {
      append("TSWITCH", min, max, labelId(dflt), labelIds(targets));
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] targets) {
      List<Integer> keyList = new ArrayList<>(keys.length);
      for (int key : keys) {
        keyList.add(key);
      }
      append("LSWITCH", labelId(dflt), List.copyOf(keyList), labelIds(targets));
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int dimensions) {
      append("MULTIANEWARRAY", descriptor, dimensions);
    }

    @Override
    public void visitEnd() {
      if (supported) {
        syntheticMethodBodies.put(
            name + descriptor, new MethodBody(access, descriptor, List.copyOf(instructions)));
      }
    }

    private int labelId(Label label) {
      return labels.computeIfAbsent(label, ignored -> labels.size());
    }

    private List<Integer> labelIds(Label[] targets) {
      List<Integer> ids = new ArrayList<>(targets.length);
      for (Label target : targets) {
        ids.add(labelId(target));
      }
      return List.copyOf(ids);
    }

    private void append(String kind, Object... operands) {
      instructions.add(new Instruction(kind, List.of(operands)));
    }

    private Constant constant(Object value) {
      if (value instanceof String string) {
        return new Constant("STRING", string);
      } else if (value instanceof Integer integer) {
        return new Constant("INT", integer);
      } else if (value instanceof Long number) {
        return new Constant("LONG", number);
      } else if (value instanceof Float number) {
        return new Constant("FLOAT", Float.floatToRawIntBits(number));
      } else if (value instanceof Double number) {
        return new Constant("DOUBLE", Double.doubleToRawLongBits(number));
      } else if (value instanceof Type type) {
        return new Constant("TYPE", type.getDescriptor());
      } else if (value instanceof Handle handle) {
        supported &= !inspectsStack(handle.getOwner(), handle.getName(), handle.getDesc());
        return new Constant("HANDLE", handle);
      }
      // Includes ConstantDynamic: bootstrap resolution may have observable per-constant state.
      supported = false;
      return new Constant("UNSUPPORTED", "");
    }
  }
}
