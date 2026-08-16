package greycos.solver.migration.v2;

import java.util.List;

import greycos.solver.migration.AbstractRecipe;

import org.openrewrite.Recipe;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.ChangeType;
import org.openrewrite.maven.RemoveDependency;

public class TestingAPIsMigrationRecipe extends AbstractRecipe {

  @Override
  public String getDisplayName() {
    return "Migrate testing APIs to their new packages";
  }

  @Override
  public String getDescription() {
    return getDisplayName() + ".";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        new ChangePackage(
            "greycos.solver.test.api.score.stream",
            "greycos.solver.core.api.score.stream.test",
            true),
        new ChangePackage(
            "greycos.solver.test.api.solver.change", "greycos.solver.core.api.solver.change", true),
        new ChangeType(
            "greycos.solver.core.preview.api.move.MoveTester",
            "greycos.solver.core.preview.api.move.test.MoveTester",
            true),
        new ChangeType(
            "greycos.solver.core.preview.api.move.MoveTestContext",
            "greycos.solver.core.preview.api.move.test.MoveTestContext",
            true),
        new ChangeType(
            "greycos.solver.core.preview.api.neighborhood.NeighborhoodTester",
            "greycos.solver.core.preview.api.neighborhood.test.NeighborhoodTester",
            true),
        new ChangeType(
            "greycos.solver.core.preview.api.neighborhood.NeighborhoodTestContext",
            "greycos.solver.core.preview.api.neighborhood.test.NeighborhoodTestContext",
            true),
        new RemoveDependency("greycos.solver", "greycos-solver-test", null));
  }
}
