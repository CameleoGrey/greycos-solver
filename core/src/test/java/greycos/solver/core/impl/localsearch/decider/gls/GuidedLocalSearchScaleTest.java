package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class GuidedLocalSearchScaleTest {

  @Test
  void evenMedianPreservesNonTerminatingRationalAndThenFreezes() {
    var calibration = new GuidedLocalSearchScale.Calibration(null);
    calibration.observe(GuidedLocalSearchNumber.ONE, 3);
    calibration.observe(GuidedLocalSearchNumber.of(2), 3);
    var median = calibration.freezeAtPenaltyUpdate();
    assertThat(median.compareTo(GuidedLocalSearchScale.of(new BigDecimal("0.5")))).isZero();
    calibration.observe(GuidedLocalSearchNumber.of(100), 1);
    assertThat(calibration.freezeAtPenaltyUpdate()).isSameAs(median);
  }

  @Test
  void noDataRemainsProvisionalUntilNextPenaltyWithInformativeData() {
    var calibration = new GuidedLocalSearchScale.Calibration(null);
    calibration.observe(GuidedLocalSearchNumber.ZERO, 0);
    assertThat(calibration.freezeAtPenaltyUpdate()).isEqualTo(GuidedLocalSearchScale.ONE);
    calibration.observe(GuidedLocalSearchNumber.of(-2), 3);
    assertThat(calibration.scale()).isEqualTo(GuidedLocalSearchScale.ONE);
    assertThat(
            calibration
                .freezeAtPenaltyUpdate()
                .compareTo(GuidedLocalSearchScale.of(GuidedLocalSearchNumber.of(2), 3)))
        .isZero();
  }

  @Test
  void onlyFirst128InformativeObservationsCountAndOverrideNeverRecalibrates() {
    var calibration = new GuidedLocalSearchScale.Calibration(null);
    for (int i = 0; i < 128; i++) calibration.observe(GuidedLocalSearchNumber.of(7), 2);
    for (int i = 0; i < 256; i++) calibration.observe(GuidedLocalSearchNumber.ONE, 2);
    assertThat(
            calibration
                .freezeAtPenaltyUpdate()
                .compareTo(GuidedLocalSearchScale.of(new BigDecimal("3.5"))))
        .isZero();
    var explicit = new GuidedLocalSearchScale.Calibration(new BigDecimal("0.125"));
    explicit.observe(GuidedLocalSearchNumber.of(100), 1);
    assertThat(
            explicit
                .freezeAtPenaltyUpdate()
                .compareTo(GuidedLocalSearchScale.of(new BigDecimal("0.125"))))
        .isZero();
  }
}
