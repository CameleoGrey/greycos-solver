package greycos.solver.migration.v2;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

@Execution(ExecutionMode.CONCURRENT)
class SolverConfigOverrideSolutionDeletionMigrationRecipeTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(new SolverConfigOverrideSolutionDeletionMigrationRecipe())
        .parser(
            JavaParser.fromJavaVersion()
                .dependsOn(
                    """
                    package greycos.solver.core.api.solver;
                    public class SolverConfigOverride<Solution_> {
                    }"""));
  }

  @Test
  void removesSolutionGeneric() {
    rewriteRun(
        java(
            """
            import greycos.solver.core.api.solver.SolverConfigOverride;

            class Test<Solution_> {
              SolverConfigOverride<Solution_> override = new SolverConfigOverride<>();
            }""",
            """
            import greycos.solver.core.api.solver.SolverConfigOverride;

            class Test<Solution_> {
              SolverConfigOverride override = new SolverConfigOverride();
            }"""));
  }
}
