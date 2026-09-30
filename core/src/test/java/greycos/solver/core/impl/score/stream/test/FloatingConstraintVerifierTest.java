package greycos.solver.core.impl.score.stream.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.test.ConstraintVerifier;
import greycos.solver.core.impl.score.definition.SimpleDoubleScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleFloatScoreDefinition;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;

class FloatingConstraintVerifierTest {

  @Test
  void fractionalImpactAndExactContributionReduction() {
    var verifier = ConstraintVerifier.build(new Provider(), Solution.class, TestdataEntity.class);
    var assertion = verifier.verifyThat(Provider::reward).given(new Weight(0.25), new Weight(0.5));
    assertion.rewardsWith(0.75);
    assertion.rewardsWith(0.75f);
    assertion.rewardsWithMoreThan(0.5);
    assertion.rewardsWithLessThan(1);
    verifier
        .verifyThat(Provider::reward)
        .given(new Weight(0.1), new Weight(0.2))
        .rewardsWith(0.1 + 0.2);
    assertThatThrownBy(() -> assertion.rewardsWith(0)).isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertion.rewardsWith(Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);
    verifier
        .verifyThat(Provider::reward)
        .given(new Weight(0x1p53), new Weight(1), new Weight(1))
        .rewardsWith(0x1p53 + 2);
    verifier
        .verifyThat(Provider::impact)
        .given(new Weight(0x1p53), new Weight(1), new Weight(-0x1p53))
        .rewardsWith(1);
  }

  @Test
  void originalWeightsSurviveProductRoundingAndUnderflow() {
    var verifier = ConstraintVerifier.build(new Provider(), Solution.class, TestdataEntity.class);
    verifier.verifyThat(Provider::nonUnitReward).given(new Weight(3)).rewardsWith(3.0);
    verifier.verifyThat(Provider::nonUnitPenalty).given(new Weight(3)).penalizesBy(3.0);
    var underflow = verifier.verifyThat(Provider::underflow).given(new Weight(0.25));
    underflow.rewardsWith(0.25);
    underflow.rewardsWithLessThan(0.5);
    assertThat(underflow.<SimpleDoubleScore>getScore()).isEqualTo(SimpleDoubleScore.ZERO);
    assertThatThrownBy(underflow::hasNoImpact).isInstanceOf(AssertionError.class);
    verifier
        .verifyThat(Provider::longWeight)
        .given(new IntegralWeight(9_007_199_254_740_993L))
        .rewardsWith(9_007_199_254_740_993L);
    var mixed =
        verifier.verifyThat(Provider::underflowMixed).given(new Weight(1), new Weight(-0.25));
    mixed.rewards(1);
    mixed.penalizes(1);
    var floatVerifier =
        ConstraintVerifier.build(new FloatProvider(), FloatSolution.class, TestdataEntity.class);
    floatVerifier.verifyThat(FloatProvider::nonUnit).given(new Weight(3)).rewardsWith(3.0f);
    floatVerifier.verifyThat(FloatProvider::underflow).given(new Weight(0.25)).rewardsWith(0.25f);
    floatVerifier
        .verifyThat(FloatProvider::doubleWeigher)
        .given(new Weight(0.1), new Weight(0.2))
        .rewardsWith(0.1 + 0.2);
    var multiVerifier =
        ConstraintVerifier.build(new MultiProvider(), MultiSolution.class, TestdataEntity.class);
    multiVerifier.verifyThat(MultiProvider::nonUnit).given(new Weight(3)).rewardsWith(3.0);
  }

