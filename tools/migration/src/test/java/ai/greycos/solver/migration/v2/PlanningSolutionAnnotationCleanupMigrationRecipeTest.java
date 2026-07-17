package ai.greycos.solver.migration.v2;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

@Execution(ExecutionMode.CONCURRENT)
class PlanningSolutionAnnotationCleanupMigrationRecipeTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(new PlanningSolutionAnnotationCleanupMigrationRecipe())
        .parser(
            JavaParser.fromJavaVersion()
                .dependsOn(
                    """
                    package ai.greycos.solver.core.api.cotwin.solution;
                    public @interface PlanningSolution {
                      String lookUpStrategyType() default "";
                      String autoDiscoverMemberType() default "";
                    }"""));
  }

  @Test
  void removesBothDeprecatedAttributes() {
    rewriteRun(
        java(
            """
            import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;

            @PlanningSolution(lookUpStrategyType = "PLANNING_ID", autoDiscoverMemberType = "FIELD")
            class MySolution {
            }""",
            """
            import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;

            @PlanningSolution
            class MySolution {
            }"""));
  }
}
