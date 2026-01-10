package ai.greycos.solver.core.impl.nodesharing;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Analyzes ConstraintProvider bytecode to find lambdas suitable for node sharing.
 *
 * <p>Why: Need to identify which lambdas can be deduplicated before transformation.
 * How: Scans bytecode for invokedynamic instructions and groups identical lambdas.
 * What: Produces LambdaAnalysis with metadata for bytecode transformation.
 */
public final class ConstraintProviderAnalyzer {

  private final Class<?> constraintProviderClass;

  public ConstraintProviderAnalyzer(Class<?> constraintProviderClass) {
    this.constraintProviderClass = constraintProviderClass;
  }

  public LambdaAnalysis analyze() {
    String className = constraintProviderClass.getName().replace('.', '/');
    String classFileName = className + ".class";

    try (InputStream is =
        constraintProviderClass.getClassLoader().getResourceAsStream(classFileName)) {
      if (is == null) {
        throw new IllegalStateException(
            "Cannot find class file for " + constraintProviderClass.getName());
      }

      ClassReader reader = new ClassReader(is);
      List<LambdaInfo> allLambdas = new ArrayList<>();

      reader.accept(
          new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {

              if ((access & Opcodes.ACC_SYNTHETIC) != 0 || (access & Opcodes.ACC_BRIDGE) != 0) {
                return null;
              }

              return new LambdaFindingVisitor(className, name) {
                @Override
                public void visitEnd() {
                  allLambdas.addAll(getLambdas());
                  super.visitEnd();
                }
              };
            }
          },
          ClassReader.EXPAND_FRAMES);

      return groupIdenticalLambdas(allLambdas);

    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to read class file for " + constraintProviderClass.getName(), e);
    }
  }

  private LambdaAnalysis groupIdenticalLambdas(List<LambdaInfo> lambdas) {
    Map<LambdaKey, List<LambdaInfo>> grouped = new HashMap<>();

    for (LambdaInfo lambda : lambdas) {
      LambdaKey key = lambda.getKey();
      grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(lambda);
    }

    grouped.entrySet().removeIf(entry -> entry.getValue().size() < 2);

    return new LambdaAnalysis(grouped);
  }
}
