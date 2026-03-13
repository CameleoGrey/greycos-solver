package ai.greycos.solver.migration.v2;

import java.util.List;

import ai.greycos.solver.migration.AbstractRecipe;

import org.openrewrite.Recipe;
import org.openrewrite.maven.RemoveDependency;

public class GeneralDependencyDeleteMigrationRecipe extends AbstractRecipe {
  @Override
  public String getDisplayName() {
    return "Remove dependencies that no longer exist";
  }

  @Override
  public String getDescription() {
    return "Remove dependencies that no longer exist.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        new RemoveDependency("ai.greycos.solver", "greycos-solver-persistence-common", null),
        new RemoveDependency("ai.greycos.solver", "greycos-solver-webui", null),
        new RemoveDependency("ai.greycos.solver", "greycos-solver-jsonb", null));
  }
}
