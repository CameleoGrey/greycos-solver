package greycos.solver.core.impl.score.director.incremental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.MatchOccurrenceAnalysis;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.calculator.AnalyzableIncrementalScoreCalculator;
import greycos.solver.core.api.score.calculator.ConstraintMatchRegistration;
import greycos.solver.core.api.score.calculator.ConstraintMatchRegistry;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.solver.IndictmentFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisOptions;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.shadow.TestdataShadowedEntity;
import greycos.solver.core.testcotwin.shadow.TestdataShadowedSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A real calculator consumes shadows; the replay uses only genuine assignments. */
class IncrementalAnalysisLifecycleTest {

  private static final ConstraintRef CONSTRAINT = ConstraintRef.of("assignment cost");
  private static final Reason REASON = new Reason("assigned");
  private static final ScoreAnalysisOptions RAW_BOTH =
      options(IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL, true);

  @Test
  void retainedCalculatorInitializesAfterShadowRepairAndSurvivesChangeUndoAndReset() {
    var calculator = new ShadowDependentCalculator();
    var factory =
        new IncrementalScoreDirectorFactory<TestdataShadowedSolution, SimpleScore>(
            TestdataShadowedSolution.buildSolutionDescriptor(),
            () -> calculator,
            EnvironmentMode.PHASE_ASSERT);
    try (var director =
        factory
            .createScoreDirectorBuilder()
            .withConstraintMatchPolicy(ConstraintMatchPolicy.match(RAW_BOTH))
            .build()) {
      var solution = problem(false);
      var firstExpected = replay(solution);
      director.setWorkingSolution(solution);
      assertThat(calculator.initializationCount).isZero();
      assertThat(calculator.shadowCallbacksBeforeInitialization).isPositive();
      assertThat(director.totalScore()).isEqualTo(SimpleScore.ZERO);
      assertRepairedShadows(solution);

      var first = director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL);
      assertAnalysis(first, firstExpected, RAW_BOTH);
      assertThat(calculator.initializationCount).isOne();
      assertThat(calculator.contributionCalculationCount).isEqualTo(2);
      var unchangedRegistration = calculator.registrations.get(solution.getEntityList().getLast());
      assertAnalysis(
          director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL), firstExpected, RAW_BOTH);
      assertThat(calculator.contributionCalculationCount).isEqualTo(2);

