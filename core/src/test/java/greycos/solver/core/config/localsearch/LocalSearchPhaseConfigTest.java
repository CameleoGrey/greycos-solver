package greycos.solver.core.config.localsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.io.StringReader;
import java.io.StringWriter;

import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.io.jaxb.GreyCOSXmlSerializationException;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

class LocalSearchPhaseConfigTest {

  @Test
  void withMethodCallsProperlyChain() {
    final int acceptedCountLimit = 5;
    LocalSearchPhaseConfig localSearchPhaseConfig =
        new LocalSearchPhaseConfig()
            .withLocalSearchType(LocalSearchType.TABU_SEARCH)
            .withStepLoggingMode(LocalSearchStepLoggingMode.BEST_SCORE_IMPROVED)
            .withTerminationConfig(new TerminationConfig().withBestScoreFeasible(true))
            .withForagerConfig(
                new LocalSearchForagerConfig().withAcceptedCountLimit(acceptedCountLimit));

    assertSoftly(
        softly -> {
          softly
              .assertThat(localSearchPhaseConfig.getLocalSearchType())
              .isEqualTo(LocalSearchType.TABU_SEARCH);
          softly
              .assertThat(localSearchPhaseConfig.getStepLoggingMode())
              .isEqualTo(LocalSearchStepLoggingMode.BEST_SCORE_IMPROVED);
          softly.assertThat(localSearchPhaseConfig.getTerminationConfig()).isNotNull();
          softly
              .assertThat(localSearchPhaseConfig.getTerminationConfig().getBestScoreFeasible())
              .isTrue();
          softly.assertThat(localSearchPhaseConfig.getForagerConfig()).isNotNull();
          softly
              .assertThat(localSearchPhaseConfig.getForagerConfig().getAcceptedCountLimit())
              .isEqualTo(acceptedCountLimit);
        });
  }

  @ParameterizedTest
  @NullSource
  @EnumSource(LocalSearchStepLoggingMode.class)
  void stepLoggingModePreservesNullableValueWhenCopiedAndInherited(
      LocalSearchStepLoggingMode mode) {
    var original = new LocalSearchPhaseConfig();
    assertThat(original.getStepLoggingMode()).isNull();
    original.setStepLoggingMode(mode);

    assertThat(original.getStepLoggingMode()).isEqualTo(mode);
    assertThat(original.copyConfig().getStepLoggingMode()).isEqualTo(mode);
    assertThat(new LocalSearchPhaseConfig().inherit(original).getStepLoggingMode()).isEqualTo(mode);
    for (var override : LocalSearchStepLoggingMode.values()) {
      assertThat(
              new LocalSearchPhaseConfig()
                  .withStepLoggingMode(override)
                  .inherit(original)
                  .getStepLoggingMode())
          .isEqualTo(override);
    }
  }

  @ParameterizedTest
  @NullSource
  @EnumSource(LocalSearchStepLoggingMode.class)
  void xmlRoundTripPreservesNullableStepLoggingMode(LocalSearchStepLoggingMode mode) {
    var modeElement = mode == null ? "" : "<stepLoggingMode>" + mode + "</stepLoggingMode>";
    var xml =
        """
        <solver xmlns="%s">
          <localSearch>
            <localSearchType>LATE_ACCEPTANCE</localSearchType>
            %s
            <forager><acceptedCountLimit>1</acceptedCountLimit></forager>
          </localSearch>
        </solver>
        """
            .formatted(SolverConfig.XML_NAMESPACE, modeElement);
    var io = new SolverConfigIO();
    var config = io.read(new StringReader(xml));
    var phase = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    assertThat(phase.getLocalSearchType()).isEqualTo(LocalSearchType.LATE_ACCEPTANCE);
    assertThat(phase.getStepLoggingMode()).isEqualTo(mode);
    assertThat(phase.getForagerConfig().getAcceptedCountLimit()).isEqualTo(1);

    var output = new StringWriter();
    io.write(config, output);
    if (mode == null) {
      assertThat(output.toString()).doesNotContain("stepLoggingMode");
    } else {
      assertThat(output.toString()).contains(modeElement);
    }
    assertThat(io.read(new StringReader(output.toString())))
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void namespacedXmlRejectsUnknownStepLoggingMode() {
    var xml =
        """
        <solver xmlns="%s">
          <localSearch><stepLoggingMode>UNKNOWN</stepLoggingMode></localSearch>
        </solver>
        """
            .formatted(SolverConfig.XML_NAMESPACE);
    assertThatThrownBy(() -> new SolverConfigIO().read(new StringReader(xml)))
        .isInstanceOf(GreyCOSXmlSerializationException.class);
  }
}