  @Test
  void doubleMatchWeightTotalCanExceedDoubleRangeWithFiniteScore() {
    var verifier = ConstraintVerifier.build(new Provider(), Solution.class, TestdataEntity.class);
    var assertion =
        verifier
            .verifyThat(Provider::tinyWeight)
            .given(new Weight(Double.MAX_VALUE), new Weight(Double.MAX_VALUE));
    var exactTotal = new BigDecimal(Double.MAX_VALUE).multiply(BigDecimal.valueOf(2));
    assertThat(Double.isFinite(assertion.<SimpleDoubleScore>getScore().score())).isTrue();
    assertThat(assertion.getImpact()).isEqualTo(exactTotal);
    assertion.rewardsWithMoreThan(Double.MAX_VALUE);
    assertion.rewardsWithLessThan(exactTotal.add(BigDecimal.ONE));
    assertion.rewardsWith(exactTotal);
    assertThatThrownBy(() -> assertion.rewardsWithLessThan(Double.MAX_VALUE))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void floatMatchWeightTotalCanExceedFloatRangeWithFiniteScore() {
    var verifier =
        ConstraintVerifier.build(new FloatProvider(), FloatSolution.class, TestdataEntity.class);
    var assertion =
        verifier
            .verifyThat(FloatProvider::tinyWeight)
            .given(new Weight(Float.MAX_VALUE), new Weight(Float.MAX_VALUE));
    var exactTotal = new BigDecimal((double) Float.MAX_VALUE).multiply(BigDecimal.valueOf(2));
    assertThat(Float.isFinite(assertion.<SimpleFloatScore>getScore().score())).isTrue();
    assertThat(assertion.getImpact()).isEqualTo(exactTotal);
    assertion.rewardsWithMoreThan(Float.MAX_VALUE);
    assertion.rewardsWithLessThan(exactTotal.add(BigDecimal.ONE));
    assertion.rewardsWith(exactTotal);
    assertThatThrownBy(() -> assertion.rewardsWithLessThan(Float.MAX_VALUE))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void integralMatchWeightTotalRemainsExactBeyondLongRange() {
    var verifier = ConstraintVerifier.build(new Provider(), Solution.class, TestdataEntity.class);
    var assertion =
        verifier
            .verifyThat(Provider::longWeight)
            .given(new IntegralWeight(Long.MAX_VALUE), new IntegralWeight(Long.MAX_VALUE - 1));
    var exactTotal =
        BigDecimal.valueOf(Long.MAX_VALUE).multiply(BigDecimal.valueOf(2)).subtract(BigDecimal.ONE);
    assertThat(Double.isFinite(assertion.<SimpleDoubleScore>getScore().score())).isTrue();
    assertThat(assertion.getImpact()).isEqualTo(exactTotal);
    assertion.rewardsWithMoreThan(Long.MAX_VALUE);
    assertion.rewardsWithLessThan(exactTotal.add(BigDecimal.ONE));
    assertion.rewardsWith(exactTotal);
    assertThatThrownBy(() -> assertion.rewardsWithLessThan(Long.MAX_VALUE))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void comparisonsDoNotTruncateFractionsOrLargeIntegers() {
    var doubles = new SimpleDoubleScoreDefinition();
    assertThat(NumberEqualityUtil.getEqualityPredicate(doubles, 0).test(0, 0.5)).isFalse();
    assertThat(
            NumberEqualityUtil.getEqualityPredicate(doubles, Long.MAX_VALUE)
                .test(Long.MAX_VALUE, (double) Long.MAX_VALUE))
        .isFalse();
    assertThat(NumberEqualityUtil.getComparison(0).compare(0.5, 0)).isPositive();
    assertThat(
            NumberEqualityUtil.getEqualityPredicate(new SimpleFloatScoreDefinition(), 0.1f)
                .test(0.1f, 0.1f))
        .isTrue();
    assertThat(
            NumberEqualityUtil.getEqualityPredicate(
                    new SimpleFloatScoreDefinition(), new BigDecimal("0.1"))
                .test(new BigDecimal("0.1"), 0.1f))
        .isFalse();
  }

  public static final class Weight {
    private final double value;

    public Weight(double value) {
      this.value = value;
    }

    public double value() {
      return value;
    }
  }

  public record IntegralWeight(long value) {}

  public static class Provider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        reward(factory),
        impact(factory),
        nonUnitReward(factory),
        nonUnitPenalty(factory),
        underflow(factory),
        longWeight(factory),
        underflowMixed(factory),
        tinyWeight(factory)
      };
    }

    Constraint reward(ConstraintFactory factory) {
      return factory
          .forEach(Weight.class)
          .rewardDouble(SimpleDoubleScore.ONE, Weight::value)
          .asConstraint("reward");
    }

    Constraint impact(ConstraintFactory factory) {
      return factory
          .forEach(Weight.class)
          .impactDouble(SimpleDoubleScore.ONE, Weight::value)
          .asConstraint("impact");
    }

    Constraint nonUnitReward(ConstraintFactory factory) {
      return factory
          .forEach(Weight.class)
          .rewardDouble(SimpleDoubleScore.of(0.1), Weight::value)
          .asConstraint("nonUnitReward");
    }

    Constraint nonUnitPenalty(ConstraintFactory factory) {
      return factory
          .forEach(Weight.class)
          .penalizeDouble(SimpleDoubleScore.of(0.1), Weight::value)
          .asConstraint("nonUnitPenalty");
    }

    Constraint underflow(ConstraintFactory factory) {
      return factory
          .forEach(Weight.class)
          .rewardDouble(SimpleDoubleScore.of(Double.MIN_VALUE), Weight::value)
          .asConstraint("underflow");
    }

    Constraint underflowMixed(ConstraintFactory factory) {
      return factory
          .forEach(Weight.class)
          .impactDouble(SimpleDoubleScore.of(Double.MIN_VALUE), Weight::value)
          .asConstraint("underflowMixed");
    }

    Constraint tinyWeight(ConstraintFactory factory) {
      return factory
          .forEach(Weight.class)
          .rewardDouble(SimpleDoubleScore.of(Double.MIN_NORMAL), Weight::value)
          .asConstraint("tinyWeight");
    }

    Constraint longWeight(ConstraintFactory factory) {
      return factory
          .forEach(IntegralWeight.class)
          .reward(SimpleDoubleScore.of(0.1), IntegralWeight::value)
          .asConstraint("longWeight");
    }
  }

