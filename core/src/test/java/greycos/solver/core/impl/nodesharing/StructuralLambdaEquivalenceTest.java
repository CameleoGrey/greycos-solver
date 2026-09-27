package greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class StructuralLambdaEquivalenceTest {

  private static final String OWNER = "example/StructuralProvider";
  private static final String BODY_DESCRIPTOR = "(Ljava/lang/Object;)Ljava/lang/Object;";
  private static final String FACTORY_DESCRIPTOR = "()Ljava/util/function/Function;";
  private static final Type METHOD_TYPE = Type.getMethodType(BODY_DESCRIPTOR);
  private static final Handle METAFACTORY =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          "java/lang/invoke/LambdaMetafactory",
          "metafactory",
          "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
              + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
              + "Ljava/lang/invoke/CallSite;",
          false);
  private static final Handle ALT_METAFACTORY =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          METAFACTORY.getOwner(),
          "altMetafactory",
          "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
              + "[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
          false);
  private static final Handle CONCAT =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          "java/lang/invoke/StringConcatFactory",
          "makeConcatWithConstants",
          "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
              + "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
          false);
  private static final Handle CUSTOM_BOOTSTRAP =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          "example/Bootstrap",
          "link",
          "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)"
              + "Ljava/lang/invoke/CallSite;",
          false);

  @Test
  void identicalBodiesShareDespiteDifferentSyntheticNames() {
    List<LambdaKey> keys = keys(returnConstant("same"), returnConstant("same"));
    assertThat(keys.get(0)).isEqualTo(keys.get(1));
    assertThat(keys.get(0).hashCode()).isEqualTo(keys.get(1).hashCode());
  }

  @Test
  void classConstantCannotCollideWithItsTextRepresentation() {
    assertDifferent(
        returnConstant(Type.getType(String.class)), returnConstant("TYPE:Ljava/lang/String;"));
  }

  @Test
  void delimiterBearingStringsRetainTheirExactContents() {
    assertDifferent(returnConstant("text|\nINSN|176|\n"), returnConstant("text"));
  }

  @Test
  void floatingPointConstantsRetainRawNanBits() {
    assertDifferent(returnFloatBits(0x7fc00001), returnFloatBits(0x7fc00002));
  }

  @Test
  void concatenationRecipesArePartOfTheBody() {
    assertDifferent(concat("A\u0001"), concat("B\u0001"));
    List<LambdaKey> keys = keys(concat("A\u0001"), concat("A\u0001"));
    assertThat(keys.get(0)).isEqualTo(keys.get(1));
  }

  @Test
  void nestedLambdaImplementationHandlesArePartOfTheBody() {
    assertDifferent(nestedLambda("first"), nestedLambda("second"));
  }

  @Test
  void branchDestinationsIncludeTheirPositions() {
    assertDifferent(branch(false), branch(true));
  }

  @Test
  void tableSwitchCaseDestinationsIncludeTheirPositions() {
    assertDifferent(switchBody(false, false), switchBody(false, true));
  }

  @Test
  void lookupSwitchCaseDestinationsIncludeTheirPositions() {
    assertDifferent(switchBody(true, false), switchBody(true, true));
  }

  @Test
  void exceptionTypesArePartOfTheBody() {
    assertDifferent(
        handler("java/lang/NumberFormatException", false),
        handler("java/lang/NullPointerException", false));
  }

  @Test
  void catchAllIsDifferentFromACatchType() {
    assertDifferent(handler(null, false), handler("java/lang/Exception", false));
  }

  @Test
  void exceptionRangesArePartOfTheBody() {
    assertDifferent(handler("java/lang/Exception", false), handler("java/lang/Exception", true));
  }

  @Test
  void exceptionHandlerOrderIsPartOfTheBody() {
    assertDifferent(orderedHandlers(false), orderedHandlers(true));
  }

  @Test
  void identicalExceptionTablesStillShare() {
    List<LambdaKey> keys =
        keys(handler("java/lang/Exception", false), handler("java/lang/Exception", false));
    assertThat(keys.get(0)).isEqualTo(keys.get(1));
  }

  @Test
  void arbitraryBootstrapBodiesUseExactMethodIdentity() {
    Consumer<MethodVisitor> body =
        mv -> {
          mv.visitInvokeDynamicInsn("value", "()Ljava/lang/Object;", CUSTOM_BOOTSTRAP);
          mv.visitInsn(Opcodes.ARETURN);
        };
    assertDifferent(body, body);
  }

  @Test
  void dynamicConstantsUseExactMethodIdentity() {
    var constant = new ConstantDynamic("value", "Ljava/lang/Object;", CUSTOM_BOOTSTRAP);
    assertDifferent(returnConstant(constant), returnConstant(constant));
  }

  @Test
  void nonSyntheticMethodsAreComparedByHandle() {
    var canonicalizer =
        canonicalizer(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            returnConstant("same"),
            returnConstant("same"));
    assertThat(key(canonicalizer, "lambda$first"))
        .isNotEqualTo(key(canonicalizer, "lambda$second"));
  }

  @Test
  void identicalInstructionsThatObserveTheirImplementationNamesRemainDistinct() {
    var provider = new StackInspectionProvider();
    assertThat(provider.first().apply(null)).isNotEqualTo(provider.second().apply(null));
    assertThat(
            new ConstraintProviderAnalyzer(StackInspectionProvider.class)
                .analyze()
                .hasShareableLambdas())
        .isFalse();
  }

  @Test
  void stackInspectionOperationsUseExactMethodIdentity() {
    List<Handle> operations =
        List.of(
            new Handle(
                Opcodes.H_INVOKEVIRTUAL,
                "java/lang/Throwable",
                "getStackTrace",
                "()[Ljava/lang/StackTraceElement;",
                false),
            new Handle(
                Opcodes.H_INVOKEVIRTUAL,
                "java/lang/IllegalStateException",
                "getStackTrace",
                "()[Ljava/lang/StackTraceElement;",
                false),
            new Handle(
                Opcodes.H_INVOKEVIRTUAL,
                "example/ApplicationException",
                "getStackTrace",
                "()[Ljava/lang/StackTraceElement;",
                false),
            new Handle(
                Opcodes.H_INVOKEVIRTUAL,
                "java/lang/Thread",
                "getStackTrace",
                "()[Ljava/lang/StackTraceElement;",
                false),
            new Handle(
                Opcodes.H_INVOKESTATIC,
                "java/lang/Thread",
                "getAllStackTraces",
                "()Ljava/util/Map;",
                false),
            new Handle(Opcodes.H_INVOKESTATIC, "java/lang/Thread", "dumpStack", "()V", false),
            new Handle(
                Opcodes.H_INVOKEVIRTUAL,
                "java/lang/Throwable",
                "fillInStackTrace",
                "()Ljava/lang/Throwable;",
                false),
            new Handle(
                Opcodes.H_INVOKEVIRTUAL, "java/lang/Throwable", "printStackTrace", "()V", false),
            new Handle(
                Opcodes.H_INVOKEVIRTUAL,
                "java/lang/Throwable",
                "printStackTrace",
                "(Ljava/io/PrintStream;)V",
                false),
            new Handle(
                Opcodes.H_INVOKEVIRTUAL,
                "java/lang/Throwable",
                "printStackTrace",
                "(Ljava/io/PrintWriter;)V",
                false),
            new Handle(
                Opcodes.H_INVOKESTATIC,
                "java/lang/StackWalker",
                "getInstance",
                "()Ljava/lang/StackWalker;",
                false));
    for (Handle operation : operations) {
      Consumer<MethodVisitor> body =
          mv -> {
            int opcode =
                operation.getTag() == Opcodes.H_INVOKESTATIC
                    ? Opcodes.INVOKESTATIC
                    : Opcodes.INVOKEVIRTUAL;
            if (opcode == Opcodes.INVOKEVIRTUAL) {
              mv.visitVarInsn(Opcodes.ALOAD, 0);
              mv.visitTypeInsn(Opcodes.CHECKCAST, operation.getOwner());
            }
            for (Type ignored : Type.getArgumentTypes(operation.getDesc())) {
              mv.visitInsn(Opcodes.ACONST_NULL);
            }
            mv.visitMethodInsn(
                opcode, operation.getOwner(), operation.getName(), operation.getDesc(), false);
            if (Type.getReturnType(operation.getDesc()).equals(Type.VOID_TYPE)) {
              mv.visitInsn(Opcodes.ACONST_NULL);
            }
            mv.visitInsn(Opcodes.ARETURN);
          };
      assertDifferent(body, body);
      // The same operations can be encoded as constant method handles.
      assertDifferent(returnConstant(operation), returnConstant(operation));
    }
  }

  @Test
  void unrelatedMethodsWithSimilarNamesCanStillShare() {
    Consumer<MethodVisitor> body =
        mv -> {
          mv.visitVarInsn(Opcodes.ALOAD, 0);
          mv.visitTypeInsn(Opcodes.CHECKCAST, "example/BusinessObject");
          mv.visitMethodInsn(
              Opcodes.INVOKEVIRTUAL,
              "example/BusinessObject",
              "getStackTrace",
              "()Ljava/lang/String;",
              false);
          mv.visitInsn(Opcodes.ARETURN);
        };
    List<LambdaKey> keys = keys(body, body);
    assertThat(keys.get(0)).isEqualTo(keys.get(1));
  }

  @Test
  void allCallSiteMetadataParticipatesInIdentity() {
    var canonicalizer = canonicalizer(returnConstant("same"), returnConstant("same"));
    Handle implementation = implementation("lambda$first");
    LambdaKey original =
        canonicalizer.buildKey(
            "apply",
            FACTORY_DESCRIPTOR,
            METAFACTORY,
            new Object[] {METHOD_TYPE, implementation, METHOD_TYPE});
    assertThat(original).isNotNull();
    assertThat(
            canonicalizer.buildKey(
                "otherName",
                FACTORY_DESCRIPTOR,
                METAFACTORY,
                new Object[] {METHOD_TYPE, implementation, METHOD_TYPE}))
        .isNotEqualTo(original);
    assertThat(
            canonicalizer.buildKey(
                "apply",
                "()Ljava/util/function/UnaryOperator;",
                METAFACTORY,
                new Object[] {METHOD_TYPE, implementation, METHOD_TYPE}))
        .isNotEqualTo(original);
    Type differentType = Type.getMethodType("(Ljava/lang/String;)Ljava/lang/Object;");
    assertThat(
            canonicalizer.buildKey(
                "apply",
                FACTORY_DESCRIPTOR,
                METAFACTORY,
                new Object[] {differentType, implementation, METHOD_TYPE}))
        .isNotEqualTo(original);
    assertThat(
            canonicalizer.buildKey(
                "apply",
                FACTORY_DESCRIPTOR,
                METAFACTORY,
                new Object[] {METHOD_TYPE, implementation, differentType}))
        .isNotEqualTo(original);
    Handle differentInterfaceFlag =
        new Handle(
            implementation.getTag(),
            implementation.getOwner(),
            implementation.getName(),
            implementation.getDesc(),
            true);
    assertThat(
            canonicalizer.buildKey(
                "apply",
                FACTORY_DESCRIPTOR,
                METAFACTORY,
                new Object[] {METHOD_TYPE, differentInterfaceFlag, METHOD_TYPE}))
        .isNotEqualTo(original);
    Handle differentTag =
        new Handle(
            Opcodes.H_INVOKEVIRTUAL,
            implementation.getOwner(),
            implementation.getName(),
            implementation.getDesc(),
            false);
    assertThat(
            canonicalizer.buildKey(
                "apply",
                FACTORY_DESCRIPTOR,
                METAFACTORY,
                new Object[] {METHOD_TYPE, differentTag, METHOD_TYPE}))
        .isNotEqualTo(original);
  }

  @Test
  void unsupportedBootstrapShapesAndCapturesAreIneligible() {
    var canonicalizer = canonicalizer(returnConstant("same"), returnConstant("same"));
    Object[] arguments = {METHOD_TYPE, implementation("lambda$first"), METHOD_TYPE};
    assertThat(
            canonicalizer.buildKey(
                "apply",
                FACTORY_DESCRIPTOR,
                ALT_METAFACTORY,
                new Object[] {METHOD_TYPE, implementation("lambda$first"), METHOD_TYPE, 1}))
        .isNull();
    assertThat(canonicalizer.buildKey("apply", FACTORY_DESCRIPTOR, CUSTOM_BOOTSTRAP, arguments))
        .isNull();
    assertThat(
            canonicalizer.buildKey(
                "apply",
                "(Ljava/lang/Object;)Ljava/util/function/Function;",
                METAFACTORY,
                arguments))
        .isNull();
    assertThat(
            canonicalizer.buildKey(
                "apply",
                FACTORY_DESCRIPTOR,
                METAFACTORY,
                new Object[] {METHOD_TYPE, "not a handle", METHOD_TYPE}))
        .isNull();
    assertThat(
            canonicalizer.buildKey(
                "apply",
                FACTORY_DESCRIPTOR,
                METAFACTORY,
                new Object[] {
                  Type.getType(String.class), implementation("lambda$first"), METHOD_TYPE
                }))
        .isNull();
    assertThat(
            canonicalizer.buildKey(
                "apply",
                FACTORY_DESCRIPTOR,
                METAFACTORY,
                new Object[] {METHOD_TYPE, implementation("lambda$first"), METHOD_TYPE, 0}))
        .isNull();
    Handle wrongDescriptor =
        new Handle(
            METAFACTORY.getTag(), METAFACTORY.getOwner(), METAFACTORY.getName(), "()V", false);
    assertThat(canonicalizer.buildKey("apply", FACTORY_DESCRIPTOR, wrongDescriptor, arguments))
        .isNull();
    Handle wrongTag =
        new Handle(
            Opcodes.H_INVOKEVIRTUAL,
            METAFACTORY.getOwner(),
            METAFACTORY.getName(),
            METAFACTORY.getDesc(),
            false);
    assertThat(canonicalizer.buildKey("apply", FACTORY_DESCRIPTOR, wrongTag, arguments)).isNull();
  }

  @Test
  void findingVisitorPreservesCompleteIdentityAndSkipsIneligibleSites() {
    var canonicalizer = canonicalizer(returnConstant("same"), returnConstant("same"));
    var visitor = new LambdaFindingVisitor(OWNER, "factory", canonicalizer);
    Object[] arguments = {METHOD_TYPE, implementation("lambda$first"), METHOD_TYPE};
    visitor.visitInvokeDynamicInsn("apply", FACTORY_DESCRIPTOR, ALT_METAFACTORY, arguments);
    visitor.visitInvokeDynamicInsn("apply", FACTORY_DESCRIPTOR, METAFACTORY, arguments);
    visitor.visitInvokeDynamicInsn("anotherSam", FACTORY_DESCRIPTOR, METAFACTORY, arguments);
    assertThat(visitor.getLambdas()).hasSize(2);
    assertThat(visitor.getLambdas().get(0).getInstructionOffset()).isEqualTo(1);
    assertThat(visitor.getLambdas().get(0).getKey())
        .isNotEqualTo(visitor.getLambdas().get(1).getKey());
    assertThat(visitor.getLambdas().get(0).getKey()).isEqualTo(key(canonicalizer, "lambda$first"));
  }

  private static void assertDifferent(
      Consumer<MethodVisitor> first, Consumer<MethodVisitor> second) {
    List<LambdaKey> keys = keys(first, second);
    assertThat(keys.get(0)).isNotEqualTo(keys.get(1));
  }

  private static List<LambdaKey> keys(
      Consumer<MethodVisitor> first, Consumer<MethodVisitor> second) {
    var canonicalizer = canonicalizer(first, second);
    return List.of(key(canonicalizer, "lambda$first"), key(canonicalizer, "lambda$second"));
  }

  private static LambdaKey key(LambdaImplementationCanonicalizer canonicalizer, String methodName) {
    return canonicalizer.buildKey(
        "apply",
        FACTORY_DESCRIPTOR,
        METAFACTORY,
        new Object[] {METHOD_TYPE, implementation(methodName), METHOD_TYPE});
  }

  private static Handle implementation(String methodName) {
    return new Handle(Opcodes.H_INVOKESTATIC, OWNER, methodName, BODY_DESCRIPTOR, false);
  }

  private static LambdaImplementationCanonicalizer canonicalizer(
      Consumer<MethodVisitor> first, Consumer<MethodVisitor> second) {
    return canonicalizer(
        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, first, second);
  }

  private static LambdaImplementationCanonicalizer canonicalizer(
      int access, Consumer<MethodVisitor> first, Consumer<MethodVisitor> second) {
    // These fixtures are read as bytecode, never loaded. Explicit jumps and exception ranges make
    // regressions independent of the source compiler's choice of control-flow layout.
    ClassWriter writer = new ClassWriter(0);
    writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, OWNER, null, "java/lang/Object", null);
    method(writer, access, "lambda$first", first);
    method(writer, access, "lambda$second", second);
    writer.visitEnd();
    return new LambdaImplementationCanonicalizer(OWNER, writer.toByteArray());
  }

  private static void method(
      ClassWriter writer, int access, String name, Consumer<MethodVisitor> body) {
    MethodVisitor mv = writer.visitMethod(access, name, BODY_DESCRIPTOR, null, null);
    mv.visitCode();
    body.accept(mv);
    mv.visitMaxs(8, 2);
    mv.visitEnd();
  }

  private static Consumer<MethodVisitor> returnConstant(Object constant) {
    return mv -> {
      mv.visitLdcInsn(constant);
      mv.visitInsn(Opcodes.ARETURN);
    };
  }

  private static Consumer<MethodVisitor> returnFloatBits(int bits) {
    return mv -> {
      mv.visitLdcInsn(Float.intBitsToFloat(bits));
      mv.visitMethodInsn(
          Opcodes.INVOKESTATIC, "java/lang/Float", "floatToRawIntBits", "(F)I", false);
      mv.visitMethodInsn(
          Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
      mv.visitInsn(Opcodes.ARETURN);
    };
  }

  private static Consumer<MethodVisitor> concat(String recipe) {
    return mv -> {
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitInvokeDynamicInsn(
          "makeConcatWithConstants", "(Ljava/lang/Object;)Ljava/lang/String;", CONCAT, recipe);
      mv.visitInsn(Opcodes.ARETURN);
    };
  }

  private static Consumer<MethodVisitor> nestedLambda(String implementationName) {
    return mv -> {
      mv.visitInvokeDynamicInsn(
          "apply",
          FACTORY_DESCRIPTOR,
          METAFACTORY,
          METHOD_TYPE,
          implementation(implementationName),
          METHOD_TYPE);
      mv.visitInsn(Opcodes.ARETURN);
    };
  }

  private static Consumer<MethodVisitor> branch(boolean laterTarget) {
    return mv -> {
      Label earlier = new Label();
      Label later = new Label();
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitJumpInsn(Opcodes.IFNULL, laterTarget ? later : earlier);
      mv.visitLabel(earlier);
      mv.visitLdcInsn("first");
      mv.visitInsn(Opcodes.ARETURN);
      mv.visitLabel(later);
      mv.visitLdcInsn("second");
      mv.visitInsn(Opcodes.ARETURN);
    };
  }

  private static Consumer<MethodVisitor> switchBody(boolean lookup, boolean swapped) {
    return mv -> {
      Label first = new Label();
      Label second = new Label();
      Label dflt = new Label();
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
      mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
      Label[] targets = swapped ? new Label[] {second, first} : new Label[] {first, second};
      if (lookup) {
        mv.visitLookupSwitchInsn(dflt, new int[] {1, 100}, targets);
      } else {
        mv.visitTableSwitchInsn(1, 2, dflt, targets);
      }
      mv.visitLabel(first);
      mv.visitLdcInsn("first");
      mv.visitInsn(Opcodes.ARETURN);
      mv.visitLabel(second);
      mv.visitLdcInsn("second");
      mv.visitInsn(Opcodes.ARETURN);
      mv.visitLabel(dflt);
      mv.visitInsn(Opcodes.ACONST_NULL);
      mv.visitInsn(Opcodes.ARETURN);
    };
  }

  private static Consumer<MethodVisitor> handler(String caughtType, boolean laterStart) {
    return mv -> {
      Label start = new Label();
      Label middle = new Label();
      Label end = new Label();
      Label handler = new Label();
      mv.visitTryCatchBlock(laterStart ? middle : start, end, handler, caughtType);
      mv.visitLabel(start);
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
      mv.visitLabel(middle);
      mv.visitMethodInsn(
          Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
      mv.visitInsn(Opcodes.POP);
      mv.visitLabel(end);
      mv.visitInsn(Opcodes.ACONST_NULL);
      mv.visitInsn(Opcodes.ARETURN);
      mv.visitLabel(handler);
      mv.visitInsn(Opcodes.POP);
      mv.visitLdcInsn("caught");
      mv.visitInsn(Opcodes.ARETURN);
    };
  }

  private static Consumer<MethodVisitor> orderedHandlers(boolean swapped) {
    return mv -> {
      Label start = new Label();
      Label end = new Label();
      Label first = new Label();
      Label second = new Label();
      if (swapped) {
        mv.visitTryCatchBlock(start, end, second, "java/lang/Exception");
        mv.visitTryCatchBlock(start, end, first, "java/lang/NumberFormatException");
      } else {
        mv.visitTryCatchBlock(start, end, first, "java/lang/NumberFormatException");
        mv.visitTryCatchBlock(start, end, second, "java/lang/Exception");
      }
      mv.visitLabel(start);
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
      mv.visitInsn(Opcodes.POP);
      mv.visitLabel(end);
      mv.visitInsn(Opcodes.ACONST_NULL);
      mv.visitInsn(Opcodes.ARETURN);
      mv.visitLabel(first);
      mv.visitInsn(Opcodes.POP);
      mv.visitLdcInsn("first");
      mv.visitInsn(Opcodes.ARETURN);
      mv.visitLabel(second);
      mv.visitInsn(Opcodes.POP);
      mv.visitLdcInsn("second");
      mv.visitInsn(Opcodes.ARETURN);
    };
  }

  static class StackInspectionProvider implements ConstraintProvider {
    Function<Object, String> first() {
      return value -> new Throwable().getStackTrace()[0].getMethodName();
    }

    Function<Object, String> second() {
      return value -> new Throwable().getStackTrace()[0].getMethodName();
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }
  }
}
