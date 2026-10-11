package greycos.solver.math.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchPickEarlyType;
import greycos.solver.core.config.solver.EnvironmentMode;

import org.junit.jupiter.api.Test;

class SolveOptionsTest {

  @Test
  void explicitDefaultsAndImmutableLimitChanges() {
    var original = SolveOptions.moveEvaluationLimit(17);
    var changed =
        original
            .withTimeLimit(Duration.ofSeconds(2))
            .withSeed(45)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT);
    assertThat(original.seed()).isZero();
    assertThat(original.environmentMode()).isEqualTo(EnvironmentMode.PHASE_ASSERT);
    assertThat(original.timeLimit()).isEmpty();
    assertThat(original.moveEvaluationLimit()).hasValue(17);
    assertThat(original.acceptorConfig().getAcceptorTypeList())
        .containsExactly(AcceptorType.LATE_ACCEPTANCE);
    assertThat(original.acceptorConfig().getLateAcceptanceSize()).isEqualTo(400);
    assertThat(original.foragerConfig().getAcceptedCountLimit()).isEqualTo(1);
    assertThat(changed.seed()).isEqualTo(45);
    assertThat(changed.timeLimit()).contains(Duration.ofSeconds(2));
    assertThat(changed.moveEvaluationLimit()).hasValue(17);
    assertThat(changed.environmentMode()).isEqualTo(EnvironmentMode.FULL_ASSERT);
    assertThat(SolveOptions.timeLimit(Duration.ZERO).timeLimit()).contains(Duration.ZERO);
    assertThat(SolveOptions.moveEvaluationLimit(0).moveEvaluationLimit()).hasValue(0);
  }

  @Test
  void nestedAcceptorAndForagerSettingsDoNotAliasInputsOrOutputs() {
    var types = new ArrayList<>(List.of(AcceptorType.LATE_ACCEPTANCE));
    var acceptor =
        new LocalSearchAcceptorConfig().withAcceptorTypeList(types).withLateAcceptanceSize(7);
    var forager =
        new LocalSearchForagerConfig().withAcceptedCountLimit(3).withBreakTieRandomly(false);
    var options =
        SolveOptions.moveEvaluationLimit(20)
            .withAcceptorConfig(acceptor)
            .withForagerConfig(forager);
    types.clear();
    acceptor.setLateAcceptanceSize(100);
    forager.setAcceptedCountLimit(100);
    var returnedAcceptor = options.acceptorConfig();
    returnedAcceptor.getAcceptorTypeList().clear();
    returnedAcceptor.setLateAcceptanceSize(200);
    var returnedForager = options.foragerConfig();
    returnedForager.setAcceptedCountLimit(200);
    returnedForager.setCustomProperties(Map.of("unused", "value"));
    assertThat(options.acceptorConfig().getAcceptorTypeList())
        .containsExactly(AcceptorType.LATE_ACCEPTANCE);
    assertThat(options.acceptorConfig().getLateAcceptanceSize()).isEqualTo(7);
    assertThat(options.foragerConfig().getAcceptedCountLimit()).isEqualTo(3);
    assertThat(options.foragerConfig().getBreakTieRandomly()).isFalse();
    assertThat(options.foragerConfig().getCustomProperties()).isNull();
  }

  @Test
  void partialForagerTuningRetainsFiniteScalarSearchDefaults() {
    var options =
        SolveOptions.moveEvaluationLimit(1)
            .withForagerConfig(
                new LocalSearchForagerConfig()
                    .withPickEarlyType(LocalSearchPickEarlyType.FIRST_BEST_SCORE_IMPROVING));
    assertThat(options.foragerConfig().getAcceptedCountLimit()).isEqualTo(1);
    assertThat(options.foragerConfig().getPickEarlyType())
        .isEqualTo(LocalSearchPickEarlyType.FIRST_BEST_SCORE_IMPROVING);
  }

  @Test
  void rejectsNegativeTruncatedOrOverflowingLimits() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> SolveOptions.moveEvaluationLimit(-1))
        .withMessageContaining("-1");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> SolveOptions.timeLimit(Duration.ofMillis(-1)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> SolveOptions.timeLimit(Duration.ofNanos(1)))
        .withMessageContaining("whole milliseconds");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> SolveOptions.timeLimit(Duration.ofSeconds(Long.MAX_VALUE)))
        .withMessageContaining("millisecond range");
    assertThat(SolveOptions.timeLimit(Duration.ofMillis(Long.MAX_VALUE)).timeLimit())
        .contains(Duration.ofMillis(Long.MAX_VALUE));
  }

  @Test
  void rejectsPreviewCustomOrUnboundedSearchSettings() {
    var options = SolveOptions.moveEvaluationLimit(10);
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                options.withAcceptorConfig(
                    new LocalSearchAcceptorConfig()
                        .withAcceptorTypeList(List.of(AcceptorType.DIVERSIFIED_LATE_ACCEPTANCE))));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                options.withAcceptorConfig(
                    new LocalSearchAcceptorConfig()
                        .withAcceptorTypeList(List.of(AcceptorType.UNDO_MOVE_TABU))));
    var nullTypeList = new ArrayList<AcceptorType>();
    nullTypeList.add(null);
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                options.withAcceptorConfig(
                    new LocalSearchAcceptorConfig().withAcceptorTypeList(nullTypeList)));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                options.withForagerConfig(
                    new LocalSearchForagerConfig().withCustomProperties(Map.of())))
        .withMessageContaining("customProperties");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                options.withForagerConfig(
                    new LocalSearchForagerConfig().withAcceptedCountLimit(0)));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                options.withForagerConfig(
                    new LocalSearchForagerConfig().withAcceptedCountLimit(Integer.MAX_VALUE)));
  }

  @Test
  void validatesDeferredAcceptorNumbersWhilePreservingZeroTemperatureAndCombinations() {
    var options = SolveOptions.moveEvaluationLimit(0);
    for (int invalid : new int[] {0, -1}) {
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  options.withAcceptorConfig(
                      new LocalSearchAcceptorConfig().withLateAcceptanceSize(invalid)))
          .withMessageContaining("lateAcceptanceSize");
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  options.withAcceptorConfig(
                      new LocalSearchAcceptorConfig().withStepCountingHillClimbingSize(invalid)))
          .withMessageContaining("stepCountingHillClimbingSize");
    }
    for (var temperature : List.of("-1hard/1soft", "1hard/-1soft", "-1hard/-1soft")) {
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  options.withAcceptorConfig(
                      new LocalSearchAcceptorConfig()
                          .withSimulatedAnnealingStartingTemperature(temperature)))
          .withMessageContaining("simulatedAnnealingStartingTemperature");
    }
    var valid =
        new LocalSearchAcceptorConfig()
            .withAcceptorTypeList(
                List.of(AcceptorType.LATE_ACCEPTANCE, AcceptorType.SIMULATED_ANNEALING))
            .withLateAcceptanceSize(1)
            .withSimulatedAnnealingStartingTemperature("0hard/0soft");
    assertThat(
            options
                .withAcceptorConfig(valid)
                .acceptorConfig()
                .getSimulatedAnnealingStartingTemperature())
        .isEqualTo("0hard/0soft");
  }
}
