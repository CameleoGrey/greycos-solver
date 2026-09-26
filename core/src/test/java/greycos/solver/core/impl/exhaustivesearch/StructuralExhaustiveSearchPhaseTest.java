package greycos.solver.core.impl.exhaustivesearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.SequencedCollection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.exhaustivesearch.ExhaustiveSearchPhaseConfig;
import greycos.solver.core.config.exhaustivesearch.ExhaustiveSearchType;
import greycos.solver.core.config.exhaustivesearch.NodeExplorationType;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.exhaustivesearch.scope.ExhaustiveSearchPhaseScope;
import greycos.solver.core.impl.exhaustivesearch.scope.ExhaustiveSearchStepScope;
import greycos.solver.core.impl.heuristic.move.AbstractMove;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.DefaultSolverFactory;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class StructuralExhaustiveSearchPhaseTest {

  static Stream<Arguments> searchModes() {
    return Stream.of(EnvironmentMode.NO_ASSERT, EnvironmentMode.FULL_ASSERT)
        .flatMap(
            mode ->
                Stream.concat(
                    Stream.of(
                        Arguments.of(
                            ExhaustiveSearchType.BRUTE_FORCE,
                            NodeExplorationType.ORIGINAL_ORDER,
                            mode)),
                    Stream.of(NodeExplorationType.values())
                        .map(
                            order ->
                                Arguments.of(ExhaustiveSearchType.BRANCH_AND_BOUND, order, mode))));
  }

  private static ExhaustiveSearchPhaseConfig phase(
      ExhaustiveSearchType type, NodeExplorationType order) {
    return new ExhaustiveSearchPhaseConfig()
        .withExhaustiveSearchType(type)
        .withNodeExplorationType(order);
  }

  @ParameterizedTest
  @MethodSource("searchModes")
  void basicCyclesNeverAbortOrBecomeBest(
      ExhaustiveSearchType type, NodeExplorationType order, EnvironmentMode mode) {
    var config =
        new SolverConfig()
            .withSolutionClass(BasicSolution.class)
            .withEntityClasses(BasicNode.class)
            .withEasyScoreCalculatorClass(BasicScoreCalculator.class)
            .withEnvironmentMode(mode)
            .withPhases(phase(type, order));
    var factory = SolverFactory.<BasicSolution>create(config);
    var solver = factory.buildSolver();
    solver.addEventListener(
        event -> assertThat(event.getNewBestScore().structuralScore()).isZero());
    var result = solver.solve(basicProblem());

    assertThat(result.score).isEqualTo(SimpleScore.of(basicOracle()));
    for (var node : result.nodes) {
      var seen = new HashSet<BasicNode>();
      var current = node;
      int depth = -1;
      while (current != null) {
        assertThat(seen.add(current)).isTrue();
        current = current.previous;
        depth++;
      }
      assertThat(node.depth).isEqualTo(depth);
    }
    assertRecalculated(factory, result, result.score);
  }

  @ParameterizedTest
  @MethodSource("searchModes")
  void listInsertionRepairsPartialCycleWithoutPruningItsOptimum(
      ExhaustiveSearchType type, NodeExplorationType order, EnvironmentMode mode) {
    var config =
        new SolverConfig()
            .withSolutionClass(ListSolution.class)
            .withEntityClasses(ListRoute.class, ListValue.class)
            .withScoreDirectorFactory(
                new ScoreDirectorFactoryConfig()
                    .withEasyScoreCalculatorClass(ListScoreCalculator.class)
                    .withInitializingScoreTrend("ONLY_UP"))
            .withEnvironmentMode(mode)
            .withPhases(
                phase(type, order)
                    .withMoveSelectorConfig(
                        new MoveIteratorFactoryConfig()
                            .withMoveIteratorFactoryClass(OrderedInsertionFactory.class)));
    var factory = SolverFactory.<ListSolution>create(config);
    var solver = (DefaultSolver<ListSolution>) factory.buildSolver();
    var sawFlawedPartial = new AtomicBoolean();
    var firstStep = new AtomicBoolean(true);
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<ListSolution> step) {
            var scope = (ExhaustiveSearchPhaseScope<ListSolution>) step.getPhaseScope();
            if (firstStep.getAndSet(false) && type == ExhaustiveSearchType.BRANCH_AND_BOUND) {
              assertThat(scope.getBestPessimisticBound()).isNull();
            }
            var node = ((ExhaustiveSearchStepScope<ListSolution>) step).getExpandingNode();
            if (node.getScore() != null && node.getScore().isStructurallyFlawed()) {
              sawFlawedPartial.set(true);
              assertThat(node.getOptimisticBound()).isNull();
            }
            var bound = scope.getBestPessimisticBound();
            if (bound != null) {
              assertThat(bound.isStructurallyFlawed()).isFalse();
              assertThat(bound.isFullyAssigned()).isTrue();
              assertThat(bound.raw()).isIn(SimpleScore.of(3), SimpleScore.of(13));
            }
          }
        });
    solver.addEventListener(
        event -> assertThat(event.getNewBestScore().structuralScore()).isZero());
    var result = solver.solve(listProblem());

    assertThat(sawFlawedPartial).isTrue();
    assertThat(result.routes.getFirst().values)
        .extracting(value -> value.id)
        .containsExactly("B", "C", "A");
    assertThat(result.score).isEqualTo(SimpleScore.of(listOracle()));
    var route = result.routes.getFirst();
    for (int index = 0; index < route.values.size(); index++) {
      var value = route.values.get(index);
      assertThat(value.previous).isSameAs(index == 0 ? null : route.values.get(index - 1));
      assertThat(value.foo).isEqualTo(1);
      assertThat(value.bar).isEqualTo(1);
    }
    assertThat(result.values)
        .allSatisfy(
            value -> {
              assertThat(value.foo).isEqualTo(1);
              assertThat(value.bar).isEqualTo(1);
            });
    assertRecalculated(factory, result, result.score);
  }

  @ParameterizedTest
  @MethodSource("searchModes")
  void mixedSearchRepairsBasicTerminalAndRetainsCompetingAssignments(
      ExhaustiveSearchType type, NodeExplorationType order, EnvironmentMode mode) {
    var factory = SolverFactory.<MixedSolution>create(mixedConfig(type, order, mode));
    var solver = factory.buildSolver();
    solver.addEventListener(
        event -> assertThat(event.getNewBestScore().structuralScore()).isZero());
    // The same solver must release the suspended queues and list-stage state after every run.
    for (int run = 0; run < 2; run++) {
      var result = solver.solve(mixedProblem(false));
      assertMixedOptimum(factory, result);
    }
  }

  @ParameterizedTest
  @MethodSource("searchModes")
  void mixedSearchPublishesCompletionWhenListsAreAlreadyAssigned(
      ExhaustiveSearchType type, NodeExplorationType order, EnvironmentMode mode) {
    var factory = SolverFactory.<MixedSolution>create(mixedConfig(type, order, mode));
    assertMixedOptimum(factory, factory.buildSolver().solve(mixedProblem(true)));
  }

  @ParameterizedTest
  @EnumSource(ExhaustiveSearchType.class)
  void mixedSearchCanRestartAfterTerminationDuringListHandoff(ExhaustiveSearchType type) {
    var factory =
        SolverFactory.<MixedSolution>create(
            mixedConfig(type, NodeExplorationType.ORIGINAL_ORDER, EnvironmentMode.FULL_ASSERT));
    var solver = (DefaultSolver<MixedSolution>) factory.buildSolver();
    var terminated = new AtomicBoolean();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<MixedSolution> step) {
            var node = ((ExhaustiveSearchStepScope<MixedSolution>) step).getExpandingNode();
            if (node.getEntity() instanceof MixedRoute && terminated.compareAndSet(false, true)) {
              solver.terminateEarly();
            }
          }
        });
    var partial = solver.solve(mixedProblem(false));
    assertThat(terminated).isTrue();
    assertThat(partial.score.structuralScore()).isZero();
    assertMixedOptimum(factory, solver.solve(mixedProblem(false)));
  }

  @ParameterizedTest
  @EnumSource(ExhaustiveSearchType.class)
  void mixedSearchCanRestartAfterMoveFailure(ExhaustiveSearchType type) {
    var config = mixedConfig(type, NodeExplorationType.ORIGINAL_ORDER, EnvironmentMode.FULL_ASSERT);
    ((ExhaustiveSearchPhaseConfig) config.getPhaseConfigList().getFirst())
        .withMoveSelectorConfig(
            new MoveIteratorFactoryConfig()
                .withMoveIteratorFactoryClass(FailingMixedFactory.class));
    var factory = SolverFactory.<MixedSolution>create(config);
    var solver = factory.buildSolver();
    var failing = mixedProblem(false);
    failing.failInsertion = true;
    assertThatThrownBy(() -> solver.solve(failing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Insertion failed");
    assertMixedOptimum(factory, solver.solve(mixedProblem(false)));
  }

  private static SolverConfig mixedConfig(
      ExhaustiveSearchType type, NodeExplorationType order, EnvironmentMode mode) {
    return new SolverConfig()
        .withSolutionClass(MixedSolution.class)
        .withEntityClasses(MixedRoute.class, MixedValue.class)
        .withScoreDirectorFactory(
            new ScoreDirectorFactoryConfig()
                .withEasyScoreCalculatorClass(MixedScoreCalculator.class)
                .withInitializingScoreTrend("ONLY_UP"))
        .withEnvironmentMode(mode)
        .withPhases(
            phase(type, order)
                .withMoveSelectorConfig(
                    new MoveIteratorFactoryConfig()
                        .withMoveIteratorFactoryClass(MixedInsertionFactory.class)));
  }

  private static void assertMixedOptimum(
      SolverFactory<MixedSolution> factory, MixedSolution result) {
    assertThat(result.values.get(1).dependency).isSameAs(result.values.getFirst());
    assertThat(result.routes.getFirst().values)
        .extracting(value -> value.id)
        .containsExactly("B", "C", "A");
    assertThat(result.score).isEqualTo(SimpleScore.of(mixedOracle()));
    var route = result.routes.getFirst();
    for (var value : result.values) {
      int index = route.values.indexOf(value);
      assertThat(value.previous).isSameAs(index <= 0 ? null : route.values.get(index - 1));
      assertThat(value.dependency).isNotNull().isIn(value.dependencies);
      assertThat(value.foo).isEqualTo(1);
      assertThat(value.bar).isEqualTo(1);
    }
    assertRecalculated(factory, result, result.score);
  }

  private static <Solution_> void assertRecalculated(
      SolverFactory<Solution_> factory, Solution_ solution, SimpleScore expected) {
    try (var director =
        ((DefaultSolverFactory<Solution_>) factory)
            .<SimpleScore>getScoreDirectorFactory()
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      assertThat(director.calculateScore().raw()).isEqualTo(expected);
      assertThat(director.computeVariableLoops()).isEmpty();
    }
  }

  private static int basicOracle() {
    int best = 0;
    for (int a = -1; a < 3; a++) {
      for (int b = -1; b < 3; b++) {
        int[] parents = {-1, a, b};
        boolean cyclic = false;
        for (int node = 0; node < 3; node++) {
          var visited = new HashSet<Integer>();
          for (int current = node; current >= 0; current = parents[current]) {
            if (!visited.add(current)) {
              cyclic = true;
              break;
            }
          }
        }
        if (!cyclic) best = Math.max(best, (a < 0 ? 0 : 1) + (b < 0 ? 0 : 1));
      }
    }
    return best;
  }

  private static int listOracle() {
    int best = Integer.MIN_VALUE;
    for (String order : List.of("ABC", "ACB", "BAC", "BCA", "CAB", "CBA")) {
      // B.bar depends on A.foo. Its sole possible cycle is A immediately following B.
      if (!order.contains("BA")) best = Math.max(best, order.equals("BCA") ? 13 : 3);
    }
    return best;
  }

  private static int mixedOracle() {
    int best = Integer.MIN_VALUE;
    for (boolean dependsOnA : List.of(false, true)) {
      for (String order : List.of("CBA", "BCA", "BAC")) {
        if (dependsOnA && order.contains("BA")) continue;
        best = Math.max(best, dependsOnA && order.equals("BCA") ? 13 : 3);
      }
    }
    return best;
  }

  private static BasicSolution basicProblem() {
    var root = new BasicNode();
    root.pinned = true;
    var solution = new BasicSolution();
    solution.nodes = new ArrayList<>(List.of(root, new BasicNode(), new BasicNode()));
    return solution;
  }

  private static ListSolution listProblem() {
    var a = new ListValue("A");
    var b = new ListValue("B");
    var c = new ListValue("C");
    var d = new ListValue("D");
    a.dependency = d;
    c.dependency = d;
    d.dependency = d;
    b.dependency = a;
    var solution = new ListSolution();
    solution.routes = List.of(new ListRoute());
    solution.values = List.of(b, a, c, d);
    solution.listRange = List.of(b, a, c);
    return solution;
  }

  private static MixedSolution mixedProblem(boolean listAssigned) {
    var a = new MixedValue("A");
    var b = new MixedValue("B");
    var c = new MixedValue("C");
    var d = new MixedValue("D");
    a.dependency = d;
    c.dependency = d;
    d.dependency = d;
    d.pinned = true;
    a.dependencies = c.dependencies = d.dependencies = List.of(d);
    // Explore a sound completion before the higher-scoring repairable branch.
    b.dependencies = List.of(d, a);
    var route = new MixedRoute();
    route.values.addAll(listAssigned ? List.of(b, c, a) : List.of(b, a));
    var solution = new MixedSolution();
    solution.routes = List.of(route);
    solution.values = List.of(a, b, c, d);
    solution.listRange = List.of(a, b, c);
    return solution;
  }

  @PlanningSolution
  public static class BasicSolution {
    @PlanningEntityCollectionProperty @ValueRangeProvider public List<BasicNode> nodes;
    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class BasicNode {
    @PlanningPin public boolean pinned;

    @PlanningVariable(allowsUnassigned = true)
    public BasicNode previous;

    @ShadowVariable(supplierName = "depthSupplier")
    public Integer depth;

    @ShadowSources("previous.depth")
    public Integer depthSupplier() {
      return previous == null ? 0 : previous.depth == null ? null : previous.depth + 1;
    }
  }

  public static class BasicScoreCalculator
      implements EasyScoreCalculator<BasicSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(BasicSolution solution) {
      return SimpleScore.of(solution.nodes.stream().filter(node -> node.previous != null).count());
    }
  }

  @PlanningSolution
  public static class ListSolution {
    @PlanningEntityCollectionProperty public List<ListRoute> routes;
    @PlanningEntityCollectionProperty public List<ListValue> values;

    @ValueRangeProvider(id = "values")
    public List<ListValue> listRange;

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class ListRoute {
    @PlanningListVariable(valueRangeProviderRefs = "values")
    public List<ListValue> values = new ArrayList<>();
  }

  @PlanningEntity
  public static class ListValue {
    public String id;
    public ListValue dependency;

    @PreviousElementShadowVariable(sourceVariableName = "values")
    public ListValue previous;

    @ShadowVariable(supplierName = "fooSupplier")
    public Integer foo;

    @ShadowVariable(supplierName = "barSupplier")
    public Integer bar;

    public ListValue() {}

    public ListValue(String id) {
      this.id = id;
    }

    @ShadowSources("previous.bar")
    public Integer fooSupplier() {
      return previous == null ? Integer.valueOf(1) : previous.bar;
    }

    @ShadowSources("dependency.foo")
    public Integer barSupplier() {
      return dependency == null ? Integer.valueOf(1) : dependency.foo;
    }
  }

  public static class ListScoreCalculator
      implements EasyScoreCalculator<ListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(ListSolution solution) {
      var values = solution.routes.getFirst().values;
      var order = values.stream().map(value -> value.id).reduce("", String::concat);
      return SimpleScore.of(order.equals("BCA") ? 13 : values.size());
    }
  }

  public static class OrderedInsertionFactory extends ExhaustiveMoveFactory<ListSolution> {
    @Override
    public List<? extends Move<ListSolution>> createMoveList(ListSolution solution) {
      var route = solution.routes.getFirst();
      for (var value : solution.listRange) {
        if (!route.values.contains(value)) {
          var moves = new ArrayList<Move<ListSolution>>();
          for (int index = 0; index <= route.values.size(); index++) {
            moves.add(new InsertionMove<>(route, route.values, value, index));
          }
          return moves;
        }
      }
      return List.of();
    }
  }

  @PlanningSolution
  public static class MixedSolution {
    @PlanningEntityCollectionProperty public List<MixedRoute> routes;
    @PlanningEntityCollectionProperty public List<MixedValue> values;

    @ValueRangeProvider(id = "values")
    public List<MixedValue> listRange;

    @PlanningScore public SimpleScore score;
    public boolean failInsertion;
  }

  @PlanningEntity
  public static class MixedRoute {
    @PlanningListVariable(valueRangeProviderRefs = "values")
    public List<MixedValue> values = new ArrayList<>();
  }

  @PlanningEntity
  public static class MixedValue {
    public String id;

    @PlanningVariable(valueRangeProviderRefs = "dependencies")
    public MixedValue dependency;

    @ValueRangeProvider(id = "dependencies")
    public List<MixedValue> dependencies;

    @PlanningPin public boolean pinned;

    @PreviousElementShadowVariable(sourceVariableName = "values")
    public MixedValue previous;

    @ShadowVariable(supplierName = "fooSupplier")
    public Integer foo;

    @ShadowVariable(supplierName = "barSupplier")
    public Integer bar;

    public MixedValue() {}

    public MixedValue(String id) {
      this.id = id;
    }

    @ShadowSources("previous.bar")
    public Integer fooSupplier() {
      return previous == null ? Integer.valueOf(1) : previous.bar;
    }

    @ShadowSources("dependency.foo")
    public Integer barSupplier() {
      return dependency == null ? Integer.valueOf(1) : dependency.foo;
    }
  }

  public static class MixedScoreCalculator
      implements EasyScoreCalculator<MixedSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(MixedSolution solution) {
      var values = solution.routes.getFirst().values;
      var order = values.stream().map(value -> value.id).reduce("", String::concat);
      return SimpleScore.of(
          order.equals("BCA") && solution.values.get(1).dependency == solution.values.getFirst()
              ? 13
              : values.size());
    }
  }

  public static class MixedInsertionFactory extends ExhaustiveMoveFactory<MixedSolution> {
    @Override
    public List<? extends Move<MixedSolution>> createMoveList(MixedSolution solution) {
      for (var value : solution.values) {
        if (value.dependency == null) {
          return value.dependencies.stream()
              .map(dependency -> new DependencyMove(value, dependency))
              .toList();
        }
      }
      var route = solution.routes.getFirst();
      for (var value : solution.listRange) {
        if (!route.values.contains(value)) {
          var moves = new ArrayList<Move<MixedSolution>>();
          for (int index = 0; index <= route.values.size(); index++) {
            moves.add(new InsertionMove<>(route, route.values, value, index));
          }
          return moves;
        }
      }
      return List.of();
    }
  }

  public static class FailingMixedFactory extends MixedInsertionFactory {
    @Override
    public List<? extends Move<MixedSolution>> createMoveList(MixedSolution solution) {
      if (solution.failInsertion
          && solution.values.stream().allMatch(value -> value.dependency != null)) {
        return List.of(
            solutionView -> {
              throw new IllegalStateException("Insertion failed");
            });
      }
      return super.createMoveList(solution);
    }
  }

  public abstract static class ExhaustiveMoveFactory<Solution_>
      implements MoveIteratorFactory<Solution_, Move<Solution_>> {

    public abstract List<? extends Move<Solution_>> createMoveList(Solution_ solution);

    @Override
    public long getSize(ScoreDirector<Solution_> director) {
      return createMoveList(director.getWorkingSolution()).size();
    }

    @Override
    public Iterator<Move<Solution_>> createOriginalMoveIterator(ScoreDirector<Solution_> director) {
      return new ArrayList<Move<Solution_>>(createMoveList(director.getWorkingSolution()))
          .iterator();
    }

    @Override
    public Iterator<Move<Solution_>> createRandomMoveIterator(
        ScoreDirector<Solution_> director, RandomGenerator random) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class DependencyMove extends AbstractMove<MixedSolution> {
    private final MixedValue value;
    private final MixedValue dependency;

    DependencyMove(MixedValue value, MixedValue dependency) {
      this.value = value;
      this.dependency = dependency;
    }

    @Override
    public boolean isMoveDoable(ScoreDirector<MixedSolution> director) {
      return true;
    }

    @Override
    protected void doMoveOnGenuineVariables(ScoreDirector<MixedSolution> director) {
      director.beforeVariableChanged(value, "dependency");
      value.dependency = dependency;
      director.afterVariableChanged(value, "dependency");
    }

    @Override
    public SequencedCollection<Object> getPlanningEntities() {
      return List.of(value);
    }
  }

  private static final class InsertionMove<Solution_> extends AbstractMove<Solution_> {
    private final Object route;
    private final List<Object> values;
    private final Object value;
    private final int index;

    @SuppressWarnings("unchecked")
    InsertionMove(Object route, List<?> values, Object value, int index) {
      this.route = route;
      this.values = (List<Object>) values;
      this.value = value;
      this.index = index;
    }

    @Override
    public boolean isMoveDoable(ScoreDirector<Solution_> director) {
      return true;
    }

    @Override
    protected void doMoveOnGenuineVariables(ScoreDirector<Solution_> director) {
      director.beforeListVariableElementAssigned(route, "values", value);
      director.beforeListVariableChanged(route, "values", index, index);
      values.add(index, value);
      director.afterListVariableChanged(route, "values", index, index + 1);
      director.afterListVariableElementAssigned(route, "values", value);
    }

    @Override
    public SequencedCollection<Object> getPlanningEntities() {
      return List.of(route);
    }
  }
}
