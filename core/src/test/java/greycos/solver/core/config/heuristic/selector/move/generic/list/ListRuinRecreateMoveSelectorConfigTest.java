package greycos.solver.core.config.heuristic.selector.move.generic.list;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;

import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ListRuinRecreateMoveSelectorConfigTest {

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {false, true})
  void nearbyConfigurationPreservesNullableFlagWhenCopiedAndInherited(Boolean enabled) {
    var original = new ListRuinRecreateMoveSelectorConfig();
    assertThat(original.getNearbySelectionAutoConfigurationEnabled()).isNull();
    original.setNearbySelectionAutoConfigurationEnabled(enabled);

    assertThat(original.getNearbySelectionAutoConfigurationEnabled()).isEqualTo(enabled);
    assertThat(original.copyConfig().getNearbySelectionAutoConfigurationEnabled())
        .isEqualTo(enabled);
    assertThat(
            new ListRuinRecreateMoveSelectorConfig()
                .inherit(original)
                .getNearbySelectionAutoConfigurationEnabled())
        .isEqualTo(enabled);
    for (var override : new boolean[] {false, true}) {
      assertThat(
              new ListRuinRecreateMoveSelectorConfig()
                  .withNearbySelectionAutoConfigurationEnabled(override)
                  .inherit(original)
                  .getNearbySelectionAutoConfigurationEnabled())
          .isEqualTo(override);
    }
    assertThat(original.hasNearbySelectionConfig()).isFalse();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {false, true})
  void xmlRoundTripPreservesNullableNearbyFlag(Boolean enabled) {
    var nearbyElement =
        enabled == null
            ? ""
            : "<nearbySelectionAutoConfigurationEnabled>"
                + enabled
                + "</nearbySelectionAutoConfigurationEnabled>";
    var xml =
        """
        <solver xmlns="https://github.com/CameleoGrey/greycos-solver/xsd/solver">
          <localSearch>
            <listRuinRecreateMoveSelector>
              <minimumRuinedCount>1</minimumRuinedCount>
              <maximumRuinedCount>3</maximumRuinedCount>
              <minimumRuinedPercentage>0.1</minimumRuinedPercentage>
              <maximumRuinedPercentage>0.5</maximumRuinedPercentage>
              %s
            </listRuinRecreateMoveSelector>
          </localSearch>
        </solver>
        """
            .formatted(nearbyElement);
    var io = new SolverConfigIO();
    var config = io.read(new StringReader(xml));
    var phase = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    var selector = (ListRuinRecreateMoveSelectorConfig) phase.getMoveSelectorConfig();
    assertThat(selector.getNearbySelectionAutoConfigurationEnabled()).isEqualTo(enabled);
    assertThat(selector.getMinimumRuinedCount()).isEqualTo(1);
    assertThat(selector.getMaximumRuinedPercentage()).isEqualTo(0.5);

    var output = new StringWriter();
    io.write(config, output);
    if (enabled == null) {
      assertThat(output.toString()).doesNotContain("nearbySelectionAutoConfigurationEnabled");
    } else {
      assertThat(output.toString()).contains(nearbyElement);
    }
    assertThat(io.read(new StringReader(output.toString())))
        .usingRecursiveComparison()
        .isEqualTo(config);
  }
}
