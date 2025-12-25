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
 * Analyzes a ConstraintProvider class to find lambda expressions suitable for node sharing.
 *
 * <p>Scans all methods for lambda expressions (invokedynamic instructions with lambda metafactory),
 * groups identical lambdas, and prepares metadata for bytecode transformation.
 */
public final class ConstraintProviderAnalyzer {

  private final Class<?> constraintProviderClass;

  public ConstraintProviderAnalyzer(Class<?> constraintProviderClass) {
    this.constraintProviderClass = constraintProviderClass;
  }

  /**
   * Analyzes ConstraintProvider class and returns all lambda information.
   *
   * @return LambdaAnalysis containing all found lambdas grouped by identity
   */
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

      // Visit all methods to find lambdas
      reader.accept(
          new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {

              // Skip bridge methods and synthetic methods
              if ((access & Opcodes.ACC_SYNTHETIC) != 0 || (access & Opcodes.ACC_BRIDGE) != 0) {
                return null;
              }

              // Create LambdaFindingVisitor without parent (analysis only)
              return new LambdaFindingVisitor(className, name) {
                @Override
                public void visitEnd() {
                  // Collect lambdas found in this method
                  allLambdas.addAll(getLambdas());
                  super.visitEnd();
                }
              };
            }
          },
          ClassReader.EXPAND_FRAMES); // Expand frames to ensure proper bytecode visiting

      return groupIdenticalLambdas(allLambdas);

    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to read class file for " + constraintProviderClass.getName(), e);
    }
  }

  /**
   * Groups identical lambdas together.
   *
   * <p>Lambdas are considered identical if they have same LambdaKey.
   *
   * @param lambdas all lambdas found in class
   * @return LambdaAnalysis with grouped lambdas
   */
  private LambdaAnalysis groupIdenticalLambdas(List<LambdaInfo> lambdas) {
    System.err.println(
        "DEBUG ConstraintProviderAnalyzer: groupIdenticalLambdas called with "
            + lambdas.size()
            + " lambdas");
    Map<LambdaKey, List<LambdaInfo>> grouped = new HashMap<>();

    for (LambdaInfo lambda : lambdas) {
      LambdaKey key = lambda.getKey();
      System.err.println("  Processing lambda with key: " + key);
      grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(lambda);
    }

    System.err.println("  Grouped into " + grouped.size() + " groups");
    for (var entry : grouped.entrySet()) {
      System.err.println("    Key: " + entry.getKey() + ", Count: " + entry.getValue().size());
    }

    // Remove lambdas that appear only once (no sharing benefit)
    grouped.entrySet().removeIf(entry -> entry.getValue().size() < 2);

    System.err.println("  After removing singles: " + grouped.size() + " groups");

    return new LambdaAnalysis(grouped);
  }
}
