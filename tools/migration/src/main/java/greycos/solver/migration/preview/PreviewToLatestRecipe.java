package greycos.solver.migration.preview;

import java.util.List;

import greycos.solver.migration.AbstractRecipe;

import org.openrewrite.Recipe;

public final class PreviewToLatestRecipe extends AbstractRecipe {

  @Override
  public String getName() {
    return "greycos.solver.migration.PreviewToLatest";
  }

  @Override
  public String getDisplayName() {
    return "Upgrade to the latest GreyCOS Solver preview APIs";
  }

  @Override
  public String getDescription() {
    return "Replace all your calls to renamed/removed preview API types and methods of GreyCOS"
        + " Solver with their proper alternatives.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(new NeighborhoodsMigrationRecipe());
  }
}
