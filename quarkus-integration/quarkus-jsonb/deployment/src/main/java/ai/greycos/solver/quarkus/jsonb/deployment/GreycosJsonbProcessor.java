package ai.greycos.solver.quarkus.jsonb.deployment;

import ai.greycos.solver.quarkus.jsonb.GreycosJsonbConfigCustomizer;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * @deprecated Prefer Jackson integration instead.
 */
@Deprecated(forRemoval = true, since = "1.4.0")
class GreycosJsonbProcessor {

  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem("greycos-solver-jsonb");
  }

  @BuildStep
  void registerGreycosJsonbConfig(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
    additionalBeans.produce(new AdditionalBeanBuildItem(GreycosJsonbConfigCustomizer.class));
  }
}
