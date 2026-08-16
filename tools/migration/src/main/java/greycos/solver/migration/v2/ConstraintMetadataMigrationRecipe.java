package greycos.solver.migration.v2;

import java.util.List;

import greycos.solver.migration.AbstractRecipe;

import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.DeleteMethodArgument;
import org.openrewrite.java.RemoveMethodInvocations;

public final class ConstraintMetadataMigrationRecipe extends AbstractRecipe {

  @Override
  public String getDisplayName() {
    return "Migrate constraint description and group APIs";
  }

  @Override
  public String getDescription() {
    return "Renames asConstraintDescribed to asConstraint, updates constraint identity accessors, and removes deleted group and description methods.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        new ChangeMethodName(
            "greycos.solver.core.api.score.stream.ConstraintBuilder asConstraintDescribed(String, String)",
            "asConstraint",
            true,
            false),
        new DeleteMethodArgument(
            "greycos.solver.core.api.score.stream.ConstraintBuilder asConstraint(String, String)",
            1),
        new ChangeMethodName(
            "greycos.solver.core.api.score.stream.ConstraintBuilder asConstraintDescribed(String, String, String)",
            "asConstraint",
            true,
            false),
        new DeleteMethodArgument(
            "greycos.solver.core.api.score.stream.ConstraintBuilder asConstraint(String, String, String)",
            2),
        new DeleteMethodArgument(
            "greycos.solver.core.api.score.stream.ConstraintBuilder asConstraint(String, String)",
            1),
        new ChangeMethodName(
            "greycos.solver.core.api.score.stream.ConstraintRef constraintName()",
            "id",
            true,
            false),
        new ChangeMethodName(
            "greycos.solver.core.api.score.analysis.ConstraintAnalysis constraintName()",
            "constraintId",
            true,
            false),
        new RemoveMethodInvocations(
            "greycos.solver.core.api.score.stream.Constraint getConstraintGroup()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.api.score.stream.Constraint getDescription()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.api.score.stream.ConstraintMetaModel getConstraintsPerGroup(..)"),
        new RemoveMethodInvocations(
            "greycos.solver.core.api.score.stream.ConstraintMetaModel getConstraintGroups()"));
  }
}
