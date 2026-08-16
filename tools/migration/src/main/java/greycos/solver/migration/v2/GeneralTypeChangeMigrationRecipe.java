package greycos.solver.migration.v2;

import java.util.List;

import greycos.solver.migration.AbstractRecipe;

import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeType;

public class GeneralTypeChangeMigrationRecipe extends AbstractRecipe {
  @Override
  public String getDisplayName() {
    return "Migrate legacy code to the new class structure";
  }

  @Override
  public String getDescription() {
    return "Migrate all legacy classes to the new class structure.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        // Planning Id
        new ChangeType(
            "greycos.solver.core.api.domain.lookup.PlanningId",
            "greycos.solver.core.api.cotwin.common.PlanningId",
            true),
        // Score API
        new ChangeType(
            "greycos.solver.core.api.score.director.ScoreDirector",
            "greycos.solver.core.impl.score.director.ScoreDirector",
            true),
        new ChangeType(
            "greycos.solver.core.api.score.buildin.simple.SimpleScore",
            "greycos.solver.core.api.score.SimpleScore",
            true),
        new ChangeType(
            "greycos.solver.core.api.score.buildin.simplelong.SimpleLongScore",
            "greycos.solver.core.api.score.SimpleScore",
            true),
        new ChangeType(
            "greycos.solver.core.api.score.buildin.simplebigdecimal.SimpleBigDecimalScore",
            "greycos.solver.core.api.score.SimpleBigDecimalScore",
            true),
        new ChangeType(
            "greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore",
            "greycos.solver.core.api.score.HardSoftScore",
            true),
        new ChangeType(
            "greycos.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore",
            "greycos.solver.core.api.score.HardSoftScore",
            true),
        new ChangeType(
            "greycos.solver.core.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScore",
            "greycos.solver.core.api.score.HardSoftBigDecimalScore",
            true),
        new ChangeType(
            "greycos.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore",
            "greycos.solver.core.api.score.HardMediumSoftScore",
            true),
        new ChangeType(
            "greycos.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore",
            "greycos.solver.core.api.score.HardMediumSoftScore",
            true),
        new ChangeType(
            "greycos.solver.core.api.score.buildin.hardmediumsoftbigdecimal.HardMediumSoftBigDecimalScore",
            "greycos.solver.core.api.score.HardMediumSoftBigDecimalScore",
            true),
        new ChangeType(
            "greycos.solver.core.api.score.buildin.bendable.BendableScore",
            "greycos.solver.core.api.score.BendableScore",
            true),
        new ChangeType(
            "greycos.solver.core.api.score.buildin.bendablelong.BendableLongScore",
            "greycos.solver.core.api.score.BendableScore",
            true),
        new ChangeType(
            "greycos.solver.core.api.score.buildin.bendablebigdecimal.BendableBigDecimalScore",
            "greycos.solver.core.api.score.BendableBigDecimalScore",
            true),
        // Constraint identity
        new ChangeType(
            "greycos.solver.core.api.score.constraint.ConstraintRef",
            "greycos.solver.core.api.score.stream.ConstraintRef",
            true),
        // Problem fact
        new ChangeType(
            "greycos.solver.core.api.solver.ProblemFactChange",
            "greycos.solver.core.api.solver.change.ProblemChange",
            true),
        // Value Range
        new ChangeType(
            "greycos.solver.core.api.domain.valuerange.CountableValueRange",
            "greycos.solver.core.api.cotwin.valuerange.ValueRange",
            true),
        new ChangeType(
            "greycos.solver.core.impl.domain.valuerange.buildin.composite.CompositeCountableValueRange",
            "greycos.solver.core.impl.cotwin.valuerange.CompositeValueRange",
            true),
        new ChangeType(
            "greycos.solver.core.impl.domain.valuerange.buildin.composite.NullAllowingCountableValueRange",
            "greycos.solver.core.impl.cotwin.valuerange.NullAllowingValueRange",
            true));
  }
}
