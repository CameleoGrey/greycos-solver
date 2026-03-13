package ai.greycos.solver.migration.v2;

import java.util.List;

import ai.greycos.solver.migration.AbstractRecipe;

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
            "ai.greycos.solver.core.api.domain.lookup.PlanningId",
            "ai.greycos.solver.core.api.domain.common.PlanningId",
            true),
        // Score API
        new ChangeType(
            "ai.greycos.solver.core.api.score.director.ScoreDirector",
            "ai.greycos.solver.core.impl.score.director.ScoreDirector",
            true),
        new ChangeType(
            "ai.greycos.solver.core.api.score.buildin.simple.SimpleScore",
            "ai.greycos.solver.core.api.score.SimpleScore",
            true),
        new ChangeType(
            "ai.greycos.solver.core.api.score.buildin.simplelong.SimpleLongScore",
            "ai.greycos.solver.core.api.score.SimpleScore",
            true),
        new ChangeType(
            "ai.greycos.solver.core.api.score.buildin.simplebigdecimal.SimpleBigDecimalScore",
            "ai.greycos.solver.core.api.score.SimpleBigDecimalScore",
            true),
        new ChangeType(
            "ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore",
            "ai.greycos.solver.core.api.score.HardSoftScore",
            true),
        new ChangeType(
            "ai.greycos.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore",
            "ai.greycos.solver.core.api.score.HardSoftScore",
            true),
        new ChangeType(
            "ai.greycos.solver.core.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScore",
            "ai.greycos.solver.core.api.score.HardSoftBigDecimalScore",
            true),
        new ChangeType(
            "ai.greycos.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore",
            "ai.greycos.solver.core.api.score.HardMediumSoftScore",
            true),
        new ChangeType(
            "ai.greycos.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore",
            "ai.greycos.solver.core.api.score.HardMediumSoftScore",
            true),
        new ChangeType(
            "ai.greycos.solver.core.api.score.buildin.hardmediumsoftbigdecimal.HardMediumSoftBigDecimalScore",
            "ai.greycos.solver.core.api.score.HardMediumSoftBigDecimalScore",
            true),
        new ChangeType(
            "ai.greycos.solver.core.api.score.buildin.bendable.BendableScore",
            "ai.greycos.solver.core.api.score.BendableScore",
            true),
        new ChangeType(
            "ai.greycos.solver.core.api.score.buildin.bendablelong.BendableLongScore",
            "ai.greycos.solver.core.api.score.BendableScore",
            true),
        new ChangeType(
            "ai.greycos.solver.core.api.score.buildin.bendablebigdecimal.BendableBigDecimalScore",
            "ai.greycos.solver.core.api.score.BendableBigDecimalScore",
            true),
        // Problem fact
        new ChangeType(
            "ai.greycos.solver.core.api.solver.ProblemFactChange",
            "ai.greycos.solver.core.api.solver.change.ProblemChange",
            true),
        // Value Range
        new ChangeType(
            "ai.greycos.solver.core.api.domain.valuerange.CountableValueRange",
            "ai.greycos.solver.core.api.domain.valuerange.ValueRange",
            true),
        new ChangeType(
            "ai.greycos.solver.core.impl.domain.valuerange.buildin.composite.CompositeCountableValueRange",
            "ai.greycos.solver.core.impl.domain.valuerange.CompositeValueRange",
            true),
        new ChangeType(
            "ai.greycos.solver.core.impl.domain.valuerange.buildin.composite.NullAllowingCountableValueRange",
            "ai.greycos.solver.core.impl.domain.valuerange.NullAllowingValueRange",
            true));
  }
}
