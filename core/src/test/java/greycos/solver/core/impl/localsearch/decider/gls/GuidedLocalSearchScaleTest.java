package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class GuidedLocalSearchScaleTest {

  @Test
  void exactEvenMedianPublishesAtBarrierAndRetainsWithoutNewData() {
    var calibration = new GuidedLocalSearchScale.Calibration(null);
    calibration.observe(GuidedLocalSearchNumber.ONE, 3);
    calibration.observe(GuidedLocalSearchNumber.of(2), 3);
    assertThat(calibration.scale()).isEqualTo(GuidedLocalSearchScale.ONE);
    var median = calibration.publishAtPenaltyUpdate();
    assertThat(median).isEqualTo(GuidedLocalSearchScale.of(new BigDecimal("0.5")));
    assertThat(calibration.publishAtPenaltyUpdate()).isSameAs(median);
    assertThat(calibration.calibrated()).isTrue();
  }

  @Test
  void firstEmpiricalScaleReplacesOneWithoutClampingThenChangesAtMostTwofold() {
    var calibration = new GuidedLocalSearchScale.Calibration(null);
    calibration.observe(GuidedLocalSearchNumber.ZERO, 0);
    calibration.observe(GuidedLocalSearchNumber.of(100), 0);
    assertThat(calibration.publishAtPenaltyUpdate()).isEqualTo(GuidedLocalSearchScale.ONE);
    assertThat(calibration.calibrated()).isFalse();
    calibration.observe(GuidedLocalSearchNumber.of(-100), 1);
    assertThat(calibration.publishAtPenaltyUpdate())
        .isEqualTo(GuidedLocalSearchScale.of(new BigDecimal("100")));
    for (int i = 0; i < 256; i++) calibration.observe(GuidedLocalSearchNumber.of(1_000), 1);
    assertThat(calibration.publishAtPenaltyUpdate())
        .isEqualTo(GuidedLocalSearchScale.of(new BigDecimal("200")));
    assertThat(calibration.publishAtPenaltyUpdate())
        .isEqualTo(GuidedLocalSearchScale.of(new BigDecimal("200")));
    for (int i = 0; i < 256; i++) calibration.observe(GuidedLocalSearchNumber.ONE, 1);
    assertThat(calibration.publishAtPenaltyUpdate())
        .isEqualTo(GuidedLocalSearchScale.of(new BigDecimal("100")));
    calibration.observe(GuidedLocalSearchNumber.ONE, 1);
    assertThat(calibration.publishAtPenaltyUpdate())
        .isEqualTo(GuidedLocalSearchScale.of(new BigDecimal("50")));
  }

  @Test
  void rollingWindowKeepsLatest256InformativeObservationsAndOverrideRemainsFixed() {
    var calibration = new GuidedLocalSearchScale.Calibration(null);
    for (int i = 0; i < 128; i++) calibration.observe(GuidedLocalSearchNumber.of(7), 2);
    for (int i = 0; i < 256; i++) calibration.observe(GuidedLocalSearchNumber.ONE, 2);
    for (int i = 0; i < 512; i++) calibration.observe(GuidedLocalSearchNumber.ZERO, 1);
    assertThat(calibration.observationCount()).isEqualTo(256);
    assertThat(calibration.publishAtPenaltyUpdate())
        .isEqualTo(GuidedLocalSearchScale.of(new BigDecimal("0.5")));
    var explicit = new GuidedLocalSearchScale.Calibration(new BigDecimal("0.125"));
    explicit.observe(GuidedLocalSearchNumber.of(100), 1);
    assertThat(explicit.publishAtPenaltyUpdate())
        .isEqualTo(GuidedLocalSearchScale.of(new BigDecimal("0.125")));
    assertThat(explicit.calibrated()).isTrue();
    assertThat(explicit.observationCount()).isZero();
  }

  @Test
  void scaleFractionsAreReducedAndDecimalComponentsRemainExact() {
    assertThat(
            new GuidedLocalSearchScale(
                GuidedLocalSearchNumber.of(new BigDecimal("0.10")),
                GuidedLocalSearchNumber.of(new BigDecimal("0.30"))))
        .isEqualTo(GuidedLocalSearchScale.of(GuidedLocalSearchNumber.ONE, 3));
    assertThat(GuidedLocalSearchScale.of(GuidedLocalSearchNumber.ONE, 3).dividedBy(4))
        .isEqualTo(GuidedLocalSearchScale.of(GuidedLocalSearchNumber.ONE, 12));
  }
}
