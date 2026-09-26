package greycos.solver.migration.preview;

import java.util.List;

import greycos.solver.migration.AbstractRecipe;

import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeType;

public class NeighborhoodsMigrationRecipe extends AbstractRecipe {
  @Override
  public String getDisplayName() {
    return "Migrate the Neighborhoods preview API";
  }

  @Override
  public String getDescription() {
    return "Migrate the Neighborhoods preview API to its new class structure.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        // Sampling streams renamed to picking streams
        new ChangeType(
            "greycos.solver.core.preview.api.neighborhood.stream.sampling.SamplingStream",
            "greycos.solver.core.preview.api.neighborhood.stream.picking.PickingStream",
            true),
        new ChangeType(
            "greycos.solver.core.preview.api.neighborhood.stream.sampling.UniSamplingStream",
            "greycos.solver.core.preview.api.neighborhood.stream.picking.UniPickingStream",
            true),
        new ChangeType(
            "greycos.solver.core.preview.api.neighborhood.stream.sampling.BiSamplingStream",
            "greycos.solver.core.preview.api.neighborhood.stream.picking.BiPickingStream",
            true),
        new ChangeType(
            "greycos.solver.core.impl.neighborhood.stream.sampling.InnerSamplingStream",
            "greycos.solver.core.impl.neighborhood.stream.picking.InnerPickingStream",
            true),
        new ChangeType(
            "greycos.solver.core.impl.neighborhood.stream.sampling.InnerUniSamplingStream",
            "greycos.solver.core.impl.neighborhood.stream.picking.InnerUniPickingStream",
            true),
        new ChangeType(
            "greycos.solver.core.impl.neighborhood.stream.sampling.DefaultUniSamplingStream",
            "greycos.solver.core.impl.neighborhood.stream.picking.DefaultUniPickingStream",
            true),
        new ChangeType(
            "greycos.solver.core.impl.neighborhood.stream.sampling.DefaultBiSamplingStream",
            "greycos.solver.core.impl.neighborhood.stream.picking.DefaultBiPickingStream",
            true));
  }
}
