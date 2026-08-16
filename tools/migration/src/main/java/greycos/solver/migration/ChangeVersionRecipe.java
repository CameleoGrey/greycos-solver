package greycos.solver.migration;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.openrewrite.Recipe;
import org.openrewrite.maven.ChangePropertyValue;

public final class ChangeVersionRecipe extends AbstractRecipe {

  final String version;

  public ChangeVersionRecipe() {
    try (var inputStream =
        ChangeVersionRecipe.class.getResourceAsStream(
            "rewrite-greycos-solver-version.properties")) {
      if (inputStream == null) {
        throw new IllegalStateException(
            "The rewrite-greycos-solver-version.properties resource is missing.");
      }
      var properties = new Properties();
      properties.load(inputStream);
      version = properties.getProperty("version");
      if (version == null || version.isBlank()) {
        throw new IllegalStateException(
            "The rewrite-greycos-solver-version.properties resource has no version.");
      }
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to load rewrite-greycos-solver-version.properties.", e);
    }
  }

  @Override
  public String getName() {
    return "greycos.solver.migration.ChangeVersion";
  }

  @Override
  public String getDisplayName() {
    return "Change the GreyCOS Solver version";
  }

  @Override
  public String getDescription() {
    return "Replaces the configured GreyCOS Solver version.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        new ChangePropertyValue("version.greycos.solver", version, false, true),
        new ChangePropertyValue("version.greycos", version, false, true),
        new ChangePropertyValue("greycos.solver.version", version, false, true),
        new ChangePropertyValue("greycos.version", version, false, true),
        new ChangePropertyValue("greycosVersion", version, false, true));
  }
}
