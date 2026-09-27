package greycos.solver.core.impl.nodesharing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Finds eligible lambda call sites using the same identity as the transformer. */
public class LambdaFindingVisitor extends MethodVisitor {

  private final String methodName;
  private final LambdaImplementationCanonicalizer implementationCanonicalizer;
  private final List<LambdaInfo> lambdas = new ArrayList<>();
  private int invokedynamicIndex;

  public LambdaFindingVisitor(
      String className,
      String methodName,
      LambdaImplementationCanonicalizer implementationCanonicalizer) {
    super(Opcodes.ASM9);
    Objects.requireNonNull(className);
    this.methodName = Objects.requireNonNull(methodName);
    this.implementationCanonicalizer = Objects.requireNonNull(implementationCanonicalizer);
  }

  @Override
  public void visitInvokeDynamicInsn(
      String name,
      String descriptor,
      Handle bootstrapMethodHandle,
      Object... bootstrapMethodArguments) {
    LambdaKey key =
        implementationCanonicalizer.buildKey(
            name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
    if (key != null) {
      lambdas.add(new LambdaInfo(methodName, invokedynamicIndex, key));
    }
    invokedynamicIndex++;
  }

  public List<LambdaInfo> getLambdas() {
    return List.copyOf(lambdas);
  }
}
