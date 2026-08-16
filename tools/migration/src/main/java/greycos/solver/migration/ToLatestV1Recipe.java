package greycos.solver.migration;

import java.util.List;

import greycos.solver.migration.v1.AsConstraintRecipe;
import greycos.solver.migration.v1.ConstraintRefRecipe;
import greycos.solver.migration.v1.NullableRecipe;
import greycos.solver.migration.v1.RemoveConstraintPackageRecipe;
import greycos.solver.migration.v1.ScoreGettersRecipe;
import greycos.solver.migration.v1.ScoreManagerMethodsRecipe;
import greycos.solver.migration.v1.SingleConstraintAssertionMethodsRecipe;
import greycos.solver.migration.v1.SolutionManagerRecommendAssignmentRecipe;
import greycos.solver.migration.v1.SolverManagerBuilderRecipe;
import greycos.solver.migration.v1.SortingMigrationRecipe;

import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.properties.ChangePropertyKey;

public final class ToLatestV1Recipe extends AbstractRecipe {

  @Override
  public String getName() {
    return "greycos.solver.migration.ToLatestV1";
  }

  @Override
  public String getDisplayName() {
    return "Upgrade to the latest GreyCOS Solver 1.x";
  }

  @Override
  public String getDescription() {
    return "Replace calls to deleted or deprecated GreyCOS Solver 1.x types and methods with their current alternatives.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        new ChangePropertyKey(
            "greycos.solver.solve-length", "greycos.solver.solve.duration", null, null),
        new ChangePropertyKey(
            "quarkus.greycos.solver.solve-length",
            "quarkus.greycos.solver.solve.duration",
            null,
            null),
        new ChangeMethodName(
            "greycos.solver.core.api.score.stream.ConstraintFactory from(Class)",
            "forEach",
            true,
            false),
        new ChangeMethodName(
            "greycos.solver.core.api.score.stream.ConstraintFactory fromUnfiltered(Class)",
            "forEachIncludingUnassigned",
            true,
            false),
        new ChangeMethodName(
            "greycos.solver.core.api.score.stream.ConstraintFactory fromUniquePair(..)",
            "forEachUniquePair",
            true,
            false),
        new ScoreManagerMethodsRecipe(),
        new ChangeType(
            "greycos.solver.core.api.score.ScoreManager",
            "greycos.solver.core.api.solver.SolutionManager",
            true),
        new ScoreGettersRecipe(),
        new ConstraintRefRecipe(),
        new SolverManagerBuilderRecipe(),
        new NullableRecipe(),
        new SingleConstraintAssertionMethodsRecipe(),
        new AsConstraintRecipe(),
        new RemoveConstraintPackageRecipe(),
        new SolutionManagerRecommendAssignmentRecipe(),
        new SortingMigrationRecipe());
  }
}
