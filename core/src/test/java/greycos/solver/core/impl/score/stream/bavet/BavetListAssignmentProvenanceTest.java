package greycos.solver.core.impl.score.stream.bavet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.MatchOccurrenceAnalysis;
import greycos.solver.core.api.score.analysis.RawScoreAnalysis;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.api.solver.IndictmentFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisOptions;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.declarative.ConsistencyTracker;
import greycos.solver.core.impl.move.MoveDirector;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Expected sources come from the public constraint operations and ordinary list membership. */
class BavetListAssignmentProvenanceTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void publicAnalysisPreservesLogicalSourcesWithAndWithoutInverseShadows(boolean raw) {
    var problem = Problem.create();
    var manager =
        SolutionManager.<Problem, SimpleScore>create(
            SolverFactory.create(
                new SolverConfig()
                    .withSolutionClass(Problem.class)
                    .withEntityClasses(
                        Owner.class, Item.class, First.class, Second.class, Inverse.class)
                    .withConstraintProviderClass(Constraints.class)));
    var expected = replay(problem);
    var original = manager.analyzeWithOptions(problem, options(raw));
    assertAnalysis(original, expected, true, raw);

    // Both optional views must leave the score and terminal selection unchanged.
    var terminal =
        manager.analyzeWithOptions(
            problem,
            new ScoreAnalysisOptions(
                ScoreAnalysisFetchPolicy.FETCH_ALL, IndictmentFetchPolicy.TERMINAL, raw));
    assertAnalysis(terminal, expected, false, raw);
    var legacy = manager.analyze(problem);
    assertThat(legacy.score()).isEqualTo(original.score());
    assertThat(legacy.indictmentMap()).isNull();
    assertThat(legacy.originalIndictmentMap()).isNull();
    assertThat(legacy.rawAnalysis()).isNull();
    try (var disabled = director(problem, ConstraintMatchPolicy.DISABLED)) {
      assertThat(disabled.calculateScore().raw()).isEqualTo(original.score());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void ownerUpdatesAndUndoRefreshSourcesWithoutChangingEarlierSnapshots(boolean raw) {
    var problem = Problem.create();
    var initialExpected = replay(problem);
    ScoreAnalysis<SimpleScore> initial;
    try (var director = director(problem, ConstraintMatchPolicy.match(options(raw)))) {
      initial = analyze(director, problem, raw);
      var session = director.getSession();
      var firstVariable =
          director
              .getSolutionDescriptor()
              .getMetaModel()
              .genuineEntity(Owner.class)
              .listVariable("first", First.class);
      var moves = new MoveDirector<>(director);
      var left = problem.owners.getFirst();
      var right = problem.owners.getLast();
      var value = left.first.getFirst();

      // These moves notify owner lists; they never change the element's basic variable.
      moves.execute(view -> view.unassignValue(firstVariable, value));
      analyze(director, problem, raw);
      moves.execute(view -> view.assignValueAndAdd(firstVariable, value, right, 0));
      analyze(director, problem, raw);
      moves.execute(view -> view.moveValueBetweenLists(firstVariable, right, 0, left, 0));
      analyze(director, problem, raw);

      Move<Problem> transfer = view -> view.moveValueBetweenLists(firstVariable, left, 0, right, 0);
      var beforeUndoExpected = replay(problem);
      var beforeUndo = analyze(director, problem, raw);
      var undo =
          moves.executeTemporaryProducingUndoMove(
              transfer, score -> analyze(director, problem, raw));
      assertAnalysis(analyze(director, problem, raw), beforeUndoExpected, true, raw);
      moves.execute(transfer);
      analyze(director, problem, raw);
      moves.execute(undo);
      assertAnalysis(analyze(director, problem, raw), beforeUndoExpected, true, raw);
      assertAnalysis(beforeUndo, beforeUndoExpected, true, raw);

      director.beforeVariableChanged(value, "number");
      value.number = null;
      director.afterVariableChanged(value, "number");
      analyze(director, problem, raw);
      director.beforeVariableChanged(value, "number");
      value.number = 1;
      director.afterVariableChanged(value, "number");
      analyze(director, problem, raw);

      // Keep a valid model while retracting an element root. The including-unassigned constraint
      // still scores this element after unassignment, and must stop scoring it on root retraction.
      moves.execute(view -> view.unassignValue(firstVariable, value));
      analyze(director, problem, raw);
      session.retract(value);
      problem.firstValues.remove(value);
      analyze(director, problem, raw);
      problem.firstValues.addFirst(value);
      session.insert(value);
      analyze(director, problem, raw);
      moves.execute(view -> view.assignValueAndAdd(firstVariable, value, left, 0));
      analyze(director, problem, raw);

      // Isolate owner-root removal from the public structural refresh that rebuilds the network.
      // Only the no-inverse families live on this owner, so their inverse fields need no updates.
      assertThat(left.inverse).isEmpty();
      session.retract(left);
      problem.owners.remove(left);
      analyze(director, problem, raw);
      problem.owners.addFirst(left);
      session.insert(left);
      analyze(director, problem, raw);
      assertThat(director.getSession()).isSameAs(session);

      assertAnalysis(initial, initialExpected, true, raw);
      assertThatThrownBy(() -> initial.originalIndictmentMap().clear())
          .isInstanceOf(UnsupportedOperationException.class);
      if (raw) {
        assertThatThrownBy(
                () ->
                    initial
                        .rawAnalysis()
                        .matchOccurrences()
                        .getFirst()
                        .originalIndictedObjects()
                        .clear())
            .isInstanceOf(UnsupportedOperationException.class);
      }

      var replacement = Problem.create();
      director.setWorkingSolution(replacement);
      analyze(director, replacement, raw);
      assertThat(director.getSession()).isNotSameAs(session);
      assertAnalysis(initial, initialExpected, true, raw);
    }
    assertAnalysis(initial, initialExpected, true, raw);
  }

  private static ScoreAnalysisOptions options(boolean raw) {
    return new ScoreAnalysisOptions(
        ScoreAnalysisFetchPolicy.FETCH_ALL, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL, raw);
  }

  private static BavetConstraintStreamScoreDirector<Problem, SimpleScore> director(
      Problem problem, ConstraintMatchPolicy policy) {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            Problem.class, Owner.class, Item.class, First.class, Second.class, Inverse.class);
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<Problem, SimpleScore>(
            descriptor, new Constraints(), EnvironmentMode.NO_ASSERT);
    var director = factory.createScoreDirectorBuilder().withConstraintMatchPolicy(policy).build();
    director.setWorkingSolution(problem);
    return director;
  }

