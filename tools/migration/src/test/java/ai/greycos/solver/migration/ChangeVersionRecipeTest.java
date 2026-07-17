package ai.greycos.solver.migration;

import static org.openrewrite.maven.Assertions.pomXml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

@Execution(ExecutionMode.CONCURRENT)
class ChangeVersionRecipeTest implements RewriteTest {

  private final ChangeVersionRecipe recipe = new ChangeVersionRecipe();

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(recipe);
  }

  @Test
  void changesKnownGreycosVersionProperties() {
    rewriteRun(
        pomXml(
            """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>example</artifactId>
              <version>1.0</version>
              <properties>
                <version.ai.greycos.solver>1.0.0</version.ai.greycos.solver>
                <greycosVersion>1.0.0</greycosVersion>
              </properties>
            </project>""",
            """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>example</artifactId>
              <version>1.0</version>
              <properties>
                <version.ai.greycos.solver>%s</version.ai.greycos.solver>
                <greycosVersion>%s</greycosVersion>
              </properties>
            </project>"""
                .formatted(recipe.version, recipe.version)));
  }

  @Test
  void leavesUnrelatedPropertiesUntouched() {
    rewriteRun(
        pomXml(
            """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>example</artifactId>
              <version>1.0</version>
              <properties>
                <some.other.version>1.0.0</some.other.version>
              </properties>
            </project>"""));
  }
}
