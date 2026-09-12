package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.alns.AlnsOperatorPair;
import greycos.solver.core.api.solver.alns.AlnsOutcome;
import greycos.solver.core.api.solver.alns.AlnsTrialResult;
import greycos.solver.core.config.alns.AlnsAcceptanceType;
import greycos.solver.core.config.alns.AlnsSelectionPolicyType;

import org.junit.jupiter.api.Test;

class DefaultAlnsPoliciesTest {
  @Test
  void lateAcceptanceAndMigrationHistory() {
    var policy =
        new DefaultAlnsAcceptance<SimpleScore>(AlnsAcceptanceType.LATE_ACCEPTANCE, 2, null, .999);
    var random = new Random(0);
    policy.initialize(SimpleScore.ZERO);
    assertThat(policy.isAccepted(SimpleScore.ZERO, SimpleScore.of(-1), random)).isFalse();
    policy.stepEnded(SimpleScore.of(10));
    assertThat(policy.isAccepted(SimpleScore.of(10), SimpleScore.of(5), random)).isTrue();
    policy.stepEnded(SimpleScore.of(5));
    assertThat(policy.isAccepted(SimpleScore.of(5), SimpleScore.of(4), random)).isFalse();
    policy.incumbentChanged(SimpleScore.of(100));
    assertThat(policy.isAccepted(SimpleScore.of(100), SimpleScore.of(99), random)).isFalse();
  }

  @Test
  void annealingCannotLoseAtZeroTemperatureLevel() {
    var policy =
        new DefaultAlnsAcceptance<HardSoftScore>(
            AlnsAcceptanceType.SIMULATED_ANNEALING, 2, HardSoftScore.of(0, 100), .999);
    policy.initialize(HardSoftScore.ZERO);
    assertThat(
            policy.isAccepted(HardSoftScore.ZERO, HardSoftScore.of(-1, 1_000_000), new Random(0)))
        .isFalse();
    assertThat(
            policy.isAccepted(HardSoftScore.ZERO, HardSoftScore.of(1, -1_000_000), new Random(0)))
        .isTrue();
  }

  @Test
  void exactArithmeticAndExplicitTemperature() {
    assertThat(
            AlnsScoreMath.difference(
                SimpleScore.of(Long.MAX_VALUE), SimpleScore.of(Long.MIN_VALUE)))
        .containsExactly(new BigDecimal("18446744073709551615"));
    assertThatThrownBy(
            () ->
                new DefaultAlnsAcceptance<SimpleScore>(
                    AlnsAcceptanceType.SIMULATED_ANNEALING, 400, null, .999))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("startingTemperature");
  }

  @Test
  void rouletteCreditsEachOperatorOncePerCompletedTrial() {
    var policy = selection(AlnsSelectionPolicyType.SEGMENTED_ROULETTE);
    policy.update(result("d1", "r", AlnsOutcome.NEW_BEST));
    assertThat(policy.weights().get("destroy/d1")).isEqualTo(1);
    policy.update(result("d1", "r", AlnsOutcome.CANCELLED));
    assertThat(policy.weights().get("destroy/d1")).isEqualTo(1);
    policy.update(result("d1", "r", AlnsOutcome.NO_CHANGE));
    assertThat(policy.weights().get("destroy/d1"))
        .isCloseTo(1.3, org.assertj.core.data.Offset.offset(1e-12));
    assertThat(policy.weights().get("repair/r"))
        .isCloseTo(1.3, org.assertj.core.data.Offset.offset(1e-12));
    assertThat(policy.weights().get("destroy/d2")).isEqualTo(1);
  }

  @Test
  void ucbExploresCompatiblePairsBeforeReusingOne() {
    var policy = selection(AlnsSelectionPolicyType.UCB);
    var pairs = List.of(new AlnsOperatorPair("d1", "r"), new AlnsOperatorPair("d2", "r"));
    var random = new Random(0);
    var first = policy.select(pairs, random);
    policy.update(result(first.destroyId(), first.repairId(), AlnsOutcome.NEW_BEST));
    assertThat(policy.select(pairs, random)).isNotEqualTo(first);
    assertThat(policy.select(List.of(first), random)).isEqualTo(first);
  }

  @Test
  void finiteExtremeRewardsCannotOverflowLearningWeights() {
    var policy =
        new DefaultAlnsSelection<SimpleScore>(
            AlnsSelectionPolicyType.SEGMENTED_ROULETTE,
            Map.of("d1", 1.0),
            Map.of("r", 1.0),
            2,
            .2,
            .01,
            Math.sqrt(2),
            Double.MAX_VALUE,
            3,
            1);
    policy.update(result("d1", "r", AlnsOutcome.NEW_BEST));
    policy.update(result("d1", "r", AlnsOutcome.NEW_BEST));
    assertThat(policy.weights().values())
        .allSatisfy(weight -> assertThat(Double.isFinite(weight)).isTrue());
    assertThat(policy.select(List.of(new AlnsOperatorPair("d1", "r")), new Random(0)))
        .isEqualTo(new AlnsOperatorPair("d1", "r"));
  }

  private DefaultAlnsSelection<SimpleScore> selection(AlnsSelectionPolicyType type) {
    var destroys = new LinkedHashMap<String, Double>();
    destroys.put("d1", 1.0);
    destroys.put("d2", 1.0);
    return new DefaultAlnsSelection<>(
        type, destroys, Map.of("r", 1.0), 2, .2, .01, Math.sqrt(2), 5, 3, 1);
  }

  private AlnsTrialResult<SimpleScore> result(String destroy, String repair, AlnsOutcome outcome) {
    return new AlnsTrialResult<>(
        0,
        destroy,
        repair,
        outcome,
        SimpleScore.ZERO,
        SimpleScore.ONE,
        SimpleScore.ZERO,
        SimpleScore.ZERO,
        SimpleScore.ONE,
        1,
        0,
        20,
        100);
  }
}
