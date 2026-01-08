package ai.greycos.solver.quarkus.jackson.deployment;

import ai.greycos.solver.jackson.api.GreyCOSJacksonModule;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.jackson.spi.ClassPathJacksonModuleBuildItem;

class GreyCOSJacksonProcessor {

  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem("greycos-solver-jackson");
  }

  @BuildStep
  ClassPathJacksonModuleBuildItem registerGreyCOSJacksonModule() {
    // Make greycos-solver-jackson discoverable by quarkus-rest
    // https://quarkus.io/guides/rest-migration#service-loading
    return new ClassPathJacksonModuleBuildItem(GreyCOSJacksonModule.class.getName());
  }
}
