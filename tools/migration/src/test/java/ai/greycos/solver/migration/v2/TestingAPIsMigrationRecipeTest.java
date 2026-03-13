package ai.greycos.solver.migration.v2;

import static org.openrewrite.java.Assertions.java;

import java.util.List;

import ai.greycos.solver.migration.NoWildCardImportStyle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

@Execution(ExecutionMode.CONCURRENT)
class TestingAPIsMigrationRecipeTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(new TestingAPIsMigrationRecipe())
        .typeValidationOptions(TypeValidation.builder().allowMissingType(ignore -> true).build())
        .parser(
            JavaParser.fromJavaVersion()
                .styles(List.of(new NoWildCardImportStyle()))
                // We must add all old classes as stubs to the JavaTemplate
                .dependsOn(
                    "package ai.greycos.solver.test.api.score.stream; public class ConstraintVerifier {}",
                    "package ai.greycos.solver.test.api.solver.change; public class MockProblemChangeDirector {}",
                    "package ai.greycos.solver.core.preview.api.move; public class MoveTester {}",
                    "package ai.greycos.solver.core.preview.api.neighborhood; public class NeighborhoodTester {}"));
  }

  @Test
  void migrate() {
    rewriteRun(
        java(
            """
                package greycos;

                import ai.greycos.solver.test.api.score.stream.ConstraintVerifier;
                import ai.greycos.solver.test.api.solver.change.MockProblemChangeDirector;
                import ai.greycos.solver.core.preview.api.move.MoveTester;
                import ai.greycos.solver.core.preview.api.neighborhood.NeighborhoodTester;

                public class Test {
                        ConstraintVerifier constraintVerifier;
                        MockProblemChangeDirector problemChangeDirector;
                        MoveTester moveTester;
                        NeighborhoodTester neighborhoodTester;
                }""",
            """
                package greycos;

                import ai.greycos.solver.core.api.score.stream.test.ConstraintVerifier;
                import ai.greycos.solver.core.api.solver.change.MockProblemChangeDirector;
                import ai.greycos.solver.core.preview.api.move.test.MoveTester;
                import ai.greycos.solver.core.preview.api.neighborhood.test.NeighborhoodTester;

                public class Test {
                        ConstraintVerifier constraintVerifier;
                        MockProblemChangeDirector problemChangeDirector;
                        MoveTester moveTester;
                        NeighborhoodTester neighborhoodTester;
                }"""));
  }
}
