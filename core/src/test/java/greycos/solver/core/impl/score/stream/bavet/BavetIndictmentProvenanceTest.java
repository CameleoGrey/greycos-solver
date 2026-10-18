package greycos.solver.core.impl.score.stream.bavet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.DefaultConstraintJustification;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.api.solver.IndictmentFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisFetchPolicy;
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
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The oracle enumerates business occurrences with ordinary loops. It never reads a Bavet tuple,
 * constraint match, justification, or provenance container to calculate expected results.
 */
class BavetIndictmentProvenanceTest {

  @ParameterizedTest
  @EnumSource(Operation.class)
  void incrementalResultsAgreeWithIndependentOccurrences(Operation operation) {
    var solution = Sample.create();
    try (var director = director(operation, solution)) {
      assertOracle(director, operation, solution);
      var retainedSession = director.getSession();
      var snapshot = director.getSession().getScoreInliner().getIndictmentMaps();
      var before = expected(operation.occurrences(solution));

      // Move a source between groups; equal mapped results from other sources remain alive.
      var changed = solution.items.getFirst();
      director.beforeVariableChanged(changed, "value");
      changed.value = 2;
      director.afterVariableChanged(changed, "value");
      assertOracle(director, operation, solution);
      assertThat(director.getSession()).isSameAs(retainedSession);

      director.beforeVariableChanged(changed, "value");
      changed.value = 1;
      director.afterVariableChanged(changed, "value");
      assertOracle(director, operation, solution);

      // Remove and reinsert in both orders, including the final contributor of a group.
      var removed = new ArrayList<Item>();
      while (!solution.items.isEmpty()) {
        var item = solution.items.getLast();
        director.beforeEntityRemoved(item);
        solution.items.removeLast();
        director.afterEntityRemoved(item);
        removed.add(item);
        assertOracle(director, operation, solution);
      }
      for (var item : removed) {
        director.beforeEntityAdded(item);
        solution.items.add(item);
        director.afterEntityAdded(item);
        assertOracle(director, operation, solution);
      }

      // Previously returned materializations must not become views of mutable live lineage.
      assertMap(snapshot.terminal(), before.terminal);
      assertMap(snapshot.original(), before.original);
      assertThatThrownBy(() -> snapshot.original().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = Operation.class,
      names = {"DISTINCT", "GROUP_MIN", "GROUP_DISTINCT", "CONCAT_DISTINCT"})
  void contributorRemovalRefreshesAlreadyReadMapsWithoutChangingScore(Operation operation) {
    var solution = Sample.create();
    try (var director = director(operation, solution)) {
      assertOracle(director, operation, solution);
      var session = director.getSession();
      var score = director.calculateScore();
      var oldMaps = session.getScoreInliner().getIndictmentMaps();
      var oldExpected = expected(operation.occurrences(solution));
      // This item is neither the minimum nor the last representative of its value.
      var removed = solution.items.get(1);
      // Isolate a retained-network event; public entity removal intentionally rebuilds the model.
      session.retract(removed);
      solution.items.remove(removed);
      assertOracle(director, operation, solution);
      assertThat(director.calculateScore()).isEqualTo(score);
      assertThat(director.getSession()).isSameAs(session);
      assertThat(session.getScoreInliner().getIndictmentMaps().original())
          .doesNotContainKey(removed);
      assertMap(oldMaps.original(), oldExpected.original);
      assertMap(oldMaps.terminal(), oldExpected.terminal);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = Operation.class,
      names = {"EXISTS_1", "EXISTS_2", "EXISTS_3", "EXISTS_4"})
  void allCurrentWitnessesContributeEvenWhenExistenceDoesNotChange(Operation operation) {
    var solution = Sample.create();
    try (var director = director(operation, solution)) {
      assertOracle(director, operation, solution);
      var session = director.getSession();
      var score = director.calculateScore();
      var oldMaps = session.getScoreInliner().getIndictmentMaps();
      var oldExpected = expected(operation.occurrences(solution));
      var removed = solution.witnesses.getFirst();
      // Session-level retraction isolates the lineage-only event from property-refresh rebuilds.
      session.retract(removed);
      solution.witnesses.remove(removed);
      assertOracle(director, operation, solution);
      assertThat(director.calculateScore()).isEqualTo(score);
      assertThat(session.getScoreInliner().getIndictmentMaps().original())
          .doesNotContainKey(removed);
      assertMap(oldMaps.original(), oldExpected.original);

      solution.witnesses.add(removed);
      session.insert(removed);
      assertOracle(director, operation, solution);
      assertThat(director.getSession()).isSameAs(session);
    }
  }

  @Test
  void equalDerivedWitnessesRetainTheirIndependentOriginalLifetimes() {
    var solution = Sample.create();
    // Distinct original facts project to equal witnesses with independent tuple lifetimes.
    var one = new Witness("one", 1, 10);
    var two = new Witness("two", 1, 10);
    solution.witnesses = new ArrayList<>(List.of(one, two));
    try (var director = director(Operation.EXISTS_MAPPED, solution)) {
      assertOracle(director, Operation.EXISTS_MAPPED, solution);
      var session = director.getSession();
      session.retract(one);
      solution.witnesses.removeFirst();
      assertOracle(director, Operation.EXISTS_MAPPED, solution);
      assertThat(session.getScoreInliner().getIndictmentMaps().original()).containsKey(two);
      session.retract(two);
      solution.witnesses.clear();
      assertOracle(director, Operation.EXISTS_MAPPED, solution);
      assertThat(session.getScoreInliner().getIndictmentMaps().original()).isEmpty();
    }
  }

  @Test
  void retainedUndoRestoresContributorMembershipAndKeepsTheSession() {
    var solution = Sample.create();
    try (var director = director(Operation.DISTINCT, solution)) {
      var session = director.getSession();
      var variable =
          director
              .getSolutionDescriptor()
              .getMetaModel()
              .genuineEntity(Item.class)
              .basicVariable("value", Integer.class);
      var item = solution.items.getFirst();
      Move<Sample> move = view -> view.changeVariable(variable, item, 2);
      var moves = new MoveDirector<>(director);
      assertOracle(director, Operation.DISTINCT, solution);
      var undo =
          moves.executeTemporaryProducingUndoMove(
              move, score -> assertOracle(director, Operation.DISTINCT, solution));
      assertThat(item.value).isEqualTo(1);
      assertOracle(director, Operation.DISTINCT, solution);
      moves.execute(move);
      assertThat(item.value).isEqualTo(2);
      assertOracle(director, Operation.DISTINCT, solution);
      moves.execute(undo);
      assertThat(item.value).isEqualTo(1);
      assertOracle(director, Operation.DISTINCT, solution);
      assertThat(director.getSession()).isSameAs(session);
    }
  }

  @ParameterizedTest
  @EnumSource(ScoreAnalysisFetchPolicy.class)
  void publicAnalysisPreservesLegacyDepthAndRejectsIncompatibleIndictmentFetch(
      ScoreAnalysisFetchPolicy fetchPolicy) {
    var solution = Sample.create();
    var solverFactory =
        SolverFactory.<Sample>create(
            new SolverConfig()
                .withSolutionClass(Sample.class)
                .withEntityClasses(Item.class)
                .withConstraintProviderClass(PublicProvider.class));
    SolutionManager<Sample, SimpleScore> manager = SolutionManager.create(solverFactory);
    var expected = expected(Operation.GROUP_MIN.occurrences(solution));
    var legacy = manager.analyze(solution, fetchPolicy);
    assertThat(legacy.indictmentMap()).isNull();
    assertThat(legacy.originalIndictmentMap()).isNull();
    for (var policy : IndictmentFetchPolicy.values()) {
      if (policy != IndictmentFetchPolicy.NONE
          && fetchPolicy != ScoreAnalysisFetchPolicy.FETCH_ALL) {
        assertThatThrownBy(
                () ->
                    manager.analyze(solution, fetchPolicy, SolutionUpdatePolicy.UPDATE_ALL, policy))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("FETCH_ALL");
        continue;
      }
      var analysis =
          manager.analyze(solution, fetchPolicy, SolutionUpdatePolicy.UPDATE_ALL, policy);
      assertThat(analysis.score()).isEqualTo(legacy.score());
      assertThat(analysis.constraintMap()).isEqualTo(legacy.constraintMap());
      if (policy == IndictmentFetchPolicy.NONE) {
        assertThat(analysis.indictmentMap()).isNull();
      } else {
        assertMap(analysis.indictmentMap(), expected.terminal);
      }
      if (policy == IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL) {
        assertMap(analysis.originalIndictmentMap(), expected.original);
      } else {
        assertThat(analysis.originalIndictmentMap()).isNull();
      }
    }
    var convenient =
        manager.analyzeWithIndictments(solution, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL);
    assertMap(convenient.indictmentMap(), expected.terminal);
    assertMap(convenient.originalIndictmentMap(), expected.original);
  }

  @Test
  void propertyRefreshAndWholeSolutionReplacementAgreeWithFreshDirectorAndOracle() {
    var solution = Sample.create();
    try (var director = director(Operation.GROUP_MIN, solution)) {
      assertOracle(director, Operation.GROUP_MIN, solution);
      var changed = solution.items.get(1);
      director.beforeProblemPropertyChanged(changed);
      changed.weight = 0;
      director.afterProblemPropertyChanged(changed);
      assertOracle(director, Operation.GROUP_MIN, solution);
      try (var fresh = director(Operation.GROUP_MIN, solution)) {
        assertOracle(fresh, Operation.GROUP_MIN, solution);
        assertThat(director.getIndictmentMaps()).isEqualTo(fresh.getIndictmentMaps());
      }
      var replacement = Sample.create();
      director.setWorkingSolution(replacement);
      assertOracle(director, Operation.GROUP_MIN, replacement);
      assertThat(director.getIndictmentMaps().original()).doesNotContainKey(changed);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = Operation.class,
      names = {"PRECOMPUTE_GROUP", "PRECOMPUTE_EXISTS", "PRECOMPUTE_GROUP_EXISTS"})
  void precomputedInnerLineageSurvivesFactReplacementAndStructuralRefresh(Operation operation) {
    var solution = Sample.create();
    var extraWitness = new Witness("fourth", 1, 20);
    var groupMember = new Witness("group-member", 1, 5);
    solution.witnesses.add(extraWitness);
    solution.witnesses.add(groupMember);
    try (var director = director(operation, solution)) {
      assertOracle(director, operation, solution);
      assertPrecomputedOriginalReferences(director, solution);
      var session = director.getSession();
      var score = director.calculateScore();
      var snapshot = director.getIndictmentMaps();
      var before = expected(operation.occurrences(solution));
      var removed = solution.witnesses.get(1);

      // A non-minimum group member and one of two positive existence witnesses disappear.
      // The terminal tuple and score are unchanged, but the precomputed source set must change.
      director.beforeProblemFactRemoved(removed);
      solution.witnesses.remove(removed);
      director.afterProblemFactRemoved(removed);
      assertOracle(director, operation, solution);
      assertPrecomputedOriginalReferences(director, solution);
      assertThat(director.calculateScore()).isEqualTo(score);
      assertThat(director.getSession()).isNotSameAs(session);
      assertThat(director.getIndictmentMaps().original()).doesNotContainKey(removed);

      var replacement = new Witness("replacement", removed.group, removed.ceiling);
      director.beforeProblemFactAdded(replacement);
      solution.witnesses.add(replacement);
      director.afterProblemFactAdded(replacement);
      assertOracle(director, operation, solution);
      assertPrecomputedOriginalReferences(director, solution);
      assertThat(director.calculateScore()).isEqualTo(score);
      assertThat(director.getIndictmentMaps().original())
          .containsKey(replacement)
          .doesNotContainKey(removed);
      try (var fresh = director(operation, solution)) {
        assertOracle(fresh, operation, solution);
        assertThat(director.getIndictmentMaps()).isEqualTo(fresh.getIndictmentMaps());
      }

      // Replace an unselected member of the inner minimum group as well as a right witness.
      director.beforeProblemFactRemoved(groupMember);
      solution.witnesses.remove(groupMember);
      director.afterProblemFactRemoved(groupMember);
      assertOracle(director, operation, solution);
      assertPrecomputedOriginalReferences(director, solution);
      if (operation != Operation.PRECOMPUTE_EXISTS) {
        assertThat(director.calculateScore()).isEqualTo(score);
      }
      var groupReplacement =
          new Witness("group-replacement", groupMember.group, groupMember.ceiling);
      director.beforeProblemFactAdded(groupReplacement);
      solution.witnesses.add(groupReplacement);
      director.afterProblemFactAdded(groupReplacement);
      assertOracle(director, operation, solution);
      assertPrecomputedOriginalReferences(director, solution);
      assertThat(director.calculateScore()).isEqualTo(score);
      assertThat(director.getIndictmentMaps().original())
          .containsKey(groupReplacement)
          .doesNotContainKey(groupMember);

      // Removing the final witness must retract an existence output completely.
      for (var witness : List.of(extraWitness, replacement)) {
        director.beforeProblemFactRemoved(witness);
        solution.witnesses.remove(witness);
        director.afterProblemFactRemoved(witness);
        assertOracle(director, operation, solution);
        assertPrecomputedOriginalReferences(director, solution);
      }
      director.beforeProblemFactAdded(removed);
      solution.witnesses.add(removed);
      director.afterProblemFactAdded(removed);
      assertOracle(director, operation, solution);
      assertPrecomputedOriginalReferences(director, solution);

      assertMap(snapshot.terminal(), before.terminal);
      assertMap(snapshot.original(), before.original);
      assertThat(snapshot.original()).containsKey(removed).doesNotContainKey(replacement);
      assertThat(snapshot.original()).containsKey(groupMember).doesNotContainKey(groupReplacement);
    }
  }

  private static void assertPrecomputedOriginalReferences(
      BavetConstraintStreamScoreDirector<Sample, SimpleScore> director, Sample solution) {
    // Derived Key/aggregate values and unrelated entities are not original sources. Merely
    // copying replay dependency sets cannot supply the hidden group members or exists witnesses.
    for (var entry : director.getIndictmentMaps().original().entrySet()) {
      assertThat(solution.witnesses.stream().anyMatch(witness -> witness == entry.getKey()))
          .isTrue();
      assertThat(entry.getValue().indictedObject()).isSameAs(entry.getKey());
    }
  }

  @Test
  void sharedUpstreamDoesNotLeakWitnessesIntoSiblingConstraint() {
    var solution = Sample.create();
    ConstraintProvider provider =
        factory -> {
          var shared = factory.forEach(Item.class).map(item -> new Key(item.value));
          return new Constraint[] {
            shared
                .ifExists(Witness.class, Joiners.equal(Key::number, witness -> witness.group))
                .penalize(SimpleScore.ONE)
                .asConstraint("exists"),
            shared.penalize(SimpleScore.ONE).asConstraint("sibling")
          };
        };
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<Sample, SimpleScore>(
            SolutionDescriptor.buildSolutionDescriptor(Sample.class, Item.class),
            provider,
            EnvironmentMode.NO_ASSERT);
    try (var director =
        factory
            .createScoreDirectorBuilder()
            .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED_WITH_ORIGINAL_INDICTMENTS)
            .build()) {
      director.setWorkingSolution(solution);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-7));
      var original = director.getIndictmentMaps().original();
      for (var witness : solution.witnesses) {
        var indictment = original.get(witness);
        assertThat(indictment.matches())
            .allSatisfy(match -> assertThat(match.constraintRef().id()).isEqualTo("exists"));
        assertThat(indictment.matchCount()).isEqualTo(witness.group == 1 ? 2 : 1);
      }
      for (var item : solution.items) {
        assertThat(original.get(item).matchCount()).isEqualTo(item.value == 3 ? 1 : 2);
      }
    }
  }

  private static BavetConstraintStreamScoreDirector<Sample, SimpleScore> director(
      Operation operation, Sample solution) {
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<Sample, SimpleScore>(
            SolutionDescriptor.buildSolutionDescriptor(Sample.class, Item.class),
            constraints -> new Constraint[] {operation.constraint(constraints)},
            EnvironmentMode.NO_ASSERT);
    var director =
        factory
            .createScoreDirectorBuilder()
            .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED_WITH_ORIGINAL_INDICTMENTS)
            .build();
    director.setWorkingSolution(solution);
    return director;
  }

