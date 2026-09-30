package greycos.solver.core.impl.score.stream.bavet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.stream.common.ScoreImpactType;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class BavetFloatingConstraintStreamTest {

  enum InputKind {
    LONG,
    FLOAT,
    DOUBLE,
    UNIT_FLOAT,
    UNIT_DOUBLE
  }

  static Stream<Arguments> scoringCases() {
    return Stream.of(ConstraintMatchPolicy.values())
        .flatMap(
            policy ->
                IntStream.rangeClosed(1, 4)
                    .boxed()
                    .flatMap(
                        arity ->
                            Stream.of(InputKind.values())
                                .flatMap(
                                    inputKind ->
                                        Stream.of(ScoreImpactType.values())
                                            .flatMap(
                                                operation ->
                                                    Stream.of(false, true)
                                                        .map(
                                                            floatPrecision ->
                                                                Arguments.of(
                                                                    policy,
                                                                    arity,
                                                                    inputKind,
                                                                    operation,
                                                                    floatPrecision))))));
  }

  @ParameterizedTest
  @MethodSource("scoringCases")
  void scoreUpdateAndRetract(
      ConstraintMatchPolicy policy,
      int arity,
      InputKind inputKind,
      ScoreImpactType operation,
      boolean floatPrecision) {
    if (floatPrecision) {
      verifyScoreUpdateAndRetract(
          FloatSolution.class,
          new FloatSolution(),
          SimpleFloatScore.of(0.25F),
          value -> SimpleFloatScore.of(value.floatValue()),
          policy,
          arity,
          inputKind,
          operation);
    } else {
      verifyScoreUpdateAndRetract(
          DoubleSolution.class,
          new DoubleSolution(),
          SimpleDoubleScore.of(0.25D),
          SimpleDoubleScore::of,
          policy,
          arity,
          inputKind,
          operation);
    }
  }

  private static <Solution_ extends FloatingSolution, Score_ extends Score<Score_>>
      void verifyScoreUpdateAndRetract(
          Class<Solution_> solutionClass,
          Solution_ solution,
          Score_ constraintWeight,
          Function<Double, Score_> scoreFactory,
          ConstraintMatchPolicy policy,
          int arity,
          InputKind inputKind,
          ScoreImpactType operation) {
    var first = new WeightedFact(operation == ScoreImpactType.MIXED ? -2.5D : 2.5D);
    var second = new WeightedFact(3.5D);
    solution.facts.add(first);
    solution.facts.add(second);
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(solutionClass, TestdataEntity.class);
    try (InnerScoreDirector<Solution_, Score_> director =
        new BavetConstraintStreamImplSupport(policy)
            .buildScoreDirector(
                descriptor,
                factory ->
                    new Constraint[] {
                      buildConstraint(factory, constraintWeight, arity, inputKind, operation)
                    })) {
      director.setWorkingSolution(solution);
      assertScoreAndMatches(
          director,
          scoreFactory.apply(expected(inputKind, operation, first, second)),
          policy,
          arity,
          2);
      director.beforeProblemPropertyChanged(second);
      second.weight = 5.5D;
      director.afterProblemPropertyChanged(second);
      assertScoreAndMatches(
          director,
          scoreFactory.apply(expected(inputKind, operation, first, second)),
          policy,
          arity,
          2);
      director.beforeProblemFactRemoved(first);
      solution.facts.remove(first);
      director.afterProblemFactRemoved(first);
      assertScoreAndMatches(
          director, scoreFactory.apply(expected(inputKind, operation, second)), policy, arity, 1);
      director.beforeProblemFactRemoved(second);
      solution.facts.remove(second);
      director.afterProblemFactRemoved(second);
      assertScoreAndMatches(director, constraintWeight.zero(), policy, arity, 0);
    }
  }

  private static double expected(
      InputKind inputKind, ScoreImpactType operation, WeightedFact... facts) {
    double result = 0D;
    for (var fact : facts) {
      result +=
          switch (inputKind) {
            case LONG -> (long) fact.weight;
            case FLOAT, DOUBLE -> fact.weight;
            case UNIT_FLOAT, UNIT_DOUBLE -> 1D;
          };
    }
    return (operation == ScoreImpactType.PENALTY ? -0.25D : 0.25D) * result;
  }

  private static <Solution_, Score_ extends Score<Score_>> void assertScoreAndMatches(
      InnerScoreDirector<Solution_, Score_> director,
      Score_ expected,
      ConstraintMatchPolicy policy,
      int arity,
      int matchCount) {
    assertThat(director.calculateScore().raw()).isEqualTo(expected);
    if (policy.isEnabled()) {
      var total = director.getConstraintMatchTotalMap().values().iterator().next();
      assertThat(total.getScore()).isEqualTo(expected);
      assertThat(total.getConstraintMatchCount()).isEqualTo(matchCount);
      if (policy.isJustificationEnabled()) {
        for (var match : total.getConstraintMatchSet()) {
          assertThat((Object) match.getJustification()).isInstanceOf(FloatingJustification.class);
          var justification = (FloatingJustification) match.getJustification();
          assertThat(justification.score()).isEqualTo(match.getScore());
          assertThat(justification.facts()).hasSize(arity);
        }
      }
    }
  }

  private static <Score_ extends Score<Score_>> Constraint buildConstraint(
      ConstraintFactory factory,
      Score_ weight,
      int arity,
      InputKind inputKind,
      ScoreImpactType operation) {
    var uni = factory.forEach(WeightedFact.class);
    return switch (arity) {
      case 1 -> {
        var stream = uni;
        var builder =
            switch (inputKind) {
              case LONG ->
                  switch (operation) {
                    case PENALTY -> stream.penalize(weight, (a) -> (long) a.weight);
                    case REWARD -> stream.reward(weight, (a) -> (long) a.weight);
                    case MIXED -> stream.impact(weight, (a) -> (long) a.weight);
                  };
              case FLOAT ->
                  switch (operation) {
                    case PENALTY -> stream.penalizeFloat(weight, (a) -> (float) a.weight);
                    case REWARD -> stream.rewardFloat(weight, (a) -> (float) a.weight);
                    case MIXED -> stream.impactFloat(weight, (a) -> (float) a.weight);
                  };
              case DOUBLE ->
                  switch (operation) {
                    case PENALTY -> stream.penalizeDouble(weight, (a) -> a.weight);
                    case REWARD -> stream.rewardDouble(weight, (a) -> a.weight);
                    case MIXED -> stream.impactDouble(weight, (a) -> a.weight);
                  };
              case UNIT_FLOAT ->
                  switch (operation) {
                    case PENALTY -> stream.penalizeFloat(weight);
                    case REWARD -> stream.rewardFloat(weight);
                    case MIXED -> stream.impactFloat(weight);
                  };
              case UNIT_DOUBLE ->
                  switch (operation) {
                    case PENALTY -> stream.penalizeDouble(weight);
                    case REWARD -> stream.rewardDouble(weight);
                    case MIXED -> stream.impactDouble(weight);
                  };
            };
        yield builder
            .justifyWith((a, score) -> new FloatingJustification(score, List.of(a)))
            .asConstraint("floating");
      }
      case 2 -> {
        var stream = uni.expand(a -> a);
        var builder =
            switch (inputKind) {
              case LONG ->
                  switch (operation) {
                    case PENALTY -> stream.penalize(weight, (a, b) -> (long) a.weight);
                    case REWARD -> stream.reward(weight, (a, b) -> (long) a.weight);
                    case MIXED -> stream.impact(weight, (a, b) -> (long) a.weight);
                  };
              case FLOAT ->
                  switch (operation) {
                    case PENALTY -> stream.penalizeFloat(weight, (a, b) -> (float) a.weight);
                    case REWARD -> stream.rewardFloat(weight, (a, b) -> (float) a.weight);
                    case MIXED -> stream.impactFloat(weight, (a, b) -> (float) a.weight);
                  };
              case DOUBLE ->
                  switch (operation) {
                    case PENALTY -> stream.penalizeDouble(weight, (a, b) -> a.weight);
                    case REWARD -> stream.rewardDouble(weight, (a, b) -> a.weight);
                    case MIXED -> stream.impactDouble(weight, (a, b) -> a.weight);
                  };
              case UNIT_FLOAT ->
                  switch (operation) {
                    case PENALTY -> stream.penalizeFloat(weight);
                    case REWARD -> stream.rewardFloat(weight);
                    case MIXED -> stream.impactFloat(weight);
                  };
              case UNIT_DOUBLE ->
                  switch (operation) {
                    case PENALTY -> stream.penalizeDouble(weight);
                    case REWARD -> stream.rewardDouble(weight);
                    case MIXED -> stream.impactDouble(weight);
                  };
            };
        yield builder
            .justifyWith((a, b, score) -> new FloatingJustification(score, List.of(a, b)))
            .asConstraint("floating");
      }
      case 3 -> {
        var stream = uni.expand(a -> a).expand((a, b) -> a);
        var builder =
            switch (inputKind) {
              case LONG ->
                  switch (operation) {
                    case PENALTY -> stream.penalize(weight, (a, b, c) -> (long) a.weight);
                    case REWARD -> stream.reward(weight, (a, b, c) -> (long) a.weight);
                    case MIXED -> stream.impact(weight, (a, b, c) -> (long) a.weight);
                  };
              case FLOAT ->
                  switch (operation) {
                    case PENALTY -> stream.penalizeFloat(weight, (a, b, c) -> (float) a.weight);
                    case REWARD -> stream.rewardFloat(weight, (a, b, c) -> (float) a.weight);
                    case MIXED -> stream.impactFloat(weight, (a, b, c) -> (float) a.weight);
                  };
              case DOUBLE ->
                  switch (operation) {
                    case PENALTY -> stream.penalizeDouble(weight, (a, b, c) -> a.weight);
                    case REWARD -> stream.rewardDouble(weight, (a, b, c) -> a.weight);
                    case MIXED -> stream.impactDouble(weight, (a, b, c) -> a.weight);
                  };
              case UNIT_FLOAT ->
                  switch (operation) {
                    case PENALTY -> stream.penalizeFloat(weight);
                    case REWARD -> stream.rewardFloat(weight);
                    case MIXED -> stream.impactFloat(weight);
                  };
              case UNIT_DOUBLE ->
                  switch (operation) {
                    case PENALTY -> stream.penalizeDouble(weight);
                    case REWARD -> stream.rewardDouble(weight);
                    case MIXED -> stream.impactDouble(weight);
                  };
            };
        yield builder
            .justifyWith((a, b, c, score) -> new FloatingJustification(score, List.of(a, b, c)))
            .asConstraint("floating");
      }
      case 4 -> {
        var stream = uni.expand(a -> a).expand((a, b) -> a).expand((a, b, c) -> a);
        var builder =
            switch (inputKind) {
              case LONG ->
                  switch (operation) {
                    case PENALTY -> stream.penalize(weight, (a, b, c, d) -> (long) a.weight);
                    case REWARD -> stream.reward(weight, (a, b, c, d) -> (long) a.weight);
                    case MIXED -> stream.impact(weight, (a, b, c, d) -> (long) a.weight);
                  };
              case FLOAT ->
                  switch (operation) {
                    case PENALTY -> stream.penalizeFloat(weight, (a, b, c, d) -> (float) a.weight);
                    case REWARD -> stream.rewardFloat(weight, (a, b, c, d) -> (float) a.weight);
                    case MIXED -> stream.impactFloat(weight, (a, b, c, d) -> (float) a.weight);
                  };
              case DOUBLE ->
                  switch (operation) {
                    case PENALTY -> stream.penalizeDouble(weight, (a, b, c, d) -> a.weight);
                    case REWARD -> stream.rewardDouble(weight, (a, b, c, d) -> a.weight);
                    case MIXED -> stream.impactDouble(weight, (a, b, c, d) -> a.weight);
                  };
              case UNIT_FLOAT ->
                  switch (operation) {
                    case PENALTY -> stream.penalizeFloat(weight);
                    case REWARD -> stream.rewardFloat(weight);
                    case MIXED -> stream.impactFloat(weight);
                  };
              case UNIT_DOUBLE ->
                  switch (operation) {
                    case PENALTY -> stream.penalizeDouble(weight);
                    case REWARD -> stream.rewardDouble(weight);
                    case MIXED -> stream.impactDouble(weight);
                  };
            };
        yield builder
            .justifyWith(
                (a, b, c, d, score) -> new FloatingJustification(score, List.of(a, b, c, d)))
            .asConstraint("floating");
      }
      default -> throw new IllegalArgumentException("Unsupported arity (" + arity + ").");
    };
  }

  @ParameterizedTest
  @EnumSource(ConstraintMatchPolicy.class)
  void doubleMatchWeightIsNotNarrowedBeforeFloatProduct(ConstraintMatchPolicy policy) {
    var solution = new FloatSolution();
    var fact = new WeightedFact(0x1.0p128);
    solution.facts.add(fact);
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(FloatSolution.class, TestdataEntity.class);
    try (InnerScoreDirector<FloatSolution, SimpleFloatScore> director =
        new BavetConstraintStreamImplSupport(policy)
            .buildScoreDirector(
                descriptor,
                factory ->
                    new Constraint[] {
                      factory
                          .forEach(WeightedFact.class)
                          .rewardDouble(SimpleFloatScore.of(Float.MIN_VALUE), a -> a.weight)
                          .asConstraint("cross-precision")
                    })) {
      director.setWorkingSolution(solution);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleFloatScore.of(0x1.0p-21F));
      if (policy.isEnabled()) {
        assertThat(director.getConstraintMatchTotalMap().values().iterator().next().getScore())
            .isEqualTo(SimpleFloatScore.of(0x1.0p-21F));
      }
      director.beforeProblemFactRemoved(fact);
      solution.facts.remove(fact);
      director.afterProblemFactRemoved(fact);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleFloatScore.ZERO);
    }
  }

  static Stream<Arguments> invalidWeights() {
    return Stream.of(InputKind.FLOAT, InputKind.DOUBLE)
        .flatMap(
            inputKind ->
                Stream.of(ScoreImpactType.values())
                    .flatMap(
                        operation ->
                            Stream.of(
                                    Double.NaN,
                                    Double.POSITIVE_INFINITY,
                                    Double.NEGATIVE_INFINITY,
                                    -1D)
                                .filter(value -> operation != ScoreImpactType.MIXED || value != -1D)
                                .map(value -> Arguments.of(inputKind, operation, value))));
  }

  @ParameterizedTest
  @MethodSource("invalidWeights")
  void rejectsNonFiniteAndNegativeUnsignedWeights(
      InputKind inputKind, ScoreImpactType operation, double value) {
    var solution = new FloatSolution();
    solution.facts.add(new WeightedFact(value));
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(FloatSolution.class, TestdataEntity.class);
    try (InnerScoreDirector<FloatSolution, SimpleFloatScore> director =
        new BavetConstraintStreamImplSupport(ConstraintMatchPolicy.DISABLED)
            .buildScoreDirector(
                descriptor,
                factory ->
                    new Constraint[] {
                      buildConstraint(factory, SimpleFloatScore.ONE, 1, inputKind, operation)
                    })) {
      assertThatThrownBy(
              () -> {
                director.setWorkingSolution(solution);
                director.calculateScore();
              })
          .isInstanceOf(IllegalStateException.class)
          .hasStackTraceContaining("floating")
          .hasRootCauseMessage(
              (Double.isFinite(value) ? "Negative" : "Non-finite")
                  + " match weight ("
                  + value
                  + ") for constraint (floating). Check constraint provider implementation.");
    }
  }

  @Test
  void bigDecimalMatchWeightStillRequiresDecimalScore() {
    var solution = new FloatSolution();
    solution.facts.add(new WeightedFact(1D));
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(FloatSolution.class, TestdataEntity.class);
    try (InnerScoreDirector<FloatSolution, SimpleFloatScore> director =
        new BavetConstraintStreamImplSupport(ConstraintMatchPolicy.DISABLED)
            .buildScoreDirector(
                descriptor,
                factory ->
                    new Constraint[] {
                      factory
                          .forEach(WeightedFact.class)
                          .rewardBigDecimal(SimpleFloatScore.ONE, a -> BigDecimal.ONE)
                          .asConstraint("decimal")
                    })) {
      assertThatThrownBy(
              () -> {
                director.setWorkingSolution(solution);
                director.calculateScore();
              })
          .hasRootCauseInstanceOf(UnsupportedOperationException.class)
          .hasStackTraceContaining("BigDecimal match weight");
    }
  }

  @Test
  void floatingMatchWeightRejectsIntegralScore() {
    var solution = TestdataSolution.generateSolution(1, 1);
    try (InnerScoreDirector<TestdataSolution, SimpleScore> director =
        new BavetConstraintStreamImplSupport(ConstraintMatchPolicy.DISABLED)
            .buildScoreDirector(
                TestdataSolution.buildSolutionDescriptor(),
                factory ->
                    new Constraint[] {
                      factory
                          .forEach(TestdataEntity.class)
                          .rewardDouble(SimpleScore.ONE, a -> 0.5D)
                          .asConstraint("integral")
                    })) {
      assertThatThrownBy(
              () -> {
                director.setWorkingSolution(solution);
                director.calculateScore();
              })
          .hasRootCauseInstanceOf(UnsupportedOperationException.class);
    }
  }

  record FloatingJustification(Score<?> score, List<?> facts) implements ConstraintJustification {}

  public static final class WeightedFact {
    double weight;

    WeightedFact(double weight) {
      this.weight = weight;
    }
  }

  @PlanningSolution
  public abstract static class FloatingSolution {
    @ProblemFactCollectionProperty public List<WeightedFact> facts = new ArrayList<>();

    @ValueRangeProvider(id = "valueRange")
    @ProblemFactCollectionProperty
    public List<TestdataValue> values = List.of(new TestdataValue("value"));

    @PlanningEntityCollectionProperty
    public List<TestdataEntity> entities = List.of(new TestdataEntity("entity", values.getFirst()));
  }

  @PlanningSolution
  public static class FloatSolution extends FloatingSolution {
    @PlanningScore public SimpleFloatScore score;
  }

  @PlanningSolution
  public static class DoubleSolution extends FloatingSolution {
    @PlanningScore public SimpleDoubleScore score;
  }
}
