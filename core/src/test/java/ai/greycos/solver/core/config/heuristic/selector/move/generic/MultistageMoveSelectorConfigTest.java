package ai.greycos.solver.core.config.heuristic.selector.move.generic;

import static org.assertj.core.api.Assertions.assertThat;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.testdomain.TestdataEntity;

import org.junit.jupiter.api.Test;

class MultistageMoveSelectorConfigTest {

  @Test
  void withMethodChaining() {
    var config =
        new MultistageMoveSelectorConfig()
            .withStageProviderClass(TestStageProvider.class)
            .withEntityClass(TestdataEntity.class)
            .withVariableName("value");

    assertThat(config.getStageProviderClass()).isEqualTo(TestStageProvider.class);
    assertThat(config.getEntityClass()).isEqualTo(TestdataEntity.class);
    assertThat(config.getVariableName()).isEqualTo("value");
  }

  @Test
  void configInheritance() {
    MultistageMoveSelectorConfig parent =
        new MultistageMoveSelectorConfig()
            .withStageProviderClass(TestStageProvider.class)
            .withCacheType(SelectionCacheType.PHASE)
            .withEntityClass(TestdataEntity.class)
            .withVariableName("value");

    MultistageMoveSelectorConfig child = new MultistageMoveSelectorConfig();
    child.inherit(parent);

    assertThat(child.getStageProviderClass()).isEqualTo(TestStageProvider.class);
    assertThat(child.getCacheType()).isEqualTo(SelectionCacheType.PHASE);
    assertThat(child.getEntityClass()).isEqualTo(TestdataEntity.class);
    assertThat(child.getVariableName()).isEqualTo("value");
  }

  @Test
  void configCopy() {
    MultistageMoveSelectorConfig original =
        new MultistageMoveSelectorConfig()
            .withStageProviderClass(TestStageProvider.class)
            .withEntityClass(TestdataEntity.class)
            .withVariableName("value");

    MultistageMoveSelectorConfig copy = original.copyConfig();

    assertThat(copy.getStageProviderClass()).isEqualTo(TestStageProvider.class);
    assertThat(copy.getEntityClass()).isEqualTo(TestdataEntity.class);
    assertThat(copy.getVariableName()).isEqualTo("value");
  }

  // Test StageProvider for config testing
  public static class TestStageProvider {
    // Dummy class for testing config
  }
}
