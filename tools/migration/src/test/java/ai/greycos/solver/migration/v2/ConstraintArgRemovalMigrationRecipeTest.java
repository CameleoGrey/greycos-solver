package ai.greycos.solver.migration.v2;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

@Execution(ExecutionMode.CONCURRENT)
class ConstraintArgRemovalMigrationRecipeTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(new ConstraintArgRemovalMigrationRecipe())
        .parser(
            JavaParser.fromJavaVersion()
                .dependsOn(
                    """
                    package ai.greycos.solver.core.api.score.constraint;
                    public interface ConstraintRef {
                      static ConstraintRef of(String constraintPackage, String constraintName) {
                        return null;
                      }
                    }""",
                    """
                    package ai.greycos.solver.core.api.score.stream;
                    public interface ConstraintBuilder {
                      Object asConstraint(String constraintPackage, String constraintName);
                    }"""));
  }

  @Test
  void removesConstraintPackages() {
    rewriteRun(
        java(
            """
            import ai.greycos.solver.core.api.score.constraint.ConstraintRef;
            import ai.greycos.solver.core.api.score.stream.ConstraintBuilder;

            class Test {
              void method(ConstraintBuilder builder) {
                ConstraintRef ref = ConstraintRef.of("com.example", "myConstraint");
                builder.asConstraint("com.example", "myConstraint");
              }
            }""",
            """
            import ai.greycos.solver.core.api.score.constraint.ConstraintRef;
            import ai.greycos.solver.core.api.score.stream.ConstraintBuilder;

            class Test {
              void method(ConstraintBuilder builder) {
                ConstraintRef ref = ConstraintRef.of("myConstraint");
                builder.asConstraint("myConstraint");
              }
            }"""));
  }
}
