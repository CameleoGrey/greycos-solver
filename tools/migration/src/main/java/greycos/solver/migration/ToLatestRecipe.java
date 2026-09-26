package greycos.solver.migration;

import java.util.List;

import greycos.solver.migration.preview.PreviewToLatestRecipe;
import greycos.solver.migration.v1.ToLatestV1Recipe;
import greycos.solver.migration.v2.ToLatestV2Recipe;

import org.openrewrite.Recipe;
import org.openrewrite.java.RemoveUnusedImports;

public final class ToLatestRecipe extends AbstractRecipe {

  @Override
  public String getName() {
    return "greycos.solver.migration.ToLatest";
  }

  @Override
  public String getDisplayName() {
    return "Upgrade to the latest GreyCOS Solver";
  }

  @Override
  public String getDescription() {
    return "Replace calls to deleted or deprecated GreyCOS Solver types and methods with their"
        + " current alternatives.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        new ToLatestV1Recipe(),
        new ToLatestV2Recipe(),
        new PreviewToLatestRecipe(),
        new RemoveUnusedImports());
  }
}
