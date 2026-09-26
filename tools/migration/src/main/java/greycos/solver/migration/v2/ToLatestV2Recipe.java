package greycos.solver.migration.v2;

import java.util.List;

import greycos.solver.migration.AbstractRecipe;
import greycos.solver.migration.ChangeVersionRecipe;

import org.openrewrite.Recipe;
import org.openrewrite.java.RemoveUnusedImports;

public final class ToLatestV2Recipe extends AbstractRecipe {

  @Override
  public String getName() {
    return "greycos.solver.migration.ToLatestV2";
  }

  @Override
  public String getDisplayName() {
    return "Upgrade to the latest GreyCOS Solver 2.x";
  }

  @Override
  public String getDescription() {
    return "Replace all your calls to deleted/deprecated types and methods of GreyCOS Solver with"
        + " their proper alternatives.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        new ChangeVersionRecipe(),
        new ConstraintArgRemovalMigrationRecipe(),
        new ConstraintMetadataMigrationRecipe(),
        new PlanningSolutionAnnotationCleanupMigrationRecipe(),
        new GeneralMethodDeleteInvocationMigrationRecipe(),
        new GeneralMethodChangeNameMigrationRecipe(),
        new GeneralTypeChangeMigrationRecipe(),
        new ProblemIdDeletionMigrationRecipe(),
        new TestingAPIsMigrationRecipe(),
        new GeneralDependencyDeleteMigrationRecipe(),
        new GeneralPackageRenameMigrationRecipe(),
        new SolverConfigOverrideSolutionDeletionMigrationRecipe(),
        new RemoveUnusedImports());
  }
}
