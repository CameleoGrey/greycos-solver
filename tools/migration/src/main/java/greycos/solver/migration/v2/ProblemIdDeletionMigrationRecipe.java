package greycos.solver.migration.v2;

import java.util.List;

import greycos.solver.migration.AbstractRecipe;
import greycos.solver.migration.common.RemoveGenericTypeFromMethodRecipe;
import greycos.solver.migration.common.RemoveGenericTypeRecipe;

import org.openrewrite.Recipe;

public class ProblemIdDeletionMigrationRecipe extends AbstractRecipe {
  @Override
  public String getDisplayName() {
    return "Remove the ProblemId generic type";
  }

  @Override
  public String getDescription() {
    return "Remove the ProblemId generic type.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        // Persistence common
        new RemoveGenericTypeRecipe("greycos.solver.core.api.solver.SolverManager", 1),
        new RemoveGenericTypeFromMethodRecipe(
            "greycos.solver.core.api.solver.SolverManager create(..)", 1),
        new RemoveGenericTypeFromMethodRecipe(
            "greycos.solver.core.api.solver.SolverManager solveBuilder()", 1),
        new RemoveGenericTypeFromMethodRecipe(
            "greycos.solver.core.api.solver.SolverManager solve(..)", 1),
        new RemoveGenericTypeFromMethodRecipe(
            "greycos.solver.core.api.solver.SolverManager solveAndListen(..)", 1),
        new RemoveGenericTypeRecipe("greycos.solver.core.api.solver.SolverJobBuilder", 1),
        new RemoveGenericTypeRecipe("greycos.solver.core.api.solver.SolverJob", 1));
  }
}
