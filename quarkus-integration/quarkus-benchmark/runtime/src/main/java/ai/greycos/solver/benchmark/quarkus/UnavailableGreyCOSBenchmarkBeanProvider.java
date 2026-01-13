package ai.greycos.solver.benchmark.quarkus;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import ai.greycos.solver.benchmark.api.PlannerBenchmarkFactory;
import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;

import io.quarkus.arc.DefaultBean;

public class UnavailableGreyCOSBenchmarkBeanProvider {

  @DefaultBean
  @Singleton
  @Produces
  PlannerBenchmarkFactory benchmarkFactory() {
    throw new IllegalStateException(
        "The "
            + PlannerBenchmarkFactory.class.getName()
            + " is not available as there are no @"
            + PlanningSolution.class.getSimpleName()
            + " or @"
            + PlanningEntity.class.getSimpleName()
            + " annotated classes."
            + "\nIf your cotwin classes are located in a dependency of this project, maybe try generating"
            + " the Jandex index by using the jandex-maven-plugin in that dependency, or by adding"
            + "application.properties entries (quarkus.index-dependency.<name>.group-id"
            + " and quarkus.index-dependency.<name>.artifact-id).");
  }
}
