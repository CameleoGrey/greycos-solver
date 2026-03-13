package ai.greycos.solver.migration.v2;

import java.util.List;

import ai.greycos.solver.migration.AbstractRecipe;

import org.openrewrite.Recipe;
import org.openrewrite.java.ChangePackage;

public class GeneralPackageRenameMigrationRecipe extends AbstractRecipe {
  @Override
  public String getDisplayName() {
    return "Migrate legacy packages to the new structure";
  }

  @Override
  public String getDescription() {
    return "Migrate all legacy packages to the new structure.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        // Persistence API
        new ChangePackage(
            "ai.greycos.solver.persistence.common.api.domain.solution",
            "ai.greycos.solver.core.api.domain.solution",
            true),
        new ChangePackage(
            "ai.greycos.solver.jpa.api.score.buildin.bendablebigdecimal",
            "ai.greycos.solver.jpa.api.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.jpa.api.score.buildin.bendable",
            "ai.greycos.solver.jpa.api.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.jpa.api.score.buildin.hardmediumsoftbigdecimal",
            "ai.greycos.solver.jpa.api.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.jpa.api.score.buildin.hardmediumsoft",
            "ai.greycos.solver.jpa.api.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.jpa.api.score.buildin.hardsoftbigdecimal",
            "ai.greycos.solver.jpa.api.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.jpa.api.score.buildin.hardsoft",
            "ai.greycos.solver.jpa.api.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.jpa.api.score.buildin.simplebigdecimal",
            "ai.greycos.solver.jpa.api.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.jpa.api.score.buildin.simple",
            "ai.greycos.solver.jpa.api.score",
            true),
        // Jackson API
        new ChangePackage(
            "ai.greycos.solver.jackson.api.score.buildin.bendablebigdecimal",
            "ai.greycos.solver.jackson.api.score.buildin",
            true),
        new ChangePackage(
            "ai.greycos.solver.jackson.api.score.buildin.bendable",
            "ai.greycos.solver.jackson.api.score.buildin",
            true),
        new ChangePackage(
            "ai.greycos.solver.jackson.api.score.buildin.hardmediumsoftbigdecimal",
            "ai.greycos.solver.jackson.api.score.buildin",
            true),
        new ChangePackage(
            "ai.greycos.solver.jackson.api.score.buildin.hardmediumsoft",
            "ai.greycos.solver.jackson.api.score.buildin",
            true),
        new ChangePackage(
            "ai.greycos.solver.jackson.api.score.buildin.hardsoftbigdecimal",
            "ai.greycos.solver.jackson.api.score.buildin",
            true),
        new ChangePackage(
            "ai.greycos.solver.jackson.api.score.buildin.hardsoft",
            "ai.greycos.solver.jackson.api.score.buildin",
            true),
        new ChangePackage(
            "ai.greycos.solver.jackson.api.score.buildin.simplebigdecimal",
            "ai.greycos.solver.jackson.api.score.buildin",
            true),
        new ChangePackage(
            "ai.greycos.solver.jackson.api.score.buildin.simple",
            "ai.greycos.solver.jackson.api.score.buildin",
            true),
        // JAXB API
        new ChangePackage(
            "ai.greycos.solver.jaxb.api.score.buildin.bendablebigdecimal",
            "ai.greycos.solver.jaxb.api.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.jaxb.api.score.buildin.bendable",
            "ai.greycos.solver.jaxb.api.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.jaxb.api.score.buildin.hardmediumsoftbigdecimal",
            "ai.greycos.solver.jaxb.api.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.jaxb.api.score.buildin.hardmediumsoft",
            "ai.greycos.solver.jaxb.api.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.jaxb.api.score.buildin.hardsoftbigdecimal",
            "ai.greycos.solver.jaxb.api.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.jaxb.api.score.buildin.hardsoft",
            "ai.greycos.solver.jaxb.api.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.jaxb.api.score.buildin.simplebigdecimal",
            "ai.greycos.solver.jaxb.api.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.jaxb.api.score.buildin.simple",
            "ai.greycos.solver.jaxb.api.score",
            true),
        // Jackson Quarkus API
        new ChangePackage(
            "ai.greycos.solver.quarkus.jackson.score.buildin.bendablebigdecimal",
            "ai.greycos.solver.quarkus.jackson.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.quarkus.jackson.score.buildin.bendable",
            "ai.greycos.solver.quarkus.jackson.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.quarkus.jackson.score.buildin.hardmediumsoftbigdecimal",
            "ai.greycos.solver.quarkus.jackson.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.quarkus.jackson.score.buildin.hardmediumsoft",
            "ai.greycos.solver.quarkus.jackson.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.quarkus.jackson.score.buildin.hardsoftbigdecimal",
            "ai.greycos.solver.quarkus.jackson.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.quarkus.jackson.score.buildin.hardsoft",
            "ai.greycos.solver.quarkus.jackson.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.quarkus.jackson.score.buildin.simplebigdecimal",
            "ai.greycos.solver.quarkus.jackson.score",
            true),
        new ChangePackage(
            "ai.greycos.solver.quarkus.jackson.score.buildin.simple",
            "ai.greycos.solver.quarkus.jackson.score",
            true),
        // Value Range API
        new ChangePackage(
            "ai.greycos.solver.core.impl.domain.valuerange.buildin.bigdecimal",
            "ai.greycos.solver.core.impl.domain.valuerange",
            true),
        new ChangePackage(
            "ai.greycos.solver.core.impl.domain.valuerange.buildin.biginteger",
            "ai.greycos.solver.core.impl.domain.valuerange",
            true),
        new ChangePackage(
            "ai.greycos.solver.core.impl.domain.valuerange.buildin.primboolean",
            "ai.greycos.solver.core.impl.domain.valuerange",
            true),
        new ChangePackage(
            "ai.greycos.solver.core.impl.domain.valuerange.buildin.primint",
            "ai.greycos.solver.core.impl.domain.valuerange",
            true),
        new ChangePackage(
            "ai.greycos.solver.core.impl.domain.valuerange.buildin.collection",
            "ai.greycos.solver.core.impl.domain.valuerange",
            true),
        new ChangePackage(
            "ai.greycos.solver.core.impl.domain.valuerange.buildin.primlong",
            "ai.greycos.solver.core.impl.domain.valuerange",
            true),
        new ChangePackage(
            "ai.greycos.solver.core.impl.domain.valuerange.buildin.temporal",
            "ai.greycos.solver.core.impl.domain.valuerange",
            true),
        new ChangePackage(
            "ai.greycos.solver.core.impl.domain.valuerange.buildin",
            "ai.greycos.solver.core.impl.domain.valuerange",
            true));
  }
}
