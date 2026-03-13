package ai.greycos.solver.migration.v2;

import java.util.List;

import ai.greycos.solver.migration.AbstractRecipe;

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
            "ai.greycos.solver.test.api.score.stream",
            "ai.greycos.solver.core.api.score.stream.test",
            true),
        new ChangePackage(
            "ai.greycos.solver.test.api.solver.change",
            "ai.greycos.solver.core.api.solver.change",
            true),
        new ChangeType(
            "ai.greycos.solver.core.preview.api.move.MoveTester",
            "ai.greycos.solver.core.preview.api.move.test.MoveTester",
            true),
        new ChangeType(
            "ai.greycos.solver.core.preview.api.move.MoveTestContext",
            "ai.greycos.solver.core.preview.api.move.test.MoveTestContext",
            true),
        new ChangeType(
            "ai.greycos.solver.core.preview.api.neighborhood.NeighborhoodTester",
            "ai.greycos.solver.core.preview.api.neighborhood.test.NeighborhoodTester",
            true),
        new ChangeType(
            "ai.greycos.solver.core.preview.api.neighborhood.NeighborhoodTestContext",
            "ai.greycos.solver.core.preview.api.neighborhood.test.NeighborhoodTestContext",
            true),
        new RemoveDependency("ai.greycos.solver", "greycos-solver-test", null));
  }
}
