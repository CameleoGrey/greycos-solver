package greycos.solver.migration.v2;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

@Execution(ExecutionMode.CONCURRENT)
class ConstraintMetadataMigrationRecipeTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(new ConstraintMetadataMigrationRecipe())
        .parser(
            JavaParser.fromJavaVersion()
                .dependsOn(
                    """
                    package greycos.solver.core.api.score.stream;
                    public interface ConstraintBuilder {
                      Object asConstraintDescribed(String name, String description);
                      Object asConstraintDescribed(String name, String group, String description);
                    }""",
                    """
                    package greycos.solver.core.api.score.stream;
                    public interface ConstraintRef {
                      String constraintName();
                    }""",
                    """
                    package greycos.solver.core.api.score.analysis;
                    public interface ConstraintAnalysis<Score_> {
                      String constraintName();
                    }"""));
  }

  @Test
  void migratesConstraintMetadataAndIdentity() {
    rewriteRun(
        java(
            """
            import greycos.solver.core.api.score.analysis.ConstraintAnalysis;
            import greycos.solver.core.api.score.stream.ConstraintBuilder;
            import greycos.solver.core.api.score.stream.ConstraintRef;

            class Test {
              void method(ConstraintBuilder builder, ConstraintRef ref, ConstraintAnalysis<?> analysis) {
                builder.asConstraintDescribed("first", "Description");
                builder.asConstraintDescribed("second", "group", "Description");
                String first = ref.constraintName();
                String second = analysis.constraintName();
              }
            }""",
            """
            import greycos.solver.core.api.score.analysis.ConstraintAnalysis;
            import greycos.solver.core.api.score.stream.ConstraintBuilder;
            import greycos.solver.core.api.score.stream.ConstraintRef;

            class Test {
              void method(ConstraintBuilder builder, ConstraintRef ref, ConstraintAnalysis<?> analysis) {
                builder.asConstraint("first");
                builder.asConstraint("second");
                String first = ref.id();
                String second = analysis.constraintId();
              }
            }"""));
  }
}
