package ai.greycos.solver.quarkus.jackson.deployment;

import ai.greycos.solver.jackson.api.GreycosJacksonModule;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.jackson.spi.ClassPathJacksonModuleBuildItem;

class GreycosJacksonProcessor {

  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem("greycos-solver-jackson");
  }

  @BuildStep
  ClassPathJacksonModuleBuildItem registerGreycosJacksonModule() {
    // Make greycos-solver-jackson discoverable by quarkus-rest
    // https://quarkus.io/guides/rest-migration#service-loading
    return new ClassPathJacksonModuleBuildItem(GreycosJacksonModule.class.getName());
  }
}
