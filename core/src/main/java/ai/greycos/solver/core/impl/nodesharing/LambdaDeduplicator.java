package ai.greycos.solver.core.impl.nodesharing;

import java.util.HashMap;
import java.util.Map;

/**
 * Deduplicates lambdas by assigning unique field names to each group of identical lambdas.
 *
 * <p>Each group of identical lambdas gets a unique field name (e.g., $predicate1, $function2) that
 * will be used in the transformed class.
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

  /** Assigns unique field names to each lambda group. */
  private void assignFieldNames() {
    int fieldIndex = 1;

    for (LambdaKey key : analysis.getShareableLambdas().keySet()) {
      String fieldName = generateFieldName(key, fieldIndex++);
      String fieldDescriptor = generateFieldDescriptor(key);

      fieldNames.put(key, fieldName);
      fieldDescriptors.put(key, fieldDescriptor);
    }
  }

  /**
   * Generates a field name for a lambda.
   *
   * <p>Uses naming convention: $predicate1, $function2, $joiner3, etc.
   */
  private String generateFieldName(LambdaKey key, int index) {
    String functionalInterface = key.getFunctionalInterfaceType();

    // Extract simple name from functional interface type
    String simpleName = functionalInterface.substring(functionalInterface.lastIndexOf('.') + 1);

    // Map common functional interfaces to prefixes
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

  /** Generates field descriptor for a lambda based on its functional interface type. */
  private String generateFieldDescriptor(LambdaKey key) {
    // For lambdas, the field type is the functional interface
    // Convert class name to descriptor format: Ljava/util/function/Predicate;
    return "L" + key.getFunctionalInterfaceType().replace('.', '/') + ";";
  }

  /**
   * Gets the field name assigned to a lambda key.
   *
   * @param key lambda key
   * @return field name, or null if no field assigned
   */
  public String getFieldName(LambdaKey key) {
    return fieldNames.get(key);
  }

  /**
   * Gets the field descriptor for a lambda key.
   *
   * @param key lambda key
   * @return field descriptor, or null if no field assigned
   */
  public String getFieldDescriptor(LambdaKey key) {
    return fieldDescriptors.get(key);
  }

  /**
   * Gets the analysis this deduplicator is based on.
   *
   * @return lambda analysis
   */
  public LambdaAnalysis getAnalysis() {
    return analysis;
  }
}