  private static void assertOracle(
      BavetConstraintStreamScoreDirector<Sample, SimpleScore> director,
      Operation operation,
      Sample solution) {
    var occurrences = operation.occurrences(solution);
    var expected = expected(occurrences);
    assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-occurrences.size()));
    var maps = director.getSession().getScoreInliner().getIndictmentMaps();
    assertMap(maps.terminal(), expected.terminal);
    assertMap(maps.original(), expected.original);
  }

  private static void assertMap(
      Map<Object, IndictmentAnalysis<SimpleScore>> actual, Map<Object, Expected> expected) {
    assertThat(actual).isNotNull().containsOnlyKeys(expected.keySet());
    for (var entry : expected.entrySet()) {
      var indictment = actual.get(entry.getKey());
      var expectedEntry = entry.getValue();
      assertThat(indictment.indictedObject()).isEqualTo(entry.getKey());
      assertThat(indictment.score()).isEqualTo(SimpleScore.of(-expectedEntry.count));
      assertThat(indictment.matchCount()).isEqualTo(expectedEntry.count);
      var actualMatches = new LinkedHashMap<List<Object>, Long>();
      for (var match : indictment.matches()) {
        assertThat(match.constraintRef().id()).isEqualTo("oracle");
        var justification = (DefaultConstraintJustification) match.justification();
        var previous = actualMatches.put(justification.getFacts(), match.matchCount());
        assertThat(previous).as("A public indictment match must be aggregated once").isNull();
        assertThat(match.score()).isEqualTo(SimpleScore.of(-match.matchCount()));
      }
      assertThat(actualMatches).containsExactlyInAnyOrderEntriesOf(expectedEntry.matches);
    }
  }

  private static ExpectedMaps expected(List<Occurrence> occurrences) {
    var terminal = new LinkedHashMap<Object, Expected>();
    var original = new LinkedHashMap<Object, Expected>();
    for (var occurrence : occurrences) {
      accumulate(terminal, occurrence.terminal, occurrence.terminal);
      accumulate(original, occurrence.original, occurrence.terminal);
    }
    return new ExpectedMaps(terminal, original);
  }

  private static void accumulate(
      Map<Object, Expected> output, Iterable<?> objects, List<Object> justification) {
    // Public object equality deduplicates targets within one raw occurrence only.
    var unique = new LinkedHashSet<Object>();
    for (var object : objects) {
      if (object != null) {
        unique.add(object);
      }
    }
    for (var object : unique) {
      var expected = output.computeIfAbsent(object, ignored -> new Expected());
      expected.count++;
      expected.matches.merge(justification, 1L, Long::sum);
    }
  }

  private static Occurrence occurrence(List<Object> terminal, Object... originals) {
    var sources = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    sources.addAll(Arrays.asList(originals));
    return new Occurrence(terminal, sources);
  }

  private static List<Object> tuple(Object... objects) {
    return Collections.unmodifiableList(Arrays.asList(objects));
  }

  private record Occurrence(List<Object> terminal, Set<Object> original) {}

  private record ExpectedMaps(Map<Object, Expected> terminal, Map<Object, Expected> original) {}

  private static final class Expected {
    long count;
    final Map<List<Object>, Long> matches = new LinkedHashMap<>();
  }

  enum Operation {
    FILTER,
    MAP_1,
    MAP_2,
    MAP_3,
    MAP_4,
    MAP_FROM_2,
    MAP_FROM_3,
    MAP_FROM_4,
    EXPAND_2,
    EXPAND_3,
    EXPAND_4,
    FLATTEN,
    FLATTEN_DISTINCT,
    FLATTEN_2,
    FLATTEN_3,
    FLATTEN_4,
    JOIN_2,
    JOIN_3,
    JOIN_4,
    EXISTS_1,
    EXISTS_2,
    EXISTS_3,
    EXISTS_4,
    EXISTS_MAPPED,
    NOT_EXISTS,
    GROUP_MIN,
    GROUP_DISTINCT,
    GROUP_COUNT_SUM,
    GROUP_FROM_2,
    GROUP_FROM_3,
    GROUP_FROM_4,
    DISTINCT,
    CONCAT,
    CONCAT_DISTINCT,
    CONCAT_PADDING,
    COMPLEMENT,
    PRECOMPUTE,
    PRECOMPUTE_2,
    PRECOMPUTE_3,
    PRECOMPUTE_4,
    PRECOMPUTE_GROUP,
    PRECOMPUTE_EXISTS,
    PRECOMPUTE_GROUP_EXISTS;

    Constraint constraint(ConstraintFactory factory) {
      var source = factory.forEach(Item.class);
      return switch (this) {
        case FILTER ->
            source.filter(item -> item.weight < 5).penalize(SimpleScore.ONE).asConstraint("oracle");
        case MAP_1 ->
            source
                .map(item -> new Key(item.value))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case MAP_2 ->
            source
                .map(item -> new Key(item.value), item -> item.id)
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case MAP_3 ->
            source
                .map(item -> new Key(item.value), item -> item.id, item -> item.weight)
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case MAP_4 ->
            source
                .map(
                    item -> new Key(item.value),
                    item -> item.id,
                    item -> item.weight,
                    item -> new Key(item.value))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case MAP_FROM_2 ->
            source
                .expand(item -> item.id)
                .map((item, id) -> new Key(item.value))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case MAP_FROM_3 ->
            source
                .expand(item -> item.id, item -> item.weight)
                .map((item, id, weight) -> new Key(item.value))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case MAP_FROM_4 ->
            source
                .expand(item -> item.id, item -> item.weight, item -> item.value)
                .map((item, id, weight, value) -> new Key(item.value))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case EXPAND_2 ->
            source
                .expand(item -> new Key(item.value))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case EXPAND_3 ->
            source
                .expand(item -> new Key(item.value), item -> item.id)
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case EXPAND_4 ->
            source
                .expand(item -> new Key(item.value), item -> item.id, item -> new Key(item.value))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case FLATTEN ->
            source
                .flattenLast(item -> item.labels)
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case FLATTEN_DISTINCT ->
            source
                .flattenLast(item -> item.labels)
                .distinct()
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case FLATTEN_2 ->
            source
                .expand(item -> item.labels)
                .flattenLast(labels -> labels)
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case FLATTEN_3 ->
            source
                .expand(item -> item.id, item -> item.labels)
                .flattenLast(labels -> labels)
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case FLATTEN_4 ->
            source
                .expand(item -> item.id, item -> item.weight, item -> item.labels)
                .flattenLast(labels -> labels)
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case JOIN_2 ->
            source
                .join(
                    Witness.class,
                    Joiners.equal(item -> item.value, witness -> witness.group),
                    Joiners.lessThan(item -> item.weight, witness -> witness.ceiling))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case JOIN_3 ->
            source
                .join(Witness.class, Joiners.equal(item -> item.value, witness -> witness.group))
                .join(
                    Witness.class,
                    Joiners.equal((item, witness) -> item.value, witness -> witness.group))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case JOIN_4 ->
            source
                .join(Witness.class, Joiners.equal(item -> item.value, witness -> witness.group))
                .join(
                    Witness.class,
                    Joiners.equal((item, witness) -> item.value, witness -> witness.group))
                .join(
                    Witness.class,
                    Joiners.equal((item, one, two) -> item.value, witness -> witness.group))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case EXISTS_1 ->
            source
                .ifExists(
                    Witness.class, Joiners.equal(item -> item.value, witness -> witness.group))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case EXISTS_MAPPED ->
            source
                .ifExists(
                    factory.forEach(Witness.class).map(witness -> new Key(witness.group)),
                    Joiners.equal(item -> item.value, Key::number))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case EXISTS_2 ->
            source
                .expand(item -> new Key(item.value))
                .ifExists(
                    Witness.class,
                    Joiners.equal((item, key) -> item.value, witness -> witness.group))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case EXISTS_3 ->
            source
                .expand(item -> new Key(item.value), item -> item.id)
                .ifExists(
                    Witness.class,
                    Joiners.equal((item, key, id) -> item.value, witness -> witness.group))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case EXISTS_4 ->
            source
                .expand(item -> new Key(item.value), item -> item.id, item -> item.weight)
                .ifExists(
                    Witness.class,
                    Joiners.equal((item, key, id, weight) -> item.value, witness -> witness.group))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case NOT_EXISTS ->
            source
                .ifNotExists(
                    Witness.class, Joiners.equal(item -> item.value, witness -> witness.group))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case GROUP_MIN ->
            source
                .groupBy(item -> item.value, ConstraintCollectors.min((Item item) -> item.weight))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case GROUP_DISTINCT ->
            source
                .groupBy(item -> item.value, ConstraintCollectors.countDistinct(item -> item.value))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case GROUP_COUNT_SUM ->
            source
                .groupBy(
                    item -> item.value,
                    ConstraintCollectors.count(),
                    ConstraintCollectors.sum(item -> item.weight))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case GROUP_FROM_2 ->
            source
                .expand(item -> item.id)
                .groupBy((item, id) -> item.value, ConstraintCollectors.countBi())
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case GROUP_FROM_3 ->
            source
                .expand(item -> item.id, item -> item.weight)
                .groupBy((item, id, weight) -> item.value, ConstraintCollectors.countTri())
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case GROUP_FROM_4 ->
            source
                .expand(item -> item.id, item -> item.weight, item -> item.value)
                .groupBy((item, id, weight, value) -> item.value, ConstraintCollectors.countQuad())
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case DISTINCT ->
            source
                .map(item -> new Key(item.value))
                .distinct()
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case CONCAT ->
            source
                .map(item -> new Key(item.value))
                .concat(source.filter(item -> item.weight < 5).map(item -> new Key(item.value)))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case CONCAT_DISTINCT ->
            source
                .map(item -> new Key(item.value))
                .concat(source.filter(item -> item.weight < 5).map(item -> new Key(item.value)))
                .distinct()
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case CONCAT_PADDING ->
            source
                .concat(source.expand(item -> new Key(item.value)))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case COMPLEMENT ->
            source
                .filter(item -> item.weight < 5)
                .complement(Item.class)
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case PRECOMPUTE ->
            factory
                .precompute(
                    precompute -> precompute.forEachUnfiltered(Item.class).map(item -> item.id))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case PRECOMPUTE_2 ->
            factory
                .precompute(
                    precompute -> precompute.forEachUnfiltered(Item.class).expand(item -> item.id))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case PRECOMPUTE_3 ->
            factory
                .precompute(
                    precompute ->
                        precompute
                            .forEachUnfiltered(Item.class)
                            .expand(item -> item.id, item -> item.weight))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case PRECOMPUTE_4 ->
            factory
                .precompute(
                    precompute ->
                        precompute
                            .forEachUnfiltered(Item.class)
                            .expand(item -> item.id, item -> item.weight, item -> item.labels))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case PRECOMPUTE_GROUP ->
            factory
                .precompute(
                    precompute ->
                        precompute
                            .forEachUnfiltered(Witness.class)
                            .groupBy(Witness::group, ConstraintCollectors.min(Witness::ceiling)))
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case PRECOMPUTE_EXISTS ->
            factory
                .precompute(
                    precompute -> {
                      var facts = precompute.forEachUnfiltered(Witness.class);
                      return facts
                          .filter(witness -> witness.ceiling <= 8)
                          .map(witness -> new Key(witness.group))
                          .ifExists(
                              facts
                                  .filter(witness -> witness.ceiling > 8)
                                  .map(witness -> new Key(witness.group)),
                              Joiners.equal());
                    })
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
        case PRECOMPUTE_GROUP_EXISTS ->
            factory
                .precompute(
                    precompute -> {
                      var facts = precompute.forEachUnfiltered(Witness.class);
                      return facts
                          .filter(witness -> witness.ceiling <= 8)
                          .groupBy(Witness::group, ConstraintCollectors.min(Witness::ceiling))
                          .ifExists(
                              facts.filter(witness -> witness.ceiling > 8),
                              Joiners.equal((group, minimum) -> group, Witness::group));
                    })
                .penalize(SimpleScore.ONE)
                .asConstraint("oracle");
      };
    }

    List<Occurrence> occurrences(Sample sample) {
      if (this == PRECOMPUTE_GROUP
          || this == PRECOMPUTE_EXISTS
          || this == PRECOMPUTE_GROUP_EXISTS) {
        return precomputedFactOccurrences(sample);
      }
      var result = new ArrayList<Occurrence>();
      var grouped = new LinkedHashMap<Integer, List<Item>>();
      for (var item : sample.items) {
        grouped.computeIfAbsent(item.value, ignored -> new ArrayList<>()).add(item);
        var key = new Key(item.value);
        switch (this) {
          case FILTER -> {
            if (item.weight < 5) result.add(occurrence(tuple(item), item));
          }
          case MAP_1, MAP_FROM_2, MAP_FROM_3, MAP_FROM_4, DISTINCT, CONCAT_DISTINCT ->
              result.add(occurrence(tuple(key), item));
          case MAP_2 -> result.add(occurrence(tuple(key, item.id), item));
          case MAP_3 -> result.add(occurrence(tuple(key, item.id, item.weight), item));
          case MAP_4 ->
              result.add(occurrence(tuple(key, item.id, item.weight, new Key(item.value)), item));
          case EXPAND_2 -> result.add(occurrence(tuple(item, key), item));
          case EXPAND_3 -> result.add(occurrence(tuple(item, key, item.id), item));
          case EXPAND_4 ->
              result.add(occurrence(tuple(item, key, item.id, new Key(item.value)), item));
          case FLATTEN, FLATTEN_DISTINCT -> {
            for (var label : item.labels) result.add(occurrence(tuple(label), item));
          }
          case FLATTEN_2, FLATTEN_3, FLATTEN_4 -> {
            for (var label : item.labels) {
              var terminal =
                  switch (this) {
                    case FLATTEN_2 -> tuple(item, label);
                    case FLATTEN_3 -> tuple(item, item.id, label);
                    default -> tuple(item, item.id, item.weight, label);
                  };
              result.add(occurrence(terminal, item));
            }
          }
          case JOIN_2, JOIN_3, JOIN_4 -> {
            for (var one : sample.witnesses) {
              if (one.group != item.value) continue;
              if (this == JOIN_2) {
                if (item.weight < one.ceiling) result.add(occurrence(tuple(item, one), item, one));
                continue;
              }
              for (var two : sample.witnesses) {
                if (two.group != item.value) continue;
                if (this == JOIN_3) {
                  result.add(occurrence(tuple(item, one, two), item, one, two));
                  continue;
                }
                for (var three : sample.witnesses) {
                  if (three.group == item.value)
                    result.add(occurrence(tuple(item, one, two, three), item, one, two, three));
                }
              }
            }
          }
          case EXISTS_1, EXISTS_2, EXISTS_3, EXISTS_4, EXISTS_MAPPED, NOT_EXISTS -> {
            var witnesses = new ArrayList<Object>();
            for (var witness : sample.witnesses) {
              if (witness.group == item.value) witnesses.add(witness);
            }
            if (this == NOT_EXISTS) {
              if (witnesses.isEmpty()) result.add(occurrence(tuple(item), item));
            } else if (!witnesses.isEmpty()) {
              witnesses.add(item);
              var terminal =
                  switch (this) {
                    case EXISTS_1, EXISTS_MAPPED -> tuple(item);
                    case EXISTS_2 -> tuple(item, key);
                    case EXISTS_3 -> tuple(item, key, item.id);
                    default -> tuple(item, key, item.id, item.weight);
                  };
              result.add(occurrence(terminal, witnesses.toArray()));
            }
          }
          case CONCAT -> {
            result.add(occurrence(tuple(key), item));
            if (item.weight < 5) result.add(occurrence(tuple(key), item));
          }
          case CONCAT_PADDING -> {
            result.add(occurrence(tuple(item, null), item));
            result.add(occurrence(tuple(item, key), item));
          }
          case COMPLEMENT -> result.add(occurrence(tuple(item), item));
          case PRECOMPUTE -> result.add(occurrence(tuple(item.id), item));
          case PRECOMPUTE_2 -> result.add(occurrence(tuple(item, item.id), item));
          case PRECOMPUTE_3 -> result.add(occurrence(tuple(item, item.id, item.weight), item));
          case PRECOMPUTE_4 ->
              result.add(occurrence(tuple(item, item.id, item.weight, item.labels), item));
          case GROUP_MIN,
              GROUP_DISTINCT,
              GROUP_COUNT_SUM,
              GROUP_FROM_2,
              GROUP_FROM_3,
              GROUP_FROM_4 -> {}
          case PRECOMPUTE_GROUP, PRECOMPUTE_EXISTS, PRECOMPUTE_GROUP_EXISTS ->
              throw new IllegalStateException(
                  "Fact-only precompute uses the separate loop oracle.");
        }
      }
      if (this == GROUP_MIN
          || this == GROUP_DISTINCT
          || this == GROUP_COUNT_SUM
          || this == GROUP_FROM_2
          || this == GROUP_FROM_3
          || this == GROUP_FROM_4) {
        for (var entry : grouped.entrySet()) {
          int minimum = Integer.MAX_VALUE;
          long sum = 0;
          for (var item : entry.getValue()) {
            minimum = Math.min(minimum, item.weight);
            sum += item.weight;
          }
          var terminal =
              switch (this) {
                case GROUP_MIN -> tuple(entry.getKey(), minimum);
                case GROUP_DISTINCT -> tuple(entry.getKey(), 1L);
                case GROUP_FROM_2, GROUP_FROM_3, GROUP_FROM_4 ->
                    tuple(entry.getKey(), (long) entry.getValue().size());
                default -> tuple(entry.getKey(), (long) entry.getValue().size(), sum);
              };
          result.add(occurrence(terminal, entry.getValue().toArray()));
        }
      }
      if (this == DISTINCT || this == CONCAT_DISTINCT || this == FLATTEN_DISTINCT) {
        var unique = new LinkedHashMap<List<Object>, Occurrence>();
        for (var occurrence : result) {
          var previous = unique.get(occurrence.terminal);
          if (previous == null) unique.put(occurrence.terminal, occurrence);
          else previous.original.addAll(occurrence.original);
        }
        return new ArrayList<>(unique.values());
      }
      return result;
    }

    private List<Occurrence> precomputedFactOccurrences(Sample sample) {
      var result = new ArrayList<Occurrence>();
      if (this == PRECOMPUTE_EXISTS) {
        for (var left : sample.witnesses) {
          if (left.ceiling > 8) continue;
          var originals = new ArrayList<Object>();
          originals.add(left);
          for (var right : sample.witnesses) {
            if (right.ceiling > 8 && right.group == left.group) originals.add(right);
          }
          if (originals.size() > 1) {
            result.add(occurrence(tuple(new Key(left.group)), originals.toArray()));
          }
        }
        return result;
      }
      var groups = new LinkedHashMap<Integer, List<Witness>>();
      for (var fact : sample.witnesses) {
        if (this == PRECOMPUTE_GROUP || fact.ceiling <= 8) {
          groups.computeIfAbsent(fact.group, ignored -> new ArrayList<>()).add(fact);
        }
      }
      for (var entry : groups.entrySet()) {
        int minimum = Integer.MAX_VALUE;
        var originals = new ArrayList<Object>(entry.getValue());
        for (var member : entry.getValue()) minimum = Math.min(minimum, member.ceiling);
        if (this == PRECOMPUTE_GROUP_EXISTS) {
          boolean exists = false;
          for (var witness : sample.witnesses) {
            if (witness.ceiling > 8 && witness.group == entry.getKey()) {
              exists = true;
              originals.add(witness);
            }
          }
          if (!exists) continue;
        }
        result.add(occurrence(tuple(entry.getKey(), minimum), originals.toArray()));
      }
      return result;
    }
  }

  record Key(int number) {}

  record Witness(String id, int group, int ceiling) {}

  public static class PublicProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {Operation.GROUP_MIN.constraint(factory)};
    }
  }

  @PlanningEntity
  public static class Item {
    @PlanningId public String id;

    @PlanningVariable(valueRangeProviderRefs = "values")
    public Integer value;

    int weight;
    List<String> labels;

    public Item() {}

    Item(String id, int value, int weight, String... labels) {
      this.id = id;
      this.value = value;
      this.weight = weight;
      this.labels = List.of(labels);
    }

    @Override
    public String toString() {
      return id;
    }
  }

  @PlanningSolution
  public static class Sample {
    @PlanningEntityCollectionProperty public List<Item> items;
    @ProblemFactCollectionProperty public List<Witness> witnesses;

    @ValueRangeProvider(id = "values")
    @ProblemFactCollectionProperty
    public List<Integer> values;

    @PlanningScore public SimpleScore score;

    public Sample() {}

    static Sample create() {
      var sample = new Sample();
      sample.values = List.of(1, 2, 3);
      sample.items =
          new ArrayList<>(
              List.of(
                  new Item("a", 1, 1, "x", "x", "y"),
                  new Item("b", 1, 3, "x", "z"),
                  new Item("c", 2, 6, "z"),
                  new Item("d", 3, 2)));
      sample.witnesses =
          new ArrayList<>(
              List.of(
                  new Witness("first", 1, 2),
                  new Witness("second", 1, 10),
                  new Witness("third", 2, 8)));
      return sample;
    }
  }
}
