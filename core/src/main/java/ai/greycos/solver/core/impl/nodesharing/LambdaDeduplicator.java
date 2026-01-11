package ai.greycos.solver.core.impl.nodesharing;

import java.util.HashMap;
import java.util.Map;

/**
 * Assigns unique field names to groups of identical lambdas for bytecode transformation.
 *
 * <p>Why: Identical lambdas need shared fields with consistent naming. How: Maps each lambda group
 * to a unique field name (e.g., $predicate1). What: Provides field metadata for ASM bytecode
 * transformation.
 */
public final class LambdaDeduplicator {

  private final LambdaAnalysis analysis;
  private final Map<LambdaKey, String> fieldNames;
  private final Map<LambdaKey, String> fieldDescriptors;

  public LambdaDeduplicator(LambdaAnalysis analysis) {
    this.analysis = analysis;
    this.fieldNames = new HashMap<>();
    this.fieldDescriptors = new HashMap<>();
    assignFieldNames();
  }

  private void assignFieldNames() {
    int fieldIndex = 1;

    for (LambdaKey key : analysis.getShareableLambdas().keySet()) {
      String fieldName = generateFieldName(key, fieldIndex++);
      String fieldDescriptor = generateFieldDescriptor(key);

      fieldNames.put(key, fieldName);
      fieldDescriptors.put(key, fieldDescriptor);
    }
  }

  private String generateFieldName(LambdaKey key, int index) {
    String functionalInterface = key.getFunctionalInterfaceType();

    String simpleName = functionalInterface.substring(functionalInterface.lastIndexOf('.') + 1);

    String prefix =
        switch (simpleName) {
          case "Predicate" -> "$predicate";
          case "Function" -> "$function";
          case "BiFunction" -> "$bifunction";
          case "TriFunction" -> "$trifunction";
          case "QuadFunction" -> "$quadfunction";
          case "Consumer" -> "$consumer";
          case "BiConsumer" -> "$biconsumer";
          case "Supplier" -> "$supplier";
          case "Joiner" -> "$joiner";
          case "Collector" -> "$collector";
          default -> "$lambda";
        };

    return prefix + index;
  }

  private String generateFieldDescriptor(LambdaKey key) {
    return "L" + key.getFunctionalInterfaceType().replace('.', '/') + ";";
  }

  public String getFieldName(LambdaKey key) {
    return fieldNames.get(key);
  }

  public String getFieldDescriptor(LambdaKey key) {
    return fieldDescriptors.get(key);
  }

  public LambdaAnalysis getAnalysis() {
    return analysis;
  }
}
