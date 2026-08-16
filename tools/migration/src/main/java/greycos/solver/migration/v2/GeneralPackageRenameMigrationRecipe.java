package greycos.solver.migration.v2;

import java.util.List;

import greycos.solver.migration.AbstractRecipe;

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
            "greycos.solver.persistence.common.api.domain.solution",
            "greycos.solver.core.api.cotwin.solution",
            true),
        new ChangePackage(
            "greycos.solver.jpa.api.score.buildin.bendablebigdecimal",
            "greycos.solver.jpa.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jpa.api.score.buildin.bendable", "greycos.solver.jpa.api.score", true),
        new ChangePackage(
            "greycos.solver.jpa.api.score.buildin.hardmediumsoftbigdecimal",
            "greycos.solver.jpa.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jpa.api.score.buildin.hardmediumsoft",
            "greycos.solver.jpa.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jpa.api.score.buildin.hardsoftbigdecimal",
            "greycos.solver.jpa.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jpa.api.score.buildin.hardsoft", "greycos.solver.jpa.api.score", true),
        new ChangePackage(
            "greycos.solver.jpa.api.score.buildin.simplebigdecimal",
            "greycos.solver.jpa.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jpa.api.score.buildin.simple", "greycos.solver.jpa.api.score", true),
        // Jackson API
        new ChangePackage(
            "greycos.solver.jackson.api.score.buildin.bendablebigdecimal",
            "greycos.solver.jackson.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jackson.api.score.buildin.bendable",
            "greycos.solver.jackson.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jackson.api.score.buildin.hardmediumsoftbigdecimal",
            "greycos.solver.jackson.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jackson.api.score.buildin.hardmediumsoft",
            "greycos.solver.jackson.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jackson.api.score.buildin.hardsoftbigdecimal",
            "greycos.solver.jackson.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jackson.api.score.buildin.hardsoft",
            "greycos.solver.jackson.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jackson.api.score.buildin.simplebigdecimal",
            "greycos.solver.jackson.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jackson.api.score.buildin.simple",
            "greycos.solver.jackson.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jackson.api.score.buildin", "greycos.solver.jackson.api.score", true),
        // JAXB API
        new ChangePackage(
            "greycos.solver.jaxb.api.score.buildin.bendablebigdecimal",
            "greycos.solver.jaxb.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jaxb.api.score.buildin.bendable",
            "greycos.solver.jaxb.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jaxb.api.score.buildin.hardmediumsoftbigdecimal",
            "greycos.solver.jaxb.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jaxb.api.score.buildin.hardmediumsoft",
            "greycos.solver.jaxb.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jaxb.api.score.buildin.hardsoftbigdecimal",
            "greycos.solver.jaxb.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jaxb.api.score.buildin.hardsoft",
            "greycos.solver.jaxb.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jaxb.api.score.buildin.simplebigdecimal",
            "greycos.solver.jaxb.api.score",
            true),
        new ChangePackage(
            "greycos.solver.jaxb.api.score.buildin.simple", "greycos.solver.jaxb.api.score", true),
        // Jackson Quarkus API
        new ChangePackage(
            "greycos.solver.quarkus.jackson.score.buildin.bendablebigdecimal",
            "greycos.solver.quarkus.jackson.score",
            true),
        new ChangePackage(
            "greycos.solver.quarkus.jackson.score.buildin.bendable",
            "greycos.solver.quarkus.jackson.score",
            true),
        new ChangePackage(
            "greycos.solver.quarkus.jackson.score.buildin.hardmediumsoftbigdecimal",
            "greycos.solver.quarkus.jackson.score",
            true),
        new ChangePackage(
            "greycos.solver.quarkus.jackson.score.buildin.hardmediumsoft",
            "greycos.solver.quarkus.jackson.score",
            true),
        new ChangePackage(
            "greycos.solver.quarkus.jackson.score.buildin.hardsoftbigdecimal",
            "greycos.solver.quarkus.jackson.score",
            true),
        new ChangePackage(
            "greycos.solver.quarkus.jackson.score.buildin.hardsoft",
            "greycos.solver.quarkus.jackson.score",
            true),
        new ChangePackage(
            "greycos.solver.quarkus.jackson.score.buildin.simplebigdecimal",
            "greycos.solver.quarkus.jackson.score",
            true),
        new ChangePackage(
            "greycos.solver.quarkus.jackson.score.buildin.simple",
            "greycos.solver.quarkus.jackson.score",
            true),
        new ChangePackage(
            "greycos.solver.quarkus.jackson.score.buildin",
            "greycos.solver.quarkus.jackson.score",
            true),
        // Value Range API
        new ChangePackage(
            "greycos.solver.core.impl.domain.valuerange.buildin.bigdecimal",
            "greycos.solver.core.impl.cotwin.valuerange",
            true),
        new ChangePackage(
            "greycos.solver.core.impl.domain.valuerange.buildin.biginteger",
            "greycos.solver.core.impl.cotwin.valuerange",
            true),
        new ChangePackage(
            "greycos.solver.core.impl.domain.valuerange.buildin.primboolean",
            "greycos.solver.core.impl.cotwin.valuerange",
            true),
        new ChangePackage(
            "greycos.solver.core.impl.domain.valuerange.buildin.primint",
            "greycos.solver.core.impl.cotwin.valuerange",
            true),
        new ChangePackage(
            "greycos.solver.core.impl.domain.valuerange.buildin.collection",
            "greycos.solver.core.impl.cotwin.valuerange",
            true),
        new ChangePackage(
            "greycos.solver.core.impl.domain.valuerange.buildin.primlong",
            "greycos.solver.core.impl.cotwin.valuerange",
            true),
        new ChangePackage(
            "greycos.solver.core.impl.domain.valuerange.buildin.temporal",
            "greycos.solver.core.impl.cotwin.valuerange",
            true),
        new ChangePackage(
            "greycos.solver.core.impl.domain.valuerange.buildin",
            "greycos.solver.core.impl.cotwin.valuerange",
            true));
  }
}