  public static class FloatProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        nonUnit(factory), underflow(factory), doubleWeigher(factory), tinyWeight(factory)
      };
    }

    Constraint tinyWeight(ConstraintFactory factory) {
      return factory
          .forEach(Weight.class)
          .rewardFloat(SimpleFloatScore.of(Float.MIN_NORMAL), weight -> (float) weight.value())
          .asConstraint("tinyWeight");
    }

    Constraint nonUnit(ConstraintFactory factory) {
      return factory
          .forEach(Weight.class)
          .rewardFloat(SimpleFloatScore.of(0.1f), weight -> (float) weight.value())
          .asConstraint("nonUnit");
    }

    Constraint doubleWeigher(ConstraintFactory factory) {
      return factory
          .forEach(Weight.class)
          .rewardDouble(SimpleFloatScore.of(0.1f), Weight::value)
          .asConstraint("doubleWeigher");
    }

    Constraint underflow(ConstraintFactory factory) {
      return factory
          .forEach(Weight.class)
          .rewardFloat(SimpleFloatScore.of(Float.MIN_VALUE), weight -> (float) weight.value())
          .asConstraint("underflow");
    }
  }

  public static class MultiProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {nonUnit(factory)};
    }

    Constraint nonUnit(ConstraintFactory factory) {
      return factory
          .forEach(Weight.class)
          .rewardDouble(HardSoftDoubleScore.of(0.1, 0.3), Weight::value)
          .asConstraint("nonUnit");
    }
  }

  @PlanningSolution
  public static class Solution {
    @PlanningEntityCollectionProperty public List<TestdataEntity> entities;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "valueRange")
    public List<TestdataValue> values;

    @ProblemFactCollectionProperty public List<Weight> weights;
    @ProblemFactCollectionProperty public List<IntegralWeight> integralWeights;
    @PlanningScore public SimpleDoubleScore score;

    public Solution() {}
  }

  @PlanningSolution
  public static class FloatSolution {
    @PlanningEntityCollectionProperty public List<TestdataEntity> entities;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "valueRange")
    public List<TestdataValue> values;

    @ProblemFactCollectionProperty public List<Weight> weights;
    @PlanningScore public SimpleFloatScore score;

    public FloatSolution() {}
  }

  @PlanningSolution
  public static class MultiSolution {
    @PlanningEntityCollectionProperty public List<TestdataEntity> entities;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "valueRange")
    public List<TestdataValue> values;

    @ProblemFactCollectionProperty public List<Weight> weights;
    @PlanningScore public HardSoftDoubleScore score;

    public MultiSolution() {}
  }
}
