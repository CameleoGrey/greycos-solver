package ai.greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Simple test to debug lambda finding. */
class LambdaFindingDebugTest {

  @Test
  void testLambdaFinding() {
    System.out.println("=== Testing Lambda Finding ===");

    // Create a simple lambda to see what it looks like
    java.util.function.Predicate<String> lambda1 = s -> s.length() > 0;
    java.util.function.Predicate<String> lambda2 = s -> s.length() > 0;

    System.out.println("lambda1 == lambda2: " + (lambda1 == lambda2));
    System.out.println("lambda1: " + lambda1);
    System.out.println("lambda2: " + lambda2);

    // Now test with TestConstraintProvider
    ConstraintProviderAnalyzer analyzer =
        new ConstraintProviderAnalyzer(TestConstraintProvider.class);
    LambdaAnalysis analysis = analyzer.analyze();

    System.out.println("Shareable lambdas: " + analysis.getShareableLambdaGroupCount());
    System.out.println("Total shareable lambda occurrences: " + analysis.getShareableLambdaCount());
    System.out.println("Has shareable lambdas: " + analysis.hasShareableLambdas());
    System.out.println("All lambdas by key:");
    for (var entry : analysis.getShareableLambdas().entrySet()) {
      System.out.println("  Key: " + entry.getKey() + ", Count: " + entry.getValue().size());
      for (LambdaInfo info : entry.getValue()) {
        System.out.println(
            "    Method: " + info.getMethodName() + ", Offset: " + info.getInstructionOffset());
      }
    }

    assertThat(analysis).isNotNull();
  }
}
