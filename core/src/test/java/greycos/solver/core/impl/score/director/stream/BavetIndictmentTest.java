package greycos.solver.core.impl.score.director.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleBigDecimalScore;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintFactory;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class BavetIndictmentTest {

  @Test
  void defaultTerminalObjectsAreDeduplicatedPerOccurrenceAtEveryArity() {
    try (var director =
        director(
            factory -> {
              var uni = factory.forEach(TestdataEntity.class);
              var bi = uni.join(TestdataEntity.class);
              var tri = bi.join(TestdataEntity.class);
              var quad = tri.join(TestdataEntity.class);
              return new Constraint[] {
                uni.penalize(SimpleScore.ONE).asConstraint("uni"),
                bi.penalize(SimpleScore.ONE).asConstraint("bi"),
                tri.penalize(SimpleScore.ONE).asConstraint("tri"),
                quad.penalize(SimpleScore.ONE).asConstraint("quad")
              };
            },
            ConstraintMatchPolicy.ENABLED_WITH_INDICTMENTS)) {
      var solution = TestdataSolution.generateSolution(1, 2);
      director.setWorkingSolution(solution);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-30));
      var maps = director.getIndictmentMaps();
      assertThat(maps.original()).isNull();
      assertThat(maps.terminal()).containsOnlyKeys(solution.getEntityList().toArray());
      for (var entity : solution.getEntityList()) {
        var indictment = maps.terminal().get(entity);
        assertThat(indictment.score()).isEqualTo(SimpleScore.of(-26));
        assertThat(indictment.matchCount()).isEqualTo(26);
        assertThat(indictment.matches()).hasSize(26);
      }
      assertThatThrownBy(() -> maps.terminal().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Test
  void customSelectionReplacesDefaultsAndIsLazyAtEveryArity() {
    var calls = new AtomicInteger();
    var target = new Key("target");
    Function<TestdataEntity, Collection<?>> mapping =
        a -> {
          calls.incrementAndGet();
          return List.of(target, new Key("target"), target);
        };
    try (var director =
        director(
            factory -> {
              var uni = factory.forEach(TestdataEntity.class);
              var bi = uni.join(TestdataEntity.class);
              var tri = bi.join(TestdataEntity.class);
              var quad = tri.join(TestdataEntity.class);
              return new Constraint[] {
                uni.penalize(SimpleScore.ONE).indictWith(mapping).asConstraint("uni"),
                bi.penalize(SimpleScore.ONE)
                    .indictWith((a, b) -> mapping.apply(a))
                    .asConstraint("bi"),
                tri.penalize(SimpleScore.ONE)
                    .indictWith((a, b, c) -> mapping.apply(a))
                    .asConstraint("tri"),
                quad.penalize(SimpleScore.ONE)
                    .indictWith((a, b, c, d) -> mapping.apply(a))
                    .asConstraint("quad")
              };
            },
            ConstraintMatchPolicy.ENABLED_WITH_INDICTMENTS)) {
      director.setWorkingSolution(TestdataSolution.generateSolution(1, 2));
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-30));
      director.getConstraintMatchTotalMap();
      assertThat(calls).hasValue(0);
      var first = director.getIndictmentMaps();
      assertThat(first.terminal()).containsOnlyKeys(target);
      assertThat(first.terminal().get(target).score()).isEqualTo(SimpleScore.of(-30));
      assertThat(first.terminal().get(target).matchCount()).isEqualTo(30);
      assertThat(calls).hasValue(30);
      director.getIndictmentMaps();
      assertThat(calls).hasValue(30);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = ConstraintMatchPolicy.class,
      names = {"DISABLED", "ENABLED_WITHOUT_JUSTIFICATIONS", "ENABLED"})
  void legacyModesNeverInvokeIndictmentCallbacks(ConstraintMatchPolicy policy) {
    try (var director =
        director(
            factory ->
                new Constraint[] {
                  factory
                      .forEach(TestdataEntity.class)
                      .penalize(SimpleScore.ONE)
                      .indictWith(
                          entity -> {
                            throw new AssertionError(
                                "Indictment callback executed without a request.");
                          })
                      .asConstraint("offpath")
                },
            policy)) {
      director.setWorkingSolution(TestdataSolution.generateSolution(1, 2));
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-2));
      if (policy.isEnabled()) {
        director.getConstraintMatchTotalMap();
      }
      assertThat(director.getIndictmentMaps().terminal()).isNull();
      assertThat(director.getIndictmentMaps().original()).isNull();
    }
  }

  @Test
  void nullTupleSlotsAreExcludedAndEmptyCustomSelectionIsValid() {
    try (var director =
        director(
            factory ->
                new Constraint[] {
                  factory
                      .forEach(TestdataEntity.class)
                      .expand(entity -> (Object) null)
                      .penalize(SimpleScore.ONE)
                      .asConstraint("default"),
                  factory
                      .forEach(TestdataEntity.class)
                      .penalize(SimpleScore.ONE)
                      .indictWith(entity -> List.of())
                      .asConstraint("empty")
                },
            ConstraintMatchPolicy.ENABLED_WITH_INDICTMENTS)) {
      var solution = TestdataSolution.generateSolution(1, 2);
      director.setWorkingSolution(solution);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-4));
      var maps = director.getIndictmentMaps();
      assertThat(maps.terminal()).hasSize(2);
      for (var entity : solution.getEntityList()) {
        assertThat(maps.terminal().get(entity).score()).isEqualTo(SimpleScore.of(-1));
        assertThat(maps.terminal().get(entity).matchCount()).isOne();
      }
    }
  }

  @Test
  void invalidCustomResultsFailOnlyOnRequestedExtraction() {
    for (Function<TestdataEntity, Collection<?>> mapping :
        List.<Function<TestdataEntity, Collection<?>>>of(
            entity -> null, entity -> Arrays.asList(entity, null))) {
      try (var director =
          director(
              factory ->
                  new Constraint[] {
                    factory
                        .forEach(TestdataEntity.class)
                        .penalize(SimpleScore.ONE)
                        .indictWith(mapping)
                        .asConstraint("invalid")
                  },
              ConstraintMatchPolicy.ENABLED_WITH_INDICTMENTS)) {
        director.setWorkingSolution(TestdataSolution.generateSolution(1, 1));
        assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-1));
        director.getConstraintMatchTotalMap();
        assertThatThrownBy(director::getIndictmentMaps)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("invalid")
            .hasMessageContaining("indicted objects")
            .hasCauseInstanceOf(NullPointerException.class);
      }
    }
  }

  @Test
  void customTargetsDoNotReplaceOriginalSourcesAndSnapshotsSurviveRetraction() {
    var target = new Key("target");
    try (var director =
        director(
            factory ->
                new Constraint[] {
                  factory
                      .forEach(TestdataEntity.class)
                      .penalize(SimpleScore.ONE)
                      .indictWith(entity -> List.of(target))
                      .asConstraint("mapped")
                },
            ConstraintMatchPolicy.ENABLED_WITH_ORIGINAL_INDICTMENTS)) {
      var solution = TestdataSolution.generateSolution(1, 2);
      director.setWorkingSolution(solution);
      director.calculateScore();
      var first = director.getIndictmentMaps();
      assertThat(first.terminal()).containsOnlyKeys(target);
      assertThat(first.original()).containsOnlyKeys(solution.getEntityList().toArray());
      var removed = solution.getEntityList().getFirst();
      director.beforeEntityRemoved(removed);
      solution.getEntityList().remove(removed);
      director.afterEntityRemoved(removed);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-1));
      var second = director.getIndictmentMaps();
      assertThat(second.terminal().get(target).matchCount()).isOne();
      assertThat(second.original()).containsOnlyKeys(solution.getEntityList().getFirst());
      assertThat(first.terminal().get(target).matchCount()).isEqualTo(2);
      assertThat(first.original()).containsKey(removed);
    }
  }

  @Test
  void requestedViewsRemainAvailableWhenThereAreNoMatches() {
    for (var policy :
        List.of(
            ConstraintMatchPolicy.ENABLED_WITH_INDICTMENTS,
            ConstraintMatchPolicy.ENABLED_WITH_ORIGINAL_INDICTMENTS)) {
      try (var director =
          director(
              factory ->
                  new Constraint[] {
                    factory
                        .forEach(TestdataEntity.class)
                        .filter(entity -> false)
                        .penalize(SimpleScore.ONE)
                        .indictWith(
                            entity -> {
                              throw new AssertionError("No match can request a target.");
                            })
                        .asConstraint("empty")
                  },
              policy)) {
        director.setWorkingSolution(TestdataSolution.generateSolution(1, 1));
        assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.ZERO);
        var maps = director.getIndictmentMaps();
        assertThat(maps.terminal()).isEmpty();
        if (policy == ConstraintMatchPolicy.ENABLED_WITH_ORIGINAL_INDICTMENTS) {
          assertThat(maps.original()).isEmpty();
        } else {
          assertThat(maps.original()).isNull();
        }
      }
    }
  }

  @Test
  void buildersRejectMissingAndRepeatedMappings() {
    var factory =
        new BavetConstraintFactory<>(
            TestdataSolution.buildSolutionDescriptor(), EnvironmentMode.NO_ASSERT);
    var uni = factory.forEach(TestdataEntity.class);
    var bi = uni.join(TestdataEntity.class);
    var tri = bi.join(TestdataEntity.class);
    var quad = tri.join(TestdataEntity.class);
    var uniBuilder = uni.penalize(SimpleScore.ONE);
    var biBuilder = bi.penalize(SimpleScore.ONE);
    var triBuilder = tri.penalize(SimpleScore.ONE);
    var quadBuilder = quad.penalize(SimpleScore.ONE);
    assertThatThrownBy(() -> uniBuilder.indictWith(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> biBuilder.indictWith(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> triBuilder.indictWith(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> quadBuilder.indictWith(null)).isInstanceOf(NullPointerException.class);
    uniBuilder.indictWith(a -> List.of());
    biBuilder.indictWith((a, b) -> List.of());
    triBuilder.indictWith((a, b, c) -> List.of());
    quadBuilder.indictWith((a, b, c, d) -> List.of());
    assertThatThrownBy(() -> uniBuilder.indictWith(a -> List.of()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> biBuilder.indictWith((a, b) -> List.of()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> triBuilder.indictWith((a, b, c) -> List.of()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> quadBuilder.indictWith((a, b, c, d) -> List.of()))
        .isInstanceOf(IllegalStateException.class);
  }

  static Stream<Arguments> numericModes() {
    return Stream.of(
        Arguments.of(new FloatSolution(), SimpleFloatScore.ONE, "float"),
        Arguments.of(new DoubleSolution(), SimpleDoubleScore.ONE, "double"),
        Arguments.of(new DecimalSolution(), SimpleBigDecimalScore.ONE, "decimal"));
  }

  @ParameterizedTest
  @MethodSource("numericModes")
  @SuppressWarnings({"unchecked", "rawtypes"})
  void numericHandlersRetainCustomSelections(
      NumericSolution solution, Score<?> weight, String mode) {
    checkNumericHandlers(solution, (Score) weight, mode);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <S extends Score<S>> void checkNumericHandlers(
      NumericSolution solution, S weight, String mode) {
    var target = new Key("numeric target");
    ConstraintProvider provider =
        factory -> {
          var uni = factory.forEach(TestdataEntity.class);
          var bi = uni.join(TestdataEntity.class);
          var tri = bi.join(TestdataEntity.class);
          var quad = tri.join(TestdataEntity.class);
          var uniBuilder =
              switch (mode) {
                case "float" -> uni.penalizeFloat(weight, a -> 0.5F);
                case "double" -> uni.penalizeDouble(weight, a -> 0.5D);
                default -> uni.penalizeBigDecimal(weight, a -> new BigDecimal("0.5"));
              };
          var biBuilder =
              switch (mode) {
                case "float" -> bi.penalizeFloat(weight, (a, b) -> 0.5F);
                case "double" -> bi.penalizeDouble(weight, (a, b) -> 0.5D);
                default -> bi.penalizeBigDecimal(weight, (a, b) -> new BigDecimal("0.5"));
              };
          var triBuilder =
              switch (mode) {
                case "float" -> tri.penalizeFloat(weight, (a, b, c) -> 0.5F);
                case "double" -> tri.penalizeDouble(weight, (a, b, c) -> 0.5D);
                default -> tri.penalizeBigDecimal(weight, (a, b, c) -> new BigDecimal("0.5"));
              };
          var quadBuilder =
              switch (mode) {
                case "float" -> quad.penalizeFloat(weight, (a, b, c, d) -> 0.5F);
                case "double" -> quad.penalizeDouble(weight, (a, b, c, d) -> 0.5D);
                default -> quad.penalizeBigDecimal(weight, (a, b, c, d) -> new BigDecimal("0.5"));
              };
          return new Constraint[] {
            uniBuilder.indictWith(a -> List.of(target)).asConstraint("uni"),
            biBuilder.indictWith((a, b) -> List.of(target)).asConstraint("bi"),
            triBuilder.indictWith((a, b, c) -> List.of(target)).asConstraint("tri"),
            quadBuilder.indictWith((a, b, c, d) -> List.of(target)).asConstraint("quad")
          };
        };
    SolutionDescriptor<NumericSolution> descriptor =
        (SolutionDescriptor)
            SolutionDescriptor.buildSolutionDescriptor(solution.getClass(), TestdataEntity.class);
    try (var director =
        new BavetConstraintStreamScoreDirectorFactory<NumericSolution, S>(
                descriptor, provider, EnvironmentMode.NO_ASSERT)
            .createScoreDirectorBuilder()
            .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED_WITH_INDICTMENTS)
            .build()) {
      director.setWorkingSolution(solution);
      assertThat(director.calculateScore().raw()).isEqualTo(weight.multiply(-2));
      var maps = director.getIndictmentMaps();
      assertThat(maps.terminal()).containsOnlyKeys(target);
      assertThat(maps.terminal().get(target).score()).isEqualTo(weight.multiply(-2));
      assertThat(maps.terminal().get(target).matchCount()).isEqualTo(4);
    }
  }

  @PlanningSolution
  public abstract static class NumericSolution {
    @ValueRangeProvider(id = "valueRange")
    @ProblemFactCollectionProperty
    public List<TestdataValue> values = List.of(new TestdataValue("value"));

    @PlanningEntityCollectionProperty
    public List<TestdataEntity> entities = List.of(new TestdataEntity("entity", values.getFirst()));
  }

  @PlanningSolution
  public static class FloatSolution extends NumericSolution {
    @PlanningScore public SimpleFloatScore score;
  }

  @PlanningSolution
  public static class DoubleSolution extends NumericSolution {
    @PlanningScore public SimpleDoubleScore score;
  }

  @PlanningSolution
  public static class DecimalSolution extends NumericSolution {
    @PlanningScore public SimpleBigDecimalScore score;
  }

  private static BavetConstraintStreamScoreDirector<TestdataSolution, SimpleScore> director(
      ConstraintProvider provider, ConstraintMatchPolicy policy) {
    return new BavetConstraintStreamScoreDirectorFactory<TestdataSolution, SimpleScore>(
            TestdataSolution.buildSolutionDescriptor(), provider, EnvironmentMode.NO_ASSERT)
        .createScoreDirectorBuilder()
        .withConstraintMatchPolicy(policy)
        .build();
  }

  private record Key(String id) {}
}
