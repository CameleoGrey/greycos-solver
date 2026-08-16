package greycos.solver.core.impl.constructionheuristic.placer.entity;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;

import greycos.solver.core.config.constructionheuristic.placer.PooledEntityPlacerConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.impl.constructionheuristic.placer.PooledEntityPlacerFactory;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

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
