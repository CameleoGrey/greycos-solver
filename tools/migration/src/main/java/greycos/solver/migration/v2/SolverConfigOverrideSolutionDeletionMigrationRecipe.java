package greycos.solver.migration.v2;

import java.util.List;

import greycos.solver.migration.AbstractRecipe;
import greycos.solver.migration.common.RemoveGenericTypeRecipe;

import org.openrewrite.Recipe;

public final class SolverConfigOverrideSolutionDeletionMigrationRecipe extends AbstractRecipe {

  @Override
  public String getDisplayName() {
    return "Remove the solution generic type from SolverConfigOverride";
  }

  @Override
  public String getDescription() {
    return getDisplayName() + ".";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        new RemoveGenericTypeRecipe("greycos.solver.core.api.solver.SolverConfigOverride", 0));
  }
}
