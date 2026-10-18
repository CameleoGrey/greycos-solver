package greycos.solver.core.impl.score.stream.bavet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.MatchOccurrenceAnalysis;
import greycos.solver.core.api.score.analysis.RawScoreAnalysis;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.api.solver.IndictmentFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisOptions;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolutionUpdatePolicy;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.move.MoveDirector;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Expected occurrences and memberships are enumerated without reading solver match data. */
class RawScoreAnalysisIntegrationTest {
  private static final Reason SHARED_REASON = new Reason("same justification for every occurrence");
  private static final ScoreAnalysisOptions BOTH = options(true);

  static Stream<Arguments> operationsAndViews() {
    return Stream.of(Operation.values())
        .flatMap(
            operation ->
                Stream.of(false, true).map(originals -> Arguments.of(operation, originals)));
  }

  @ParameterizedTest
  @MethodSource("operationsAndViews")
  void transformedOccurrencesAndCanonicalIndexesAgreeWithIndependentReplay(
      Operation operation, boolean originals) {
    var problem = Problem.create();
    try (var director = director(operation, problem, originals)) {
      var firstExpected = operation.replay(problem);
      var first = director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL);
      assertAnalysis(first, firstExpected, originals);
      var retained = director.getSession();
      var changed = problem.jobs.getFirst();
      director.beforeVariableChanged(changed, "allocation");
      changed.allocation = 2;
      director.afterVariableChanged(changed, "allocation");
      assertAnalysis(
          director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL),
          operation.replay(problem),
          originals);
      assertThat(director.getSession()).isSameAs(retained);
      director.beforeVariableChanged(changed, "allocation");
      changed.allocation = 1;
      director.afterVariableChanged(changed, "allocation");
      assertAnalysis(
          director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL),
          operation.replay(problem),
          originals);
      assertAnalysis(first, firstExpected, originals);
      assertThatThrownBy(() -> first.rawAnalysis().matchOccurrences().clear())
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> first.rawAnalysis().constraintMap().clear())
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> first.rawAnalysis().indictmentMap().firstEntry().getValue().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = Operation.class,
      names = {"GROUP", "EXISTS"})
  void alreadyReadOccurrencesRefreshNeutralSourceChangesInTheRetainedSession(Operation operation) {
    var problem = Problem.create();
    try (var director = director(operation, problem, true)) {
      var beforeExpected = operation.replay(problem);
      var before = director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL);
      var session = director.getSession();
      Object removed;
      if (operation == Operation.GROUP) {
        removed = problem.jobs.get(1); // Not the group's minimum or final contributor.
        session.retract(removed);
        problem.jobs.remove(removed);
      } else {
        removed = problem.witnesses.getFirst(); // Another matching witness remains.
        session.retract(removed);
        problem.witnesses.remove(removed);
      }
      var after = director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL);
      assertAnalysis(after, operation.replay(problem), true);
      assertThat(after.score()).isEqualTo(before.score());
      assertThat(director.getSession()).isSameAs(session);
      assertThat(after.rawAnalysis().originalIndictmentMap()).doesNotContainKey(removed);
      assertAnalysis(before, beforeExpected, true);
      assertThat(before.rawAnalysis().originalIndictmentMap()).containsKey(removed);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = Operation.class,
      names = {"PRECOMPUTE_GROUP", "PRECOMPUTE_EXISTS"})
  void equalFactReplacementKeepsFreshOriginalReferencesAndCancelsInValueDiff(Operation operation) {
    var problem = Problem.create();
    try (var director = director(operation, problem, true)) {
      var beforeExpected = operation.replay(problem);
      var before = director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL);
      var old = problem.witnesses.get(1);
      director.beforeProblemFactRemoved(old);
      problem.witnesses.remove(old);
      director.afterProblemFactRemoved(old);
      director.calculateScore();
      var replacement = new Witness(old.id(), old.bucket(), old.capacity());
      assertThat(replacement).isEqualTo(old).isNotSameAs(old);
      director.beforeProblemFactAdded(replacement);
      problem.witnesses.add(replacement);
      director.afterProblemFactAdded(replacement);
      var after = director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL);
      assertAnalysis(after, operation.replay(problem), true);
      assertAnalysis(before, beforeExpected, true);
      assertThat(after.rawAnalysis().matchOccurrences())
          .anySatisfy(
              row ->
                  assertThat(row.originalIndictedObjects())
                      .anyMatch(source -> source == replacement));
      assertThat(after.rawAnalysis().matchOccurrences())
          .allSatisfy(
              row -> assertThat(row.originalIndictedObjects()).noneMatch(source -> source == old));
      var diff = after.diff(before);
      assertThat(diff.score()).isEqualTo(SimpleScore.ZERO);
      assertRaw(diff.rawAnalysis(), List.of(), true);
    }
  }

  @Test
  void associationOnlyDiffSurvivesWhenEveryGroupedEffectIsZero() {
    var problem = Problem.create();
    problem.jobs = new ArrayList<>(problem.jobs.subList(0, 2));
    problem.jobs.getLast().allocation = 2;
    var manager = manager(AssociationConstraints.class);
    var beforeExpected = assignmentReplay(problem, false);
    var before = manager.analyzeWithOptions(problem, BOTH);
    problem.jobs.getFirst().allocation = 2;
    problem.jobs.getLast().allocation = 1;
    var afterExpected = assignmentReplay(problem, false);
    var after = manager.analyzeWithOptions(problem, BOTH, SolutionUpdatePolicy.UPDATE_ALL);
    var delta = after.diff(before);
    var expectedDelta = new ArrayList<>(afterExpected);
    beforeExpected.forEach(row -> expectedDelta.add(row.withSign(-1)));
    assertThat(delta.score()).isEqualTo(SimpleScore.ZERO);
    assertThat(delta.indictmentMap()).isEmpty();
    assertThat(delta.originalIndictmentMap()).isEmpty();
    assertRaw(delta.rawAnalysis(), expectedDelta, true);
    assertThat(delta.rawAnalysis().matchOccurrences()).hasSize(4);
    assertRaw(delta.diff(delta).rawAnalysis(), List.of(), true);
    assertAnalysis(before, beforeExpected, true);
    assertAnalysis(after, afterExpected, true);
  }

  @Test
  void rawSelectionsRetainDuplicateAndEqualDistinctReferencesAndIgnoreOrderInDiff() {
    var problem = Problem.create();
    problem.jobs = new ArrayList<>(problem.jobs.subList(0, 1));
    var job = problem.jobs.getFirst();
    var first = new Target("selected", 7);
    var equal = new Target("selected", 7);
    job.targets = new ArrayList<>(List.of(first, equal, first));
    var manager = manager(SelectionConstraints.class);
    var before = manager.analyzeWithOptions(problem, BOTH);
    var row = before.rawAnalysis().matchOccurrences().getFirst();
    assertThat(row.indictedObjects()).hasSize(3);
    assertThat(row.indictedObjects().get(0)).isSameAs(first);
    assertThat(row.indictedObjects().get(1)).isSameAs(equal).isNotSameAs(first);
    assertThat(row.indictedObjects().get(2)).isSameAs(first);
    assertThat(before.rawAnalysis().indictmentMap()).hasSize(1);
    assertThat(before.rawAnalysis().indictmentMap().get(first)).containsExactly(row);

    job.targets.clear();
    job.targets.addAll(List.of(equal, first, first));
    var reordered = manager.analyzeWithOptions(problem, BOTH);
    assertThat(reordered.rawAnalysis().matchOccurrences().getFirst().indictedObjects().getFirst())
        .isSameAs(equal);
    assertRaw(reordered.diff(before).rawAnalysis(), List.of(), true);
    assertThat(row.indictedObjects()).hasSize(3);
    assertThat(row.indictedObjects().getFirst()).isSameAs(first);

    job.targets.removeLast();
    var shorter = manager.analyzeWithOptions(problem, BOTH);
    assertThat(shorter.diff(before).indictmentMap()).isEmpty();
    assertThat(shorter.diff(before).rawAnalysis().matchOccurrences()).hasSize(2);
    assertThatThrownBy(() -> row.indictedObjects().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void temporaryAndRetainedUndoRestoreRowsWithoutMutatingAnEarlierSnapshot() {
    var problem = Problem.create();
    try (var director =
        director(
            factory -> new AssociationConstraints().defineConstraints(factory), problem, true)) {
      var beforeExpected = assignmentReplay(problem, false);
      var before = director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL);
      var session = director.getSession();
      var variable =
          director
              .getSolutionDescriptor()
              .getMetaModel()
              .genuineEntity(Job.class)
              .basicVariable("allocation", Integer.class);
      var job = problem.jobs.getFirst();
      Move<Problem> change = view -> view.changeVariable(variable, job, 2);
      var moves = new MoveDirector<>(director);
      var undo =
          moves.executeTemporaryProducingUndoMove(
              change,
              score ->
                  assertAnalysis(
                      director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL),
                      assignmentReplay(problem, false),
                      true));
      assertThat(job.allocation).isEqualTo(1);
      assertAnalysis(
          director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL), beforeExpected, true);
      moves.execute(change);
      assertAnalysis(
          director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL),
          assignmentReplay(problem, false),
          true);
      moves.execute(undo);
      assertAnalysis(
          director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL), beforeExpected, true);
      assertAnalysis(before, beforeExpected, true);
      assertThat(director.getSession()).isSameAs(session);
    }
  }

  @Test
  void recommendationsKeepBothDiffSignsAfterRestoringAnAlreadyAssignedEntity() {
    var problem = Problem.create();
    var fitted = problem.jobs.getFirst();
    var originalValue = fitted.allocation;
    var manager = manager(WeightedAssignmentConstraints.class);
    var recommendations =
        manager.recommendAssignmentWithOptions(problem, fitted, job -> job.allocation, BOTH);
    assertThat(recommendations).hasSize(problem.values.size());
    assertThat(fitted.allocation).isEqualTo(originalValue);
    for (var recommendation : recommendations) {
      var value = recommendation.proposition();
      var delta = recommendation.scoreAnalysisDiff();
      assertThat(delta.score()).isEqualTo(SimpleScore.of(originalValue - value));
      if (value.equals(originalValue)) {
        assertRaw(delta.rawAnalysis(), List.of(), true);
        continue;
      }
      assertThat(delta.rawAnalysis().matchOccurrences()).hasSize(2);
      var added =
          delta.rawAnalysis().matchOccurrences().stream()
              .filter(row -> row.occurrenceSign() == 1)
              .findFirst()
              .orElseThrow();
      var removed =
          delta.rawAnalysis().matchOccurrences().stream()
              .filter(row -> row.occurrenceSign() == -1)
              .findFirst()
              .orElseThrow();
      assertThat(added.matchScore()).isEqualTo(SimpleScore.of(-value));
      assertThat(removed.matchScore()).isEqualTo(SimpleScore.of(-originalValue));
      assertThat(removed.score()).isEqualTo(SimpleScore.of(originalValue));
      assertThat(added.indictedObjects()).containsExactly(value, value);
      assertThat(removed.indictedObjects()).containsExactly(originalValue, originalValue);
      assertThat(added.originalIndictedObjects())
          .singleElement()
          .satisfies(source -> assertThat(((Job) source).id).isEqualTo(fitted.id));
      assertThat(removed.originalIndictedObjects().getFirst())
          .isSameAs(added.originalIndictedObjects().getFirst());
      assertThat(delta.rawAnalysis().indictmentMap().get(value)).containsExactly(added);
      assertThat(delta.rawAnalysis().indictmentMap().get(originalValue)).containsExactly(removed);
    }
    assertThat(fitted.allocation).isEqualTo(originalValue);
  }

  private static ScoreAnalysisOptions options(boolean originals) {
    return new ScoreAnalysisOptions(
        ScoreAnalysisFetchPolicy.FETCH_ALL,
        originals ? IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL : IndictmentFetchPolicy.TERMINAL,
        true);
  }

  private static SolutionManager<Problem, SimpleScore> manager(
      Class<? extends ConstraintProvider> provider) {
    return SolutionManager.create(
        SolverFactory.<Problem>create(
            new SolverConfig()
                .withSolutionClass(Problem.class)
                .withEntityClasses(Job.class)
                .withConstraintProviderClass(provider)));
  }

  private static BavetConstraintStreamScoreDirector<Problem, SimpleScore> director(
      Operation operation, Problem problem, boolean originals) {
    return director(
        factory -> new Constraint[] {operation.constraint(factory)}, problem, originals);
  }

  private static BavetConstraintStreamScoreDirector<Problem, SimpleScore> director(
      ConstraintProvider provider, Problem problem, boolean originals) {
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<Problem, SimpleScore>(
            SolutionDescriptor.buildSolutionDescriptor(Problem.class, Job.class),
            provider,
            EnvironmentMode.NO_ASSERT);
    var director =
        factory
            .createScoreDirectorBuilder()
            .withConstraintMatchPolicy(ConstraintMatchPolicy.match(options(originals)))
            .build();
    director.setWorkingSolution(problem);
    return director;
  }

  private static void assertAnalysis(
      ScoreAnalysis<SimpleScore> analysis, List<ExpectedOccurrence> expected, boolean originals) {
    long score = 0;
    for (var occurrence : expected) score += occurrence.matchScore * occurrence.sign;
    assertThat(analysis.score()).isEqualTo(SimpleScore.of(score));
    assertRaw(analysis.rawAnalysis(), expected, originals);
    for (var constraint : analysis.constraintAnalyses()) {
      long count =
          expected.stream()
              .filter(row -> row.constraintId.equals(constraint.constraintId()))
              .mapToLong(ExpectedOccurrence::sign)
              .sum();
      assertThat(constraint.rawMatchCount()).isEqualTo(count);
    }
  }

  private static void assertRaw(
      RawScoreAnalysis<SimpleScore> raw, List<ExpectedOccurrence> expected, boolean originals) {
    assertThat(raw).isNotNull();
    assertThat(raw.originalsFetched()).isEqualTo(originals);
    assertThat(raw.matchOccurrences()).hasSize(expected.size());
    var unmatched = new ArrayList<>(raw.matchOccurrences());
    var identities =
        new IdentityHashMap<MatchOccurrenceAnalysis<SimpleScore>, ExpectedOccurrence>();
    for (var wanted : expected) {
      int found = -1;
      for (int i = 0; i < unmatched.size(); i++) {
        if (matches(unmatched.get(i), wanted, originals)) {
          found = i;
          break;
        }
      }
      assertThat(found)
          .as("Missing raw occurrence %s in %s", wanted, unmatched)
          .isGreaterThanOrEqualTo(0);
      var actual = unmatched.remove(found);
      assertThat(identities.put(actual, wanted))
          .as("Distinct occurrences need distinct row instances")
          .isNull();
      assertThat(actual.score()).isEqualTo(SimpleScore.of(wanted.matchScore * wanted.sign));
    }
    assertThat(unmatched).isEmpty();
    assertIndex(
        raw.constraintMap(),
        expected,
        row -> List.of(row.constraintId),
        identities,
        key -> key.id());
    assertIndex(
        raw.indictmentMap(),
        expected,
        ExpectedOccurrence::selected,
        identities,
        Function.identity());
    if (originals) {
      assertIndex(
          raw.originalIndictmentMap(),
          expected,
          ExpectedOccurrence::sources,
          identities,
          Function.identity());
    } else {
      assertThat(raw.originalIndictmentMap()).isNull();
    }
  }

  private static boolean matches(
      MatchOccurrenceAnalysis<SimpleScore> actual, ExpectedOccurrence expected, boolean originals) {
    return actual.constraintRef().id().equals(expected.constraintId)
        && actual.matchScore().equals(SimpleScore.of(expected.matchScore))
        && actual.occurrenceSign() == expected.sign
        && actual.justification().equals(SHARED_REASON)
        && actual.indictedObjects().equals(expected.selected)
        && (originals
            ? sameReferences(actual.originalIndictedObjects(), expected.sources)
            : actual.originalIndictedObjects() == null);
  }

  private static boolean sameReferences(List<Object> actual, List<Object> expected) {
    if (actual == null || actual.size() != expected.size()) return false;
    var identity = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    identity.addAll(actual);
    if (identity.size() != actual.size()) return false;
    return expected.stream().allMatch(identity::contains);
  }

  private static <Key_> void assertIndex(
      Map<Key_, List<MatchOccurrenceAnalysis<SimpleScore>>> actual,
      List<ExpectedOccurrence> expected,
      Function<ExpectedOccurrence, List<Object>> keys,
      IdentityHashMap<MatchOccurrenceAnalysis<SimpleScore>, ExpectedOccurrence> identities,
      Function<Key_, Object> normalizeKey) {
    var wanted = new LinkedHashMap<Object, List<ExpectedOccurrence>>();
    for (var row : expected) {
      for (var key : new LinkedHashSet<>(keys.apply(row))) {
        wanted.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
      }
    }
    assertThat(actual).hasSize(wanted.size());
    for (var entry : actual.entrySet()) {
      var remaining = new ArrayList<>(wanted.get(normalizeKey.apply(entry.getKey())));
      var indexedIdentities =
          Collections.newSetFromMap(
              new IdentityHashMap<MatchOccurrenceAnalysis<SimpleScore>, Boolean>());
      for (var row : entry.getValue()) {
        assertThat(indexedIdentities.add(row))
            .as("An index must contain each occurrence only once")
            .isTrue();
        var expectedRow = identities.get(row);
        assertThat(expectedRow)
            .as("An index must reference the canonical row instance")
            .isNotNull();
        int found = -1;
        for (int i = 0; i < remaining.size(); i++) if (remaining.get(i) == expectedRow) found = i;
        assertThat(found)
            .as("Unexpected occurrence association for %s", entry.getKey())
            .isGreaterThanOrEqualTo(0);
        remaining.remove(found);
      }
      assertThat(remaining).isEmpty();
    }
  }

  private static List<ExpectedOccurrence> assignmentReplay(Problem problem, boolean weighted) {
    var result = new ArrayList<ExpectedOccurrence>();
    for (var job : problem.jobs) {
      if (job.allocation != null)
        result.add(
            occurrence(
                weighted ? -job.allocation : -1, List.of(job.allocation, job.allocation), job));
    }
    return result;
  }

  private static ExpectedOccurrence occurrence(long score, List<?> selected, Object... sources) {
    var identity = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    var originals = new ArrayList<Object>();
    for (var source : sources) if (identity.add(source)) originals.add(source);
    return new ExpectedOccurrence("raw", score, 1, List.copyOf(selected), List.copyOf(originals));
  }

  private record ExpectedOccurrence(
      String constraintId, long matchScore, int sign, List<Object> selected, List<Object> sources) {
    ExpectedOccurrence withSign(int newSign) {
      return new ExpectedOccurrence(constraintId, matchScore, newSign, selected, sources);
    }
  }

  enum Operation {
    MAP,
    GROUP,
    JOIN,
    EXISTS,
    FLATTEN,
    PRECOMPUTE_GROUP,
    PRECOMPUTE_EXISTS;

    Constraint constraint(ConstraintFactory factory) {
      var source = factory.forEach(Job.class);
      return switch (this) {
        case MAP ->
            source
                .map(job -> new Derived(new Target("mapped", job.allocation), job.weight))
                .penalize(SimpleScore.ONE, Derived::weight)
                .justifyWith((value, score) -> SHARED_REASON)
                .indictWith(value -> List.of(value.target()))
                .asConstraint("raw");
        case GROUP ->
            source
                .groupBy(job -> job.allocation, ConstraintCollectors.min((Job job) -> job.weight))
                .penalize(SimpleScore.ONE, (bucket, minimum) -> minimum)
                .justifyWith((bucket, minimum, score) -> SHARED_REASON)
                .asConstraint("raw");
        case JOIN ->
            source
                .join(Witness.class, Joiners.equal(job -> job.allocation, Witness::bucket))
                .penalize(SimpleScore.ONE, (job, witness) -> job.weight + witness.capacity())
                .justifyWith((job, witness, score) -> SHARED_REASON)
                .indictWith((job, witness) -> List.of(new Target("joined", witness.bucket())))
                .asConstraint("raw");
        case EXISTS ->
            source
                .expand(job -> new Target("exists", job.allocation))
                .ifExists(
                    Witness.class, Joiners.equal((job, target) -> job.allocation, Witness::bucket))
                .penalize(SimpleScore.ONE, (job, target) -> job.weight)
                .justifyWith((job, target, score) -> SHARED_REASON)
                .indictWith((job, target) -> List.of(target, target))
                .asConstraint("raw");
        case FLATTEN ->
            source
                .flattenLast(job -> job.tags)
                .penalize(SimpleScore.ONE)
                .justifyWith((tag, score) -> SHARED_REASON)
                .asConstraint("raw");
        case PRECOMPUTE_GROUP ->
            factory
                .precompute(
                    precompute ->
                        precompute
                            .forEachUnfiltered(Witness.class)
                            .groupBy(Witness::bucket, ConstraintCollectors.min(Witness::capacity)))
                .penalize(SimpleScore.ONE, (bucket, minimum) -> minimum)
                .justifyWith((bucket, minimum, score) -> SHARED_REASON)
                .asConstraint("raw");
        case PRECOMPUTE_EXISTS ->
            factory
                .precompute(
                    precompute -> {
                      var facts = precompute.forEachUnfiltered(Witness.class);
                      return facts
                          .filter(witness -> witness.capacity() <= 3)
                          .map(witness -> new Target("precomputed", witness.bucket()))
                          .ifExists(
                              facts.filter(witness -> witness.capacity() > 3),
                              Joiners.equal(Target::bucket, Witness::bucket));
                    })
                .penalize(SimpleScore.of(2))
                .justifyWith((target, score) -> SHARED_REASON)
                .asConstraint("raw");
      };
    }

    List<ExpectedOccurrence> replay(Problem problem) {
      var result = new ArrayList<ExpectedOccurrence>();
      if (this == PRECOMPUTE_GROUP || this == GROUP) {
        var groups = new LinkedHashMap<Integer, List<Object>>();
        var minima = new LinkedHashMap<Integer, Integer>();
        if (this == GROUP) {
          for (var job : problem.jobs) {
            groups.computeIfAbsent(job.allocation, ignored -> new ArrayList<>()).add(job);
            minima.merge(job.allocation, job.weight, Math::min);
          }
        } else {
          for (var witness : problem.witnesses) {
            groups.computeIfAbsent(witness.bucket(), ignored -> new ArrayList<>()).add(witness);
            minima.merge(witness.bucket(), witness.capacity(), Math::min);
          }
        }
        for (var entry : groups.entrySet())
          result.add(
              occurrence(
                  -minima.get(entry.getKey()),
                  List.of(entry.getKey(), minima.get(entry.getKey())),
                  entry.getValue().toArray()));
        return result;
      }
      if (this == PRECOMPUTE_EXISTS) {
        for (var left : problem.witnesses) {
          if (left.capacity() > 3) continue;
          var originals = new ArrayList<Object>();
          originals.add(left);
          for (var right : problem.witnesses) {
            if (right.capacity() > 3 && right.bucket() == left.bucket()) originals.add(right);
          }
          if (originals.size() > 1)
            result.add(
                occurrence(
                    -2, List.of(new Target("precomputed", left.bucket())), originals.toArray()));
        }
        return result;
      }
      for (var job : problem.jobs) {
        switch (this) {
          case MAP ->
              result.add(
                  occurrence(-job.weight, List.of(new Target("mapped", job.allocation)), job));
          case JOIN -> {
            for (var witness : problem.witnesses) {
              if (witness.bucket() == job.allocation)
                result.add(
                    occurrence(
                        -job.weight - witness.capacity(),
                        List.of(new Target("joined", witness.bucket())),
                        job,
                        witness));
            }
          }
          case EXISTS -> {
            var originals = new ArrayList<Object>();
            originals.add(job);
            for (var witness : problem.witnesses) {
              if (witness.bucket() == job.allocation) originals.add(witness);
            }
            if (originals.size() > 1) {
              var target = new Target("exists", job.allocation);
              result.add(occurrence(-job.weight, List.of(target, target), originals.toArray()));
            }
          }
          case FLATTEN -> {
            for (var tag : job.tags) result.add(occurrence(-1, List.of(tag), job));
          }
          default ->
              throw new IllegalStateException(
                  "This operation uses the separate group/fact oracle.");
        }
      }
      return result;
    }
  }

  record Reason(String text) implements ConstraintJustification {}

  record Target(String kind, int bucket) {}

  record Derived(Target target, int weight) {}

  record Witness(String id, int bucket, int capacity) {}

  public static class AssociationConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(Job.class)
            .penalize(SimpleScore.ONE)
            .justifyWith((job, score) -> SHARED_REASON)
            .indictWith(job -> List.of(job.allocation, job.allocation))
            .asConstraint("raw")
      };
    }
  }

  public static class WeightedAssignmentConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(Job.class)
            .penalize(SimpleScore.ONE, job -> job.allocation)
            .justifyWith((job, score) -> SHARED_REASON)
            .indictWith(job -> List.of(job.allocation, job.allocation))
            .asConstraint("raw")
      };
    }
  }

  public static class SelectionConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(Job.class)
            .penalize(SimpleScore.ONE, job -> job.weight)
            .justifyWith((job, score) -> SHARED_REASON)
            .indictWith(job -> job.targets)
            .asConstraint("raw")
      };
    }
  }

  @PlanningEntity
  public static class Job {
    @PlanningId public String id;

    @PlanningVariable(valueRangeProviderRefs = "values")
    public Integer allocation;

    public int weight;
    public List<Target> tags;
    public List<Object> targets;

    public Job() {}

    Job(String id, int allocation, int weight, Target... tags) {
      this.id = id;
      this.allocation = allocation;
      this.weight = weight;
      this.tags = List.of(tags);
      this.targets = new ArrayList<>(List.of(new Target("selected", allocation)));
    }

    @Override
    public String toString() {
      return id;
    }
  }

  @PlanningSolution
  public static class Problem {
    @PlanningEntityCollectionProperty public List<Job> jobs;
    @ProblemFactCollectionProperty public List<Witness> witnesses;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "values")
    public List<Integer> values;

    @PlanningScore public SimpleScore score;

    public Problem() {}

    static Problem create() {
      var problem = new Problem();
      var tag = new Target("tag", 1);
      problem.jobs =
          new ArrayList<>(
              List.of(
                  new Job("a", 1, 1, tag, tag),
                  new Job("b", 1, 3, new Target("tag", 1)),
                  new Job("c", 2, 5, new Target("tag", 2))));
      problem.witnesses =
          new ArrayList<>(
              List.of(new Witness("w1", 1, 2), new Witness("w2", 1, 6), new Witness("w3", 2, 3)));
      problem.values = List.of(1, 2, 3);
      return problem;
    }
  }
}
