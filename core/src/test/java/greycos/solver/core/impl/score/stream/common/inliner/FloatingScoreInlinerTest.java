package greycos.solver.core.impl.score.stream.common.inliner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleBigDecimalScore;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.definition.BendableDoubleScoreDefinition;
import greycos.solver.core.impl.score.definition.BendableFloatScoreDefinition;
import greycos.solver.core.impl.score.definition.HardMediumSoftDoubleScoreDefinition;
import greycos.solver.core.impl.score.definition.HardMediumSoftFloatScoreDefinition;
import greycos.solver.core.impl.score.definition.HardSoftDoubleScoreDefinition;
import greycos.solver.core.impl.score.definition.HardSoftFloatScoreDefinition;
import greycos.solver.core.impl.score.definition.ScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleDoubleScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleFloatScoreDefinition;
import greycos.solver.core.testconstraint.TestConstraint;
import greycos.solver.core.testconstraint.TestConstraintFactory;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FloatingScoreInlinerTest {

  static Stream<Arguments> definitionsAndPolicies() {
    return Stream.<ScoreDefinition<?>>of(
            new SimpleFloatScoreDefinition(),
            new SimpleDoubleScoreDefinition(),
            new HardSoftFloatScoreDefinition(),
            new HardSoftDoubleScoreDefinition(),
            new HardMediumSoftFloatScoreDefinition(),
            new HardMediumSoftDoubleScoreDefinition(),
            new BendableFloatScoreDefinition(2, 3),
            new BendableDoubleScoreDefinition(2, 3))
        .flatMap(
            definition ->
                Stream.of(ConstraintMatchPolicy.values())
                    .map(policy -> Arguments.of(definition, policy)));
  }

  @ParameterizedTest
  @MethodSource("definitionsAndPolicies")
  <Score_ extends Score<Score_>> void defaultScore(
      ScoreDefinition<Score_> definition, ConstraintMatchPolicy policy) {
    AbstractScoreInliner<Score_> inliner =
        AbstractScoreInliner.buildScoreInliner(definition, Map.of(), policy);
    assertThat(inliner.extractScore()).isEqualTo(definition.getZeroScore());
    assertThat(inliner).hasToString(definition.getScoreClass().getSimpleName() + " inliner");
  }

  @ParameterizedTest
  @MethodSource("definitionsAndPolicies")
  <Score_ extends Score<Score_>> void roundAfterSummingAndUndoExactly(
      ScoreDefinition<Score_> definition, ConstraintMatchPolicy policy) {
    var constraint = constraint(score(definition, 1.0));
    var inliner = inliner(definition, policy, constraint);
    var impacter = inliner.buildWeightedScoreImpacter(constraint);
    long large = definition.getNumericType() == float.class ? 1L << 24 : 1L << 53;
    var largeImpact = impacter.impactScore(large, supplier(policy));
    var smallImpact = impacter.impactScore(1.0f, supplier(policy));
    var otherSmallImpact = impacter.impactScore(1.0d, supplier(policy));
    var expected = score(definition, large + 2.0);
    assertThat(inliner.extractScore()).isEqualTo(expected);
    assertThat(inliner.extractScore()).isEqualTo(expected);
    assertMatches(inliner, constraint, policy, expected, 3);

    largeImpact.undo();
    assertThat(inliner.extractScore()).isEqualTo(score(definition, 2.0));
    assertMatches(inliner, constraint, policy, score(definition, 2.0), 2);
    smallImpact.undo();
    assertThat(inliner.extractScore()).isEqualTo(score(definition, 1.0));
    otherSmallImpact.undo();
    assertThat(inliner.extractScore()).isEqualTo(definition.getZeroScore());
    assertMatches(inliner, constraint, policy, definition.getZeroScore(), 0);
  }

  @ParameterizedTest
  @MethodSource("definitionsAndPolicies")
  <Score_ extends Score<Score_>> void exactCancellationAcrossConstraints(
      ScoreDefinition<Score_> definition, ConstraintMatchPolicy policy) {
    var positive = constraint("positive", score(definition, 1.0));
    var negative = constraint("negative", score(definition, -1.0));
    AbstractScoreInliner<Score_> inliner =
        AbstractScoreInliner.buildScoreInliner(
            definition,
            Map.of(
                positive, positive.getConstraintWeight(), negative, negative.getConstraintWeight()),
            policy);
    var positiveImpacter = inliner.buildWeightedScoreImpacter(positive);
    var negativeImpacter = inliner.buildWeightedScoreImpacter(negative);
    var large = positiveImpacter.impactScore(1L << 60, supplier(policy));
    var small = positiveImpacter.impactScore(0.25d, supplier(policy));
    var cancelling = negativeImpacter.impactScore(1L << 60, supplier(policy));
    assertThat(inliner.extractScore()).isEqualTo(score(definition, 0.25));
    large.undo();
    cancelling.undo();
    assertThat(inliner.extractScore()).isEqualTo(score(definition, 0.25));
    small.undo();
    assertThat(inliner.extractScore()).isEqualTo(definition.getZeroScore());
  }

  @ParameterizedTest
  @MethodSource("definitionsAndPolicies")
  <Score_ extends Score<Score_>> void overflowDoesNotPartiallyApplyAMatch(
      ScoreDefinition<Score_> definition, ConstraintMatchPolicy policy) {
    var levels = new Number[definition.getLevelsSize()];
    for (int i = 0; i < levels.length - 1; i++) {
      levels[i] = number(definition, 1.0);
    }
    levels[levels.length - 1] =
        number(
            definition,
            definition.getNumericType() == float.class ? Float.MAX_VALUE : Double.MAX_VALUE);
    var weight = definition.fromLevelNumbers(levels);
    var constraint = constraint(weight);
    var inliner = inliner(definition, policy, constraint);
    var impacter = inliner.buildWeightedScoreImpacter(constraint);
    var first = impacter.impactScore(0.25d, supplier(policy));
    var before = inliner.extractScore();
    assertMatches(inliner, constraint, policy, before, 1);
    assertThatThrownBy(() -> impacter.impactScore(2L, supplier(policy)))
        .isInstanceOf(ArithmeticException.class);
    assertThat(inliner.extractScore()).isEqualTo(before);
    assertMatches(inliner, constraint, policy, before, 1);
    first.undo();
    assertThat(inliner.extractScore()).isEqualTo(definition.getZeroScore());
  }

  @ParameterizedTest
  @MethodSource("definitionsAndPolicies")
  <Score_ extends Score<Score_>> void totalOverflowCanBeRetracted(
      ScoreDefinition<Score_> definition, ConstraintMatchPolicy policy) {
    var maximum = definition.getNumericType() == float.class ? Float.MAX_VALUE : Double.MAX_VALUE;
    var constraint = constraint(score(definition, maximum));
    var inliner = inliner(definition, policy, constraint);
    var impacter = inliner.buildWeightedScoreImpacter(constraint);
    var first = impacter.impactScore(1L, supplier(policy));
    var second = impacter.impactScore(1L, supplier(policy));
    assertThatThrownBy(inliner::extractScore).isInstanceOf(ArithmeticException.class);
    if (policy.isEnabled()) {
      assertThatThrownBy(
              () ->
                  inliner
                      .getConstraintMatchTotalMap()
                      .get(constraint.getConstraintRef())
                      .getScore())
          .isInstanceOf(ArithmeticException.class);
    }
    second.undo();
    assertThat(inliner.extractScore()).isEqualTo(score(definition, maximum));
    assertMatches(inliner, constraint, policy, score(definition, maximum), 1);
    first.undo();
    assertThat(inliner.extractScore()).isEqualTo(definition.getZeroScore());
  }

  @ParameterizedTest
  @MethodSource("definitionsAndPolicies")
  <Score_ extends Score<Score_>> void rejectInvalidWeightsBeforeMutation(
      ScoreDefinition<Score_> definition, ConstraintMatchPolicy policy) {
    var constraint = constraint(score(definition, 1.0));
    var inliner = inliner(definition, policy, constraint);
    var impacter = inliner.buildWeightedScoreImpacter(constraint);
    assertThatThrownBy(() -> impacter.impactScore(Float.NaN, supplier(policy)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> impacter.impactScore(Double.POSITIVE_INFINITY, supplier(policy)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> impacter.impactScore(Float.NEGATIVE_INFINITY, supplier(policy)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> impacter.impactScore(-1.0f, supplier(policy)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> impacter.impactScore(-1.0d, supplier(policy)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> impacter.impactScore(BigDecimal.ONE, supplier(policy)))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(inliner.extractScore()).isEqualTo(definition.getZeroScore());
    assertMatches(inliner, constraint, policy, definition.getZeroScore(), 0);
  }

  @ParameterizedTest
  @MethodSource("definitionsAndPolicies")
  <Score_ extends Score<Score_>> void contributionsAndArbitraryUndoMatchIndependentExactOracle(
      ScoreDefinition<Score_> definition, ConstraintMatchPolicy policy) {
    var weight = score(definition, 0.1);
    var constraint = constraint(weight);
    var inliner = inliner(definition, policy, constraint);
    var impacter = inliner.buildWeightedScoreImpacter(constraint);
    var exactWeight = new BigDecimal(weight.toLevelNumbers()[0].doubleValue());
    var total = BigDecimal.ZERO;
    var impacts = new ArrayList<ScoreImpact<Score_>>();
    var contributions = new ArrayList<BigDecimal>();
    for (int i = 0; i < 48; i++) {
      ScoreImpact<Score_> impact;
      BigDecimal exactMatchWeight;
      if (i % 3 == 0) {
        long matchWeight = (1L << 53) + i;
        impact = impacter.impactScore(matchWeight, supplier(policy));
        exactMatchWeight = BigDecimal.valueOf(matchWeight);
      } else if (i % 3 == 1) {
        float matchWeight = i * 0.1f;
        impact = impacter.impactScore(matchWeight, supplier(policy));
        exactMatchWeight = new BigDecimal((double) matchWeight);
      } else {
        double matchWeight = i * 0.1d;
        impact = impacter.impactScore(matchWeight, supplier(policy));
        exactMatchWeight = new BigDecimal(matchWeight);
      }
      var contribution = rounded(definition, exactWeight.multiply(exactMatchWeight));
      assertThat(impact.toScore()).isEqualTo(score(definition, contribution.doubleValue()));
      total = total.add(contribution);
      impacts.add(impact);
      contributions.add(contribution);
      assertThat(inliner.extractScore())
          .isEqualTo(score(definition, rounded(definition, total).doubleValue()));
    }
    var indices = new ArrayList<Integer>();
    for (int i = 0; i < impacts.size(); i++) {
      indices.add(i);
    }
    Collections.shuffle(indices, new Random(37));
    for (var index : indices) {
      impacts.get(index).undo();
      total = total.subtract(contributions.get(index));
      assertThat(inliner.extractScore())
          .isEqualTo(score(definition, rounded(definition, total).doubleValue()));
    }
    assertMatches(inliner, constraint, policy, definition.getZeroScore(), 0);
  }

  @Test
  void longMatchWeightIsNotRoundedToDoubleBeforeMultiplication() {
    var definition = new SimpleDoubleScoreDefinition();
    var constraint = constraint(SimpleDoubleScore.of(Math.nextUp(1.0)));
    var inliner = inliner(definition, ConstraintMatchPolicy.DISABLED, constraint);
    var impact = inliner.buildWeightedScoreImpacter(constraint).impactScore((1L << 53) + 1L, null);
    assertThat(impact.toScore()).isEqualTo(SimpleDoubleScore.of((1L << 53) + 4.0));
    assertThat(inliner.extractScore()).isEqualTo(impact.toScore());
  }

  @Test
  void doubleMatchWeightIsNotNarrowedBeforeMultiplyingFloatScore() {
    var definition = new SimpleFloatScoreDefinition();
    var constraint = constraint(SimpleFloatScore.of(Float.MIN_VALUE));
    var inliner = inliner(definition, ConstraintMatchPolicy.DISABLED, constraint);
    var impact = inliner.buildWeightedScoreImpacter(constraint).impactScore(1.0e40d, null);
    var expected =
        new BigDecimal((double) Float.MIN_VALUE).multiply(new BigDecimal(1.0e40d)).floatValue();
    assertThat(impact.toScore()).isEqualTo(SimpleFloatScore.of(expected));
    assertThat(inliner.extractScore()).isEqualTo(impact.toScore());
  }

  @Test
  void existingNumericTypesRejectFloatingMatchWeights() {
    var longConstraint = constraint(SimpleScore.ONE);
    var longInliner =
        new SimpleScoreInliner(
            Map.of(longConstraint, SimpleScore.ONE), ConstraintMatchPolicy.DISABLED);
    var longImpacter = longInliner.buildWeightedScoreImpacter(longConstraint);
    var decimalConstraint = constraint(SimpleBigDecimalScore.ONE);
    var decimalInliner =
        new SimpleBigDecimalScoreInliner(
            Map.of(decimalConstraint, SimpleBigDecimalScore.ONE), ConstraintMatchPolicy.DISABLED);
    var decimalImpacter = decimalInliner.buildWeightedScoreImpacter(decimalConstraint);
    for (var impacter : List.of(longImpacter, decimalImpacter)) {
      assertThatThrownBy(() -> impacter.impactScore(0.5f, null))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> impacter.impactScore(0.5d, null))
          .isInstanceOf(UnsupportedOperationException.class);
    }
    assertThat(longInliner.extractScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(decimalInliner.extractScore()).isEqualTo(SimpleBigDecimalScore.ZERO);
  }

  private static <Score_ extends Score<Score_>> TestConstraint<TestdataSolution, Score_> constraint(
      Score_ weight) {
    return constraint("floating", weight);
  }

  private static <Score_ extends Score<Score_>> TestConstraint<TestdataSolution, Score_> constraint(
      String id, Score_ weight) {
    return new TestConstraint<>(
        new TestConstraintFactory<>(TestdataSolution.buildSolutionDescriptor()), id, weight);
  }

  private static <Score_ extends Score<Score_>> AbstractScoreInliner<Score_> inliner(
      ScoreDefinition<Score_> definition,
      ConstraintMatchPolicy policy,
      TestConstraint<?, Score_> constraint) {
    Map<Constraint, Score_> weights = Map.of(constraint, constraint.getConstraintWeight());
    return AbstractScoreInliner.buildScoreInliner(definition, weights, policy);
  }

  private static <Score_ extends Score<Score_>> ConstraintMatchSupplier<Score_> supplier(
      ConstraintMatchPolicy policy) {
    return policy.isEnabled() ? ConstraintMatchSupplier.empty() : null;
  }

  private static Number number(ScoreDefinition<?> definition, double value) {
    if (definition.getNumericType() == float.class) {
      return (float) value;
    }
    return value;
  }

  private static BigDecimal rounded(ScoreDefinition<?> definition, BigDecimal value) {
    return new BigDecimal(
        definition.getNumericType() == float.class
            ? (double) value.floatValue()
            : value.doubleValue());
  }

  private static <Score_ extends Score<Score_>> Score_ score(
      ScoreDefinition<Score_> definition, double value) {
    var levels = new Number[definition.getLevelsSize()];
    java.util.Arrays.fill(levels, number(definition, value));
    return definition.fromLevelNumbers(levels);
  }

  private static <Score_ extends Score<Score_>> void assertMatches(
      AbstractScoreInliner<Score_> inliner,
      Constraint constraint,
      ConstraintMatchPolicy policy,
      Score_ expected,
      int count) {
    if (policy.isEnabled()) {
      var total = inliner.getConstraintMatchTotalMap().get(constraint.getConstraintRef());
      assertThat(total.getScore()).isEqualTo(expected);
      assertThat(total.getConstraintMatchCount()).isEqualTo(count);
    } else {
      assertThatThrownBy(inliner::getConstraintMatchTotalMap)
          .isInstanceOf(IllegalStateException.class);
    }
  }
}