  private static ScoreAnalysis<SimpleScore> analyze(
      BavetConstraintStreamScoreDirector<Problem, SimpleScore> director,
      Problem problem,
      boolean raw) {
    var analysis = director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL);
    var expected = replay(problem);
    assertAnalysis(analysis, expected, true, raw);
    // A fresh Bavet session reads the already repaired solution without resetting shared shadows.
    var fresh =
        director
            .getScoreDirectorFactory()
            .newSession(
                problem, new ConsistencyTracker<>(), director.getConstraintMatchPolicy(), true);
    director.getSolutionDescriptor().visitAll(problem, fresh::insert);
    assertThat(fresh.calculateScore()).isEqualTo(analysis.score());
    var details = fresh.getScoreInliner().getAnalysisDetails();
    assertIndictments(details.indictments().terminal(), expected, ExpectedOccurrence::terminal);
    assertIndictments(details.indictments().original(), expected, ExpectedOccurrence::sources);
    assertRaw(details.rawAnalysis(), expected, true, raw);
    return analysis;
  }

  private static void assertAnalysis(
      ScoreAnalysis<SimpleScore> analysis,
      List<ExpectedOccurrence> expected,
      boolean originals,
      boolean raw) {
    assertThat(analysis.score())
        .isEqualTo(SimpleScore.of(expected.stream().mapToLong(ExpectedOccurrence::score).sum()));
    assertIndictments(analysis.indictmentMap(), expected, ExpectedOccurrence::terminal);
    if (originals) {
      assertIndictments(analysis.originalIndictmentMap(), expected, ExpectedOccurrence::sources);
    } else {
      assertThat(analysis.originalIndictmentMap()).isNull();
    }
    assertRaw(analysis.rawAnalysis(), expected, originals, raw);
  }

  private static void assertRaw(
      RawScoreAnalysis<SimpleScore> analysis,
      List<ExpectedOccurrence> expected,
      boolean originals,
      boolean raw) {
    if (!raw) {
      assertThat(analysis).isNull();
      return;
    }
    assertThat(analysis.originalsFetched()).isEqualTo(originals);
    var remaining = new ArrayList<>(expected);
    var expectedIndex = new LinkedHashMap<Object, List<MatchOccurrenceAnalysis<SimpleScore>>>();
    for (var row : analysis.matchOccurrences()) {
      var found =
          remaining.stream().filter(candidate -> matches(row, candidate, originals)).findFirst();
      assertThat(found).as("raw occurrence of %s", row.constraintRef()).isPresent();
      var occurrence = found.orElseThrow();
      remaining.remove(occurrence);
      assertThat(row.occurrenceSign()).isEqualTo(1);
      if (originals) {
        for (var source : new LinkedHashSet<>(occurrence.sources())) {
          expectedIndex.computeIfAbsent(source, ignored -> new ArrayList<>()).add(row);
        }
      } else {
        assertThat(row.originalIndictedObjects()).isNull();
      }
    }
    assertThat(remaining).as("missing raw occurrences").isEmpty();
    if (originals) {
      assertThat(analysis.originalIndictmentMap().keySet())
          .containsExactlyInAnyOrderElementsOf(expectedIndex.keySet());
      expectedIndex.forEach(
          (object, rows) -> {
            var indexed = analysis.originalIndictmentMap().get(object);
            assertThat(indexed).hasSameSizeAs(rows);
            for (var row : rows) {
              assertThat(indexed.stream().filter(candidate -> candidate == row).count())
                  .as("canonical occurrence indexed for %s", object)
                  .isEqualTo(1);
            }
          });
    } else {
      assertThat(analysis.originalIndictmentMap()).isNull();
    }
  }

  private static boolean matches(
      MatchOccurrenceAnalysis<SimpleScore> actual, ExpectedOccurrence expected, boolean originals) {
    return actual.constraintRef().id().equals(expected.constraint())
        && actual.matchScore().equals(SimpleScore.of(expected.score()))
        && actual.indictedObjects().equals(expected.terminal())
        && (!originals
            || new LinkedHashSet<>(actual.originalIndictedObjects())
                .equals(new LinkedHashSet<>(expected.sources())));
  }

  private static void assertIndictments(
      Map<Object, IndictmentAnalysis<SimpleScore>> actual,
      List<ExpectedOccurrence> occurrences,
      Function<ExpectedOccurrence, List<Object>> objects) {
    var expected = new LinkedHashMap<Object, Map<String, Contribution>>();
    for (var occurrence : occurrences) {
      for (var object : new LinkedHashSet<>(objects.apply(occurrence))) {
        expected
            .computeIfAbsent(object, ignored -> new LinkedHashMap<>())
            .merge(
                occurrence.constraint(),
                new Contribution(occurrence.score(), 1),
                Contribution::plus);
      }
    }
    assertThat(actual).isNotNull();
    assertThat(actual.keySet()).containsExactlyInAnyOrderElementsOf(expected.keySet());
    expected.forEach(
        (object, contributions) -> {
          var indictment = actual.get(object);
          var actualContributions = new LinkedHashMap<String, Contribution>();
          indictment
              .matches()
              .forEach(
                  match ->
                      actualContributions.merge(
                          match.constraintRef().id(),
                          new Contribution(match.score().score(), match.matchCount()),
                          Contribution::plus));
          assertThat(actualContributions)
              .as("constraint contributions of %s", object)
              .isEqualTo(contributions);
          assertThat(indictment.score())
              .isEqualTo(
                  SimpleScore.of(
                      contributions.values().stream().mapToLong(Contribution::score).sum()));
          assertThat(indictment.matchCount())
              .isEqualTo(contributions.values().stream().mapToLong(Contribution::count).sum());
        });
  }

  private record Contribution(long score, long count) {
    Contribution plus(Contribution other) {
      return new Contribution(score + other.score, count + other.count);
    }
  }

  private record ExpectedOccurrence(
      String constraint, long score, List<Object> terminal, List<Object> sources) {}

  private static List<ExpectedOccurrence> replay(Problem problem) {
    var result = new ArrayList<ExpectedOccurrence>();
    replayFamily(problem, "first", problem.firstValues, owner -> owner.first, result);
    replayFamily(problem, "second", problem.secondValues, owner -> owner.second, result);
    replayFamily(problem, "inverse", problem.inverseValues, owner -> owner.inverse, result);
    for (var value : problem.firstValues) {
      result.add(
          new ExpectedOccurrence("first-including-unassigned", -1, List.of(value), List.of(value)));
    }
    var assigned = new ArrayList<Item>();
    for (var owner : problem.owners) {
      result.add(new ExpectedOccurrence("common-interface", -1, List.of(owner), List.of(owner)));
      assigned.addAll(owner.first);
      assigned.addAll(owner.second);
      assigned.addAll(owner.inverse);
    }
    assigned.addAll(problem.otherValues);
    for (var item : assigned) {
      if (item.number != null) {
        result.add(new ExpectedOccurrence("common-interface", -1, List.of(item), List.of(item)));
        result.add(new ExpectedOccurrence("common-base", -1, List.of(item), List.of(item)));
      }
    }
    return List.copyOf(result);
  }

  private static <Value_ extends Item> void replayFamily(
      Problem problem,
      String family,
      List<Value_> values,
      Function<Owner, List<Value_>> list,
      List<ExpectedOccurrence> result) {
    var groups = new LinkedHashMap<Integer, List<Object>>();
    for (var value : values) {
      if (value.number == null) {
        continue;
      }
      for (var owner : problem.owners) {
        if (list.apply(owner).contains(value)) {
          result.add(new ExpectedOccurrence(family + "-plain", -1, List.of(value), List.of(value)));
          result.add(
              new ExpectedOccurrence(
                  family + "-exists", -1, List.of(value), List.of(value, owner)));
          groups.computeIfAbsent(value.number, ignored -> new ArrayList<>()).add(value);
        }
      }
    }
    groups.forEach(
        (number, members) ->
            result.add(
                new ExpectedOccurrence(
                    family + "-group",
                    -members.size(),
                    List.of(new GroupKey(family, number)),
                    List.copyOf(members))));
  }

  public static class Constraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      var constraints = new ArrayList<Constraint>();
      addFamily(factory, "first", First.class, owner -> owner.first, constraints);
      addFamily(factory, "second", Second.class, owner -> owner.second, constraints);
      addFamily(factory, "inverse", Inverse.class, owner -> owner.inverse, constraints);
      constraints.add(
          factory
              .forEachIncludingUnassigned(First.class)
              .penalize(SimpleScore.ONE)
              .asConstraint("first-including-unassigned"));
      constraints.add(
          factory.forEach(Source.class).penalize(SimpleScore.ONE).asConstraint("common-interface"));
      constraints.add(
          factory.forEach(Item.class).penalize(SimpleScore.ONE).asConstraint("common-base"));
      return constraints.toArray(Constraint[]::new);
    }

    private static <Value_ extends Item> void addFamily(
        ConstraintFactory factory,
        String name,
        Class<Value_> valueClass,
        Function<Owner, List<Value_>> list,
        List<Constraint> constraints) {
      var source = factory.forEach(valueClass);
      constraints.add(source.penalize(SimpleScore.ONE).asConstraint(name + "-plain"));
      constraints.add(
          source
              .ifExists(
                  Owner.class,
                  Joiners.filtering((value, owner) -> list.apply(owner).contains(value)))
              .penalize(SimpleScore.ONE)
              .asConstraint(name + "-exists"));
      constraints.add(
          source
              .groupBy(value -> value.number, ConstraintCollectors.count())
              .penalize(SimpleScore.ONE, (number, count) -> count)
              .indictWith((number, count) -> List.of(new GroupKey(name, number)))
              .asConstraint(name + "-group"));
    }
  }

  private record GroupKey(String family, int number) {}

  public interface Source {}

  @PlanningEntity
  public static class Item implements Source {
    @PlanningVariable(valueRangeProviderRefs = "numbers")
    public Integer number = 1;
  }

  @PlanningEntity
  public static class First extends Item {}

  @PlanningEntity
  public static class Second extends Item {}

  @PlanningEntity
  public static class Inverse extends Item {
    @InverseRelationShadowVariable(sourceVariableName = "inverse")
    public Owner owner;
  }

  @PlanningEntity
  public static class Owner implements Source {
    @PlanningListVariable(valueRangeProviderRefs = "first", allowsUnassignedValues = true)
    public List<First> first = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "second", allowsUnassignedValues = true)
    public List<Second> second = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "inverse", allowsUnassignedValues = true)
    public List<Inverse> inverse = new ArrayList<>();
  }

  @PlanningSolution
  public static class Problem {
    @PlanningEntityCollectionProperty public List<Owner> owners = new ArrayList<>();

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "first")
    public List<First> firstValues = new ArrayList<>();

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "second")
    public List<Second> secondValues = new ArrayList<>();

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "inverse")
    public List<Inverse> inverseValues = new ArrayList<>();

    @PlanningEntityCollectionProperty public List<Item> otherValues = new ArrayList<>();

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "numbers")
    public List<Integer> numbers = List.of(1, 2);

    @PlanningScore public SimpleScore score;

    static Problem create() {
      var problem = new Problem();
      var left = new Owner();
      var right = new Owner();
      problem.owners.addAll(List.of(left, right));
      problem.firstValues.addAll(List.of(new First(), new First(), new First()));
      problem.secondValues.addAll(List.of(new Second(), new Second()));
      problem.inverseValues.addAll(List.of(new Inverse(), new Inverse(), new Inverse()));
      problem.otherValues.add(new Item());
      left.first.addAll(problem.firstValues.subList(0, 2));
      left.second.add(problem.secondValues.getFirst());
      right.inverse.addAll(problem.inverseValues.subList(0, 2));
      return problem;
    }
  }
}
