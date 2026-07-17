package ai.greycos.solver.migration;

import java.util.List;

import ai.greycos.solver.migration.v2.ConstraintArgRemovalMigrationRecipe;
import ai.greycos.solver.migration.v2.ConstraintMetadataMigrationRecipe;
import ai.greycos.solver.migration.v2.GeneralDependencyDeleteMigrationRecipe;
import ai.greycos.solver.migration.v2.GeneralMethodChangeNameMigrationRecipe;
import ai.greycos.solver.migration.v2.GeneralMethodDeleteInvocationMigrationRecipe;
import ai.greycos.solver.migration.v2.GeneralPackageRenameMigrationRecipe;
import ai.greycos.solver.migration.v2.GeneralTypeChangeMigrationRecipe;
import ai.greycos.solver.migration.v2.PlanningSolutionAnnotationCleanupMigrationRecipe;
import ai.greycos.solver.migration.v2.ProblemIdDeletionMigrationRecipe;
import ai.greycos.solver.migration.v2.SolverConfigOverrideSolutionDeletionMigrationRecipe;
import ai.greycos.solver.migration.v2.TestingAPIsMigrationRecipe;

import org.openrewrite.Recipe;
import org.openrewrite.java.RemoveUnusedImports;

public final class ToLatestRecipe extends AbstractRecipe {

  @Override
  public String getName() {
    return "ai.greycos.solver.migration.ToLatest";
  }

  @Override
  public String getDisplayName() {
    return "Upgrade to the latest GreyCOS Solver";
  }

  @Override
  public String getDescription() {
    return "Replace calls to deleted or deprecated GreyCOS Solver types and methods with their current alternatives.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        new ToLatestV1Recipe(),
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
