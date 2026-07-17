package ai.greycos.solver.migration.v2;

import java.util.List;

import ai.greycos.solver.migration.AbstractRecipe;

import org.openrewrite.Recipe;
import org.openrewrite.java.RemoveAnnotationAttribute;

public final class PlanningSolutionAnnotationCleanupMigrationRecipe extends AbstractRecipe {

  @Override
  public String getDisplayName() {
    return "Remove deprecated attributes from @PlanningSolution";
  }

  @Override
  public String getDescription() {
    return "Removes the deprecated lookUpStrategyType and autoDiscoverMemberType attributes from @PlanningSolution.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        new RemoveAnnotationAttribute(
            "ai.greycos.solver.core.api.cotwin.solution.PlanningSolution", "lookUpStrategyType"),
        new RemoveAnnotationAttribute(
            "ai.greycos.solver.core.api.cotwin.solution.PlanningSolution",
            "autoDiscoverMemberType"));
  }
}
