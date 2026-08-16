package greycos.solver.quarkus.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GreyCOSProcessorTest {

  @Test
  void indexesCoreArtifactFromPublishedNamespace() {
    var indexDependency = new GreyCOSProcessor().indexDependencyBuildItem();

    assertThat(indexDependency.getGroupId()).isEqualTo("io.github.cameleogrey");
    assertThat(indexDependency.getArtifactId()).isEqualTo("greycos-solver-core");
  }
}
