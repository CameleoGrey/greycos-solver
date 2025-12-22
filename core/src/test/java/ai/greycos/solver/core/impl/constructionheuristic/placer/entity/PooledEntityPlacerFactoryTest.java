package ai.greycos.solver.core.impl.constructionheuristic.placer.entity;

import static ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;

import ai.greycos.solver.core.config.constructionheuristic.placer.PooledEntityPlacerConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import ai.greycos.solver.core.impl.constructionheuristic.placer.PooledEntityPlacerFactory;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataSolution;

import org.junit.jupiter.api.Test;

class PooledEntityPlacerFactoryTest {

  @Test
  void unfoldNew() {
    ChangeMoveSelectorConfig moveSelectorConfig =
        new ChangeMoveSelectorConfig().withValueSelectorConfig(new ValueSelectorConfig("value"));

    HeuristicConfigPolicy<TestdataSolution> configPolicy = buildHeuristicConfigPolicy();
    PooledEntityPlacerConfig placerConfig =
        PooledEntityPlacerFactory.unfoldNew(configPolicy, moveSelectorConfig);

    assertThat(placerConfig.getMoveSelectorConfig())
        .isExactlyInstanceOf(ChangeMoveSelectorConfig.class);

    ChangeMoveSelectorConfig changeMoveSelectorConfig =
        (ChangeMoveSelectorConfig) placerConfig.getMoveSelectorConfig();
    assertThat(changeMoveSelectorConfig.getEntitySelectorConfig().getEntityClass()).isNull();
    assertThat(changeMoveSelectorConfig.getEntitySelectorConfig().getMimicSelectorRef())
        .isEqualTo(TestdataEntity.class.getName());
    assertThat(changeMoveSelectorConfig.getValueSelectorConfig().getVariableName())
        .isEqualTo("value");
  }
}
