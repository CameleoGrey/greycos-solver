package greycos.solver.migration.v2;

import static org.openrewrite.java.Assertions.java;

import java.util.List;

import greycos.solver.migration.NoWildCardImportStyle;

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
                    "package greycos.solver.test.api.score.stream; public class ConstraintVerifier"
                        + " {}",
                    """
                    package greycos.solver.test.api.score.stream;
                    public interface SingleConstraintAssertion {
                        SingleConstraintAssertion indictsWith(Object... indictedObjects);
                        SingleConstraintAssertion indictsWith(String message, Object... indictedObjects);
                        SingleConstraintAssertion indictsWithExactly(Object... indictedObjects);
                        SingleConstraintAssertion indictsWithExactly(String message, Object... indictedObjects);
                    }
                    """,
                    """
                    package greycos.solver.core.api.score.stream.test;
                    public interface SingleConstraintAssertion {
                        SingleConstraintAssertion indictsWith(Object... indictedObjects);
                        SingleConstraintAssertion indictsWithMessage(String message, Object... indictedObjects);
                        SingleConstraintAssertion indictsWithExactly(Object... indictedObjects);
                        SingleConstraintAssertion indictsExactlyWithMessage(String message, Object... indictedObjects);
                    }
                    """,
                    "package greycos.solver.test.api.solver.change; public class"
                        + " MockProblemChangeDirector {}",
                    "package greycos.solver.core.preview.api.move; public class MoveTester {}",
                    "package greycos.solver.core.preview.api.neighborhood; public class"
                        + " NeighborhoodTester {}"));
  }

  @Test
  void migrateIndictmentAssertionsWithoutRemovingThem() {
    rewriteRun(
        spec ->
            spec.recipes(
                    new GeneralMethodDeleteInvocationMigrationRecipe(),
                    new TestingAPIsMigrationRecipe())
                .cycles(2)
                .expectedCyclesThatMakeChanges(1),
        java(
            """
            package greycos;

            import greycos.solver.test.api.score.stream.SingleConstraintAssertion;

            class Test {
                void verify(SingleConstraintAssertion assertion, Object fact) {
                    assertion.indictsWith(fact).indictsWithExactly(fact);
                }
            }
            """,
            """
            package greycos;

            import greycos.solver.core.api.score.stream.test.SingleConstraintAssertion;

            class Test {
                void verify(SingleConstraintAssertion assertion, Object fact) {
                    assertion.indictsWith(fact).indictsWithExactly(fact);
                }
            }
            """));
  }

  @Test
  void migrateOnlyLegacyMessageOverloadsBeforeChangingTheirPackage() {
    rewriteRun(
        spec ->
            spec.recipes(
                    new GeneralMethodDeleteInvocationMigrationRecipe(),
                    new TestingAPIsMigrationRecipe())
                .cycles(2)
                .expectedCyclesThatMakeChanges(1),
        java(
            """
            package greycos;

            import greycos.solver.test.api.score.stream.SingleConstraintAssertion;

            class Test {
                void verify(SingleConstraintAssertion assertion, Object fact, Object[] facts) {
                    assertion.indictsWith("subset", fact).indictsWithExactly("exact", fact);
                    assertion.indictsWith("array", facts).indictsWithExactly("array", new Object[] { fact });
                    assertion.indictsWith("empty").indictsWithExactly((String) null);
                    assertion.indictsWith((String) null, fact).indictsWithExactly("strings", "first", "second");
                    assertion.indictsWith(facts).indictsWithExactly(new Object[] { "first", fact });
                    assertion.indictsWith((Object) "first", fact).indictsWithExactly((Object) "first");
                }
            }
            """,
            """
            package greycos;

            import greycos.solver.core.api.score.stream.test.SingleConstraintAssertion;

            class Test {
                void verify(SingleConstraintAssertion assertion, Object fact, Object[] facts) {
                    assertion.indictsWithMessage("subset", fact).indictsExactlyWithMessage("exact", fact);
                    assertion.indictsWithMessage("array", facts).indictsExactlyWithMessage("array", new Object[] { fact });
                    assertion.indictsWithMessage("empty").indictsExactlyWithMessage((String) null);
                    assertion.indictsWithMessage((String) null, fact).indictsExactlyWithMessage("strings", "first", "second");
                    assertion.indictsWith(facts).indictsWithExactly(new Object[] { "first", fact });
                    assertion.indictsWith((Object) "first", fact).indictsWithExactly((Object) "first");
                }
            }
            """));
  }

  @Test
  void migrateLegacyMessageMethodReferencesWithoutRenamingPlainVarargsReferences() {
    rewriteRun(
        spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
        java(
            """
            package greycos;

            import greycos.solver.test.api.score.stream.SingleConstraintAssertion;

            import java.util.function.BiFunction;
            import java.util.function.Function;

            class Test {
                void verify(SingleConstraintAssertion assertion) {
                    BiFunction<String, Object[], SingleConstraintAssertion> subset = assertion::indictsWith;
                    BiFunction<String, Object[], SingleConstraintAssertion> exact = assertion::indictsWithExactly;
                    Function<Object[], SingleConstraintAssertion> plainSubset = assertion::indictsWith;
                    Function<Object[], SingleConstraintAssertion> plainExact = assertion::indictsWithExactly;
                }
            }
            """,
            """
            package greycos;

            import greycos.solver.core.api.score.stream.test.SingleConstraintAssertion;

            import java.util.function.BiFunction;
            import java.util.function.Function;

            class Test {
                void verify(SingleConstraintAssertion assertion) {
                    BiFunction<String, Object[], SingleConstraintAssertion> subset = assertion::indictsWithMessage;
                    BiFunction<String, Object[], SingleConstraintAssertion> exact = assertion::indictsExactlyWithMessage;
                    Function<Object[], SingleConstraintAssertion> plainSubset = assertion::indictsWith;
                    Function<Object[], SingleConstraintAssertion> plainExact = assertion::indictsWithExactly;
                }
            }
            """));
  }

  @Test
  void migratedIndictmentAssertionsAreIdempotent() {
    rewriteRun(
        spec ->
            spec.recipes(
                new GeneralMethodDeleteInvocationMigrationRecipe(),
                new TestingAPIsMigrationRecipe()),
        java(
            """
            package greycos;

            import greycos.solver.core.api.score.stream.test.SingleConstraintAssertion;

            class Test {
                void verify(SingleConstraintAssertion assertion, Object fact) {
                    assertion.indictsWith(fact).indictsWithExactly(fact);
                    assertion.indictsWith("first", "second").indictsWithExactly("first", "second");
                    assertion.indictsWithMessage("subset", fact).indictsExactlyWithMessage("exact", fact);
                }
            }
            """));
  }

  @Test
  void migrate() {
    rewriteRun(
        java(
            """
            package greycos;

            import greycos.solver.test.api.score.stream.ConstraintVerifier;
            import greycos.solver.test.api.solver.change.MockProblemChangeDirector;
            import greycos.solver.core.preview.api.move.MoveTester;
            import greycos.solver.core.preview.api.neighborhood.NeighborhoodTester;

            public class Test {
                    ConstraintVerifier constraintVerifier;
                    MockProblemChangeDirector problemChangeDirector;
                    MoveTester moveTester;
                    NeighborhoodTester neighborhoodTester;
            }\
            """,
            """
            package greycos;

            import greycos.solver.core.api.score.stream.test.ConstraintVerifier;
            import greycos.solver.core.api.solver.change.MockProblemChangeDirector;
            import greycos.solver.core.preview.api.move.test.MoveTester;
            import greycos.solver.core.preview.api.neighborhood.test.NeighborhoodTester;

            public class Test {
                    ConstraintVerifier constraintVerifier;
                    MockProblemChangeDirector problemChangeDirector;
                    MoveTester moveTester;
                    NeighborhoodTester neighborhoodTester;
            }\
            """));
  }
}