      var changed = solution.getEntityList().getFirst();
      var originalValue = changed.getValue();
      changeValue(director, changed, solution.getValueList().getLast());
      assertAnalysis(
          director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL),
          replay(solution),
          RAW_BOTH);
      assertThat(calculator.registrations.get(solution.getEntityList().getLast()))
          .isSameAs(unchangedRegistration);
      assertThat(calculator.contributionCalculationCount).isEqualTo(3);
      assertThat(calculator.resetCount).isOne();
      assertThat(calculator.shadowCallbacksAfterInitialization).isPositive();
      assertAnalysis(first, firstExpected, RAW_BOTH);

      changeValue(director, changed, originalValue);
      assertAnalysis(
          director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL), firstExpected, RAW_BOTH);
      assertThat(calculator.contributionCalculationCount).isEqualTo(4);
      var staleRegistration = calculator.registrations.get(changed);

      // The replacement deliberately has the same IDs and different object identities.
      var replacement = problem(true);
      var replacementExpected = replay(replacement);
      director.setWorkingSolution(replacement);
      assertThat(calculator.resetCount).isEqualTo(2);
      assertThat(calculator.initializationCount).isOne();
      assertThat(director.totalScore()).isEqualTo(SimpleScore.ZERO);
      assertRepairedShadows(replacement);
      var replaced = director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL);
      assertAnalysis(replaced, replacementExpected, RAW_BOTH);
      assertThatThrownBy(staleRegistration::cancel)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("resetting the working solution");
      assertThat(director.totalScore()).isEqualTo(replaced.score());
      assertAnalysis(
          director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL),
          replacementExpected,
          RAW_BOTH);
      assertAnalysis(first, firstExpected, RAW_BOTH);
      assertThat(replaced.originalIndictmentMap().keySet())
          .noneMatch(first.originalIndictmentMap()::containsKey);

      var resetRegistration = calculator.registrations.get(replacement.getEntityList().getFirst());
      director.setWorkingSolution(replacement);
      assertThat(calculator.resetCount).isEqualTo(3);
      assertThatThrownBy(resetRegistration::cancel)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("resetting the working solution");
      assertAnalysis(
          director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL),
          replacementExpected,
          RAW_BOTH);
      assertThat(calculator.initializationCount).isEqualTo(3);
      assertThat(calculator.contributionCalculationCount).isEqualTo(8);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "TERMINAL, false",
    "TERMINAL, true",
    "TERMINAL_AND_ORIGINAL, false",
    "TERMINAL_AND_ORIGINAL, true"
  })
  void solutionManagerRepairsShadowsAndReturnsRequestedViews(
      IndictmentFetchPolicy policy, boolean raw) {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataShadowedSolution.class)
            .withEntityClasses(TestdataShadowedEntity.class)
            .withEnvironmentMode(EnvironmentMode.PHASE_ASSERT)
            .withScoreDirectorFactory(
                new ScoreDirectorFactoryConfig()
                    .withIncrementalScoreCalculatorClass(ShadowDependentCalculator.class));
    var manager =
        SolutionManager.<TestdataShadowedSolution, SimpleScore>create(SolverFactory.create(config));
    var solution = problem(false);
    var expected = replay(solution);
    var options = options(policy, raw);
    var first = manager.analyzeWithOptions(solution, options);
    assertAnalysis(first, expected, options);
    assertRepairedShadows(solution);
    assertThat(solution.getScore()).isEqualTo(first.score());

    solution.getEntityList().getFirst().setValue(solution.getValueList().getLast());
    solution.getEntityList().getFirst().setFirstShadow("stale");
    var updated = manager.analyzeWithOptions(solution, options);
    assertAnalysis(updated, replay(solution), options);
    assertRepairedShadows(solution);
    assertAnalysis(first, expected, options);
    var legacy = manager.analyze(solution);
    assertThat(legacy.score()).isEqualTo(updated.score());
    assertThat(legacy.indictmentMap()).isNull();
    assertThat(legacy.originalIndictmentMap()).isNull();
    assertThat(legacy.rawAnalysis()).isNull();
  }

  private static ScoreAnalysisOptions options(IndictmentFetchPolicy policy, boolean raw) {
    return new ScoreAnalysisOptions(ScoreAnalysisFetchPolicy.FETCH_ALL, policy, raw);
  }

  private static TestdataShadowedSolution problem(boolean useSecondValue) {
    var solution = new TestdataShadowedSolution("same IDs, reconstructed model");
    var values = List.of(new TestdataValue("2"), new TestdataValue("5"));
    solution.setValueList(new ArrayList<>(values));
    var selected = values.get(useSecondValue ? 1 : 0);
    var entities =
        List.of(
            new TestdataShadowedEntity("A", selected), new TestdataShadowedEntity("B", selected));
    entities.forEach(entity -> entity.setFirstShadow("stale"));
    solution.setEntityList(new ArrayList<>(entities));
    return solution;
  }

  private static void changeValue(
      IncrementalScoreDirector<TestdataShadowedSolution, SimpleScore> director,
      TestdataShadowedEntity entity,
      TestdataValue value) {
    director.beforeVariableChanged(entity, "value");
    entity.setValue(value);
    director.afterVariableChanged(entity, "value");
    director.updateShadowVariables();
    assertRepairedShadows(director.getWorkingSolution());
  }

  private static void assertRepairedShadows(TestdataShadowedSolution solution) {
    for (var entity : solution.getEntityList()) {
      assertThat(entity.getFirstShadow()).isEqualTo(entity.getValue().getCode() + "/firstShadow");
    }
  }

  private static List<ExpectedOccurrence> replay(TestdataShadowedSolution solution) {
    return solution.getEntityList().stream()
        .map(
            entity ->
                new ExpectedOccurrence(
                    new Target(entity.getCode(), entity.getValue().getCode() + "/firstShadow"),
                    -Long.parseLong(entity.getValue().getCode()),
                    entity,
                    entity.getValue()))
        .toList();
  }

  private static void assertAnalysis(
      ScoreAnalysis<SimpleScore> analysis,
      List<ExpectedOccurrence> expected,
      ScoreAnalysisOptions options) {
    var expectedScore =
        SimpleScore.of(expected.stream().mapToLong(ExpectedOccurrence::score).sum());
    assertThat(analysis.score()).isEqualTo(expectedScore);
    assertThat(analysis.constraintMap()).containsOnlyKeys(CONSTRAINT);
    var constraint = analysis.constraintMap().get(CONSTRAINT);
    assertThat(constraint.score()).isEqualTo(expectedScore);
    assertThat(constraint.matchCount()).isOne();
    assertThat(constraint.matches())
        .singleElement()
        .satisfies(
            match -> {
              assertThat(match.score()).isEqualTo(expectedScore);
              assertThat(match.justification()).isEqualTo(REASON);
            });
    var terminal = new LinkedHashMap<Object, Contribution>();
    var original = new IdentityHashMap<Object, Contribution>();
    for (var row : expected) {
      var contribution = new Contribution(row.score(), 1);
      terminal.put(row.target(), contribution);
      original.merge(row.entity(), contribution, Contribution::plus);
      original.merge(row.value(), contribution, Contribution::plus);
    }
    assertIndictments(analysis.indictmentMap(), terminal);
    boolean originals = options.indictmentPolicy() == IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL;
    if (originals) {
      assertIndictments(analysis.originalIndictmentMap(), original);
    } else {
      assertThat(analysis.originalIndictmentMap()).isNull();
    }
    if (!options.includeMatchOccurrences()) {
      assertThat(analysis.rawAnalysis()).isNull();
      assertThat(constraint.rawMatchCount()).isNull();
      return;
    }
    assertThat(constraint.rawMatchCount()).isEqualTo((long) expected.size());
    var raw = analysis.rawAnalysis();
    assertThat(raw.originalsFetched()).isEqualTo(originals);
    assertThat(raw.matchOccurrences()).hasSize(expected.size());
    assertThat(raw.constraintMap()).containsOnlyKeys(CONSTRAINT);
    assertThat(raw.constraintMap().get(CONSTRAINT))
        .containsExactlyElementsOf(raw.matchOccurrences());
    assertThat(raw.indictmentMap()).containsOnlyKeys(terminal.keySet());
    var canonical = new LinkedHashMap<Target, MatchOccurrenceAnalysis<SimpleScore>>();
    for (var occurrence : raw.matchOccurrences()) {
      var target = (Target) occurrence.indictedObjects().getFirst();
      assertThat(canonical.put(target, occurrence)).isNull();
    }
    for (var row : expected) {
      var occurrence = canonical.get(row.target());
      assertThat(occurrence).isNotNull();
      assertThat(occurrence.constraintRef()).isEqualTo(CONSTRAINT);
      assertThat(occurrence.matchScore()).isEqualTo(SimpleScore.of(row.score()));
      assertThat(occurrence.score()).isEqualTo(occurrence.matchScore());
      assertThat(occurrence.justification()).isEqualTo(REASON);
      assertThat(occurrence.occurrenceSign()).isOne();
      assertThat(occurrence.indictedObjects()).containsExactly(row.target(), row.target());
      assertThat(raw.indictmentMap().get(row.target())).containsExactly(occurrence);
      if (originals) {
        assertThat(occurrence.originalIndictedObjects()).hasSize(2);
        assertThat(occurrence.originalIndictedObjects().get(0)).isSameAs(row.entity());
        assertThat(occurrence.originalIndictedObjects().get(1)).isSameAs(row.value());
      } else {
        assertThat(occurrence.originalIndictedObjects()).isNull();
      }
    }
    if (originals) {
      assertThat(raw.originalIndictmentMap()).containsOnlyKeys(original.keySet());
      original.forEach(
          (source, contribution) -> {
            var expectedRows =
                expected.stream()
                    .filter(row -> row.entity() == source || row.value() == source)
                    .map(row -> canonical.get(row.target()))
                    .toList();
            assertThat(raw.originalIndictmentMap().get(source))
                .containsExactlyInAnyOrderElementsOf(expectedRows);
          });
    } else {
      assertThat(raw.originalIndictmentMap()).isNull();
    }
  }

  private static void assertIndictments(
      Map<Object, IndictmentAnalysis<SimpleScore>> actual, Map<Object, Contribution> expected) {
    assertThat(actual).containsOnlyKeys(expected.keySet());
    expected.forEach(
        (object, contribution) -> {
          var indictment = actual.get(object);
          assertThat(indictment.score()).isEqualTo(SimpleScore.of(contribution.score()));
          assertThat(indictment.matchCount()).isEqualTo(contribution.count());
          assertThat(indictment.matches())
              .singleElement()
              .satisfies(
                  match -> {
                    assertThat(match.constraintRef()).isEqualTo(CONSTRAINT);
                    assertThat(match.justification()).isEqualTo(REASON);
                    assertThat(match.score()).isEqualTo(indictment.score());
                    assertThat(match.matchCount()).isEqualTo(indictment.matchCount());
                  });
        });
  }

  private record Target(String entityId, String shadow) {}

  private record Reason(String text) implements ConstraintJustification {}

  private record ExpectedOccurrence(
      Target target, long score, TestdataShadowedEntity entity, TestdataValue value) {}

  private record Contribution(long score, long count) {
    private Contribution plus(Contribution other) {
      return new Contribution(score + other.score, count + other.count);
    }
  }

  public static final class ShadowDependentCalculator
      implements AnalyzableIncrementalScoreCalculator<TestdataShadowedSolution, SimpleScore> {

    private final Map<TestdataShadowedEntity, ConstraintMatchRegistration<SimpleScore>>
        registrations = new IdentityHashMap<>();
    private final Map<TestdataShadowedEntity, Long> contributions = new IdentityHashMap<>();
    private final Set<TestdataShadowedEntity> dirty =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private ConstraintMatchRegistry<SimpleScore> registry;
    private TestdataShadowedSolution solution;
    private boolean initialized;
    private long total;
    private int resetCount;
    private int initializationCount;
    private int contributionCalculationCount;
    private int shadowCallbacksBeforeInitialization;
    private int shadowCallbacksAfterInitialization;

    @Override
    public void enableConstraintMatch(ConstraintMatchRegistry<SimpleScore> registry) {
      this.registry = registry;
    }

    @Override
    public void resetWorkingSolution(TestdataShadowedSolution solution) {
      this.solution = solution;
      registrations.clear(); // The registry already invalidated registrations from the old epoch.
      contributions.clear();
      dirty.clear();
      total = 0;
      initialized = false;
      resetCount++;
    }

    @Override
    public void beforeVariableChanged(Object entity, String variableName) {
      if (!initialized) {
        if (variableName.equals("firstShadow")) shadowCallbacksBeforeInitialization++;
        return;
      }
      if (variableName.equals("firstShadow")) shadowCallbacksAfterInitialization++;
      var changed = (TestdataShadowedEntity) entity;
      var registration = registrations.remove(changed);
      if (registration != null) registration.cancel();
      var contribution = contributions.remove(changed);
      if (contribution != null) total -= contribution;
      dirty.add(changed);
    }

    @Override
    public void afterVariableChanged(Object entity, String variableName) {
      if (initialized) dirty.add((TestdataShadowedEntity) entity);
    }

    @Override
    public SimpleScore calculateScore() {
      if (!initialized) {
        dirty.addAll(solution.getEntityList());
        initialized = true;
        initializationCount++;
      }
      for (var entity : dirty) {
        var shadow = entity.getFirstShadow();
        long contribution = -Long.parseLong(shadow.substring(0, shadow.indexOf('/')));
        contributions.put(entity, contribution);
        total += contribution;
        contributionCalculationCount++;
        if (registry != null) {
          var target = new Target(entity.getCode(), shadow);
          registrations.put(
              entity,
              registry.registerConstraintMatchWithIndictments(
                  CONSTRAINT,
                  SimpleScore.of(contribution),
                  REASON,
                  List.of(target, target),
                  List.of(entity, entity.getValue(), entity)));
        }
      }
      dirty.clear();
      return SimpleScore.of(total);
    }
  }
}
