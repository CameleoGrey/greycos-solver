package greycos.solver.core.impl.multistage.integration;

import static greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.CUSTOMERS;
import static greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.QUANTITY;
import static greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.assignments;
import static greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.independentScore;
import static greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.verify;
import static greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.verifyAssignments;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.solver.multistage.BasicVariableCustomStage;
import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
import greycos.solver.core.api.solver.multistage.CrossVariableCustomStage;
import greycos.solver.core.api.solver.multistage.CrossVariableStageProvider;
import greycos.solver.core.api.solver.multistage.ListVariableCustomStage;
import greycos.solver.core.api.solver.multistage.ListVariableStageProvider;
import greycos.solver.core.api.solver.multistage.MultistageOperation;
import greycos.solver.core.api.solver.multistage.MultistageStageResult;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.multistage.PreparedMultistageMove;
import greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.Customer;
import greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.Plan;
import greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.Vehicle;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class CrossVariableVehicleIntegrationTest {
  private static final Map<String, Observations> OBSERVATIONS = new ConcurrentHashMap<>();

  static Stream<Arguments> providersAndWorkers() {
    return Stream.of("NONE", "1", "2", "4")
        .flatMap(
            workers ->
                Stream.of(
                    Arguments.of(workers, AuditedOrderedStages.class, 9, 9),
                    Arguments.of(workers, AuditedCompoundStages.class, 3, 3)));
  }

  @ParameterizedTest
  @MethodSource("providersAndWorkers")
  void fixedWorkHasIdenticalScoresAssignmentsAndShadowsAndCallbacksNeverReplay(
      String workers,
      Class<? extends CrossVariableStageProvider> provider,
      int callbacks,
      int probes) {
    var problem = CrossVariableVehicleTestSupport.problem();
    var initial = assignments(problem);
    var observations = new Observations();
    OBSERVATIONS.put(problem.id, observations);
    try {
      var solver =
          MultistageIntegrationSupport.<Plan>solver(
              CrossVariableVehicleTestSupport.config(provider, workers));
      var trace = record(solver);
      var result = solver.solve(problem);
      assertTrace(trace);
      verify(result);
      assertThat(result.score).isEqualTo(HardSoftScore.of(0, -3400));
      assertThat(assignments(result))
          .containsExactly(
              "source:200:[source-resident]", "target:1400:[target-resident, a, b, c]");
      assertThat(observations.callbacks).hasValue(callbacks);
      assertThat(observations.probes).hasValue(probes);
      assertThat(observations.initialized.get()).isPositive().isEqualTo(observations.ended.get());
      assertThat(assignments(problem)).isEqualTo(initial);
    } finally {
      OBSERVATIONS.remove(problem.id);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = LocalSearchType.class,
      names = {"TABU_SEARCH", "GUIDED_LOCAL_SEARCH"})
  void normalAcceptorsReceiveCompleteCrossVariableMoves(LocalSearchType type) {
    var problem = CrossVariableVehicleTestSupport.problem();
    var observations = new Observations();
    OBSERVATIONS.put(problem.id, observations);
    try {
      var config =
          CrossVariableVehicleTestSupport.config(AuditedCompoundStages.class, "2")
              .withPhases(
                  phase(CrossVariableVehicleTestSupport.selector(AuditedCompoundStages.class))
                      .withLocalSearchType(type));
      var solver = MultistageIntegrationSupport.<Plan>solver(config);
      var trace = record(solver);
      var result = solver.solve(problem);
      assertTrace(trace);
      verify(result);
      assertThat(observations.callbacks).hasValue(3);
    } finally {
      OBSERVATIONS.remove(problem.id);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void islandsUseIndependentProvidersAndReleaseTheirCachesOnSolverReuse(String workers) {
    var config =
        CrossVariableVehicleTestSupport.config(AuditedCompoundStages.class, "NONE")
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withMoveThreadCount(workers)
                    .withCompareGlobalEnabled(false)
                    .withMigrationFrequency(Integer.MAX_VALUE)
                    .withPhaseConfigList(
                        List.of(
                            phase(
                                CrossVariableVehicleTestSupport.selector(
                                    AuditedCompoundStages.class)))));
    var solver = MultistageIntegrationSupport.<Plan>solver(config);
    for (int run = 0; run < 2; run++) {
      var problem = CrossVariableVehicleTestSupport.problem();
      var observations = new Observations();
      OBSERVATIONS.put(problem.id, observations);
      try {
        var result = solver.solve(problem);
        verify(result);
        assertThat(result.score).isEqualTo(HardSoftScore.of(0, -3400));
        assertThat(observations.callbacks).hasValue(6);
        assertThat(observations.initialized.get()).isPositive().isEqualTo(observations.ended.get());
      } finally {
        OBSERVATIONS.remove(problem.id);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2", "4"})
  void unionFilterSeesAllChangedVariablesOnCoordinatorObjects(String workers) {
    var problem = CrossVariableVehicleTestSupport.problem();
    var observations = new Observations();
    OBSERVATIONS.put(problem.id, observations);
    try {
      var union =
          new UnionMoveSelectorConfig()
              .withSelectionOrder(SelectionOrder.ORIGINAL)
              .withMoveSelectors(
                  CrossVariableVehicleTestSupport.selector(AuditedCompoundStages.class))
              .withFilterClass(PreparedFilter.class);
      var solver =
          MultistageIntegrationSupport.<Plan>solver(
              CrossVariableVehicleTestSupport.config(AuditedCompoundStages.class, workers)
                  .withPhases(phase(union)));
      var trace = record(solver);
      var result = solver.solve(problem);
      assertTrace(trace);
      verify(result);
      assertThat(observations.filters).hasValue(3);
      assertThat(observations.callbacks).hasValue(3);
    } finally {
      OBSERVATIONS.remove(problem.id);
    }
  }

  @Test
  void oneCoordinatedCandidatePreservesFeasibilityWhereSeparateChangesCannot() {
    // Fixed input and finite selectors: these assertions measure useful work, never wall-clock
    // speed.
    var independent =
        new UnionMoveSelectorConfig()
            .withSelectionOrder(SelectionOrder.ORIGINAL)
            .withMoveSelectors(
                new ChangeMoveSelectorConfig()
                    .withValueSelectorConfig(new ValueSelectorConfig("quantity")),
                new ListChangeMoveSelectorConfig()
                    .withValueSelectorConfig(new ValueSelectorConfig("customers")));
    var oldSingleVariable =
        new UnionMoveSelectorConfig()
            .withSelectionOrder(SelectionOrder.ORIGINAL)
            .withMoveSelectors(
                new MultistageMoveSelectorConfig()
                    .withEntityClass(Vehicle.class)
                    .withVariableName("quantity")
                    .withStageProviderClass(QuantityOnlyStages.class)
                    .withSelectionOrder(SelectionOrder.ORIGINAL),
                new ListMultistageMoveSelectorConfig()
                    .withEntityClass(Vehicle.class)
                    .withVariableName("customers")
                    .withStageProviderClass(RouteOnlyStages.class)
                    .withSelectionOrder(SelectionOrder.ORIGINAL));
    for (var selector : List.of(independent, oldSingleVariable)) {
      var problem = CrossVariableVehicleTestSupport.problem();
      var solver =
          MultistageIntegrationSupport.<Plan>solver(
              CrossVariableVehicleTestSupport.config(
                      CrossVariableVehicleTestSupport.OrderedStages.class, "NONE")
                  .withPhases(
                      phase(selector)
                          .withTerminationConfig(new TerminationConfig().withStepCountLimit(1))));
      var trace = record(solver);
      var result = solver.solve(problem);
      verify(result);
      assertThat(result.score).isEqualTo(HardSoftScore.of(0, -14200));
      // A finite hill-climbing selector may fall back to its best rejected move. Its best
      // solution must still be the initial feasible state after this single step.
      assertThat(trace).hasSizeLessThanOrEqualTo(1);
    }
    var problem = CrossVariableVehicleTestSupport.problem();
    var observations = new Observations();
    OBSERVATIONS.put(problem.id, observations);
    try {
      var solver =
          MultistageIntegrationSupport.<Plan>solver(
              CrossVariableVehicleTestSupport.config(ComparisonStages.class, "NONE")
                  .withPhases(
                      phase(CrossVariableVehicleTestSupport.selector(ComparisonStages.class))
                          .withTerminationConfig(new TerminationConfig().withStepCountLimit(1))));
      var trace = record(solver);
      var result = solver.solve(problem);
      verify(result);
      assertThat(trace)
          .containsExactly(
              new Step(
                  List.of("source:1100:[b, c, source-resident]", "target:500:[target-resident, a]"),
                  HardSoftScore.of(0, -11500)));
      assertThat(result.score).isEqualTo(HardSoftScore.of(0, -11500));
      assertThat(observations.callbacks).hasValue(1);
      assertThat(observations.probes).hasValue(5);
    } finally {
      OBSERVATIONS.remove(problem.id);
    }
  }

  private static LocalSearchPhaseConfig phase(MoveSelectorConfig<?> selector) {
    return new LocalSearchPhaseConfig()
        .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
        .withMoveSelectorConfig(selector)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(3));
  }

  private static List<Step> record(DefaultSolver<Plan> solver) {
    var trace = new ArrayList<Step>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<Plan> scope) {
            var solution = scope.getWorkingSolution();
            verifyAssignments(solution);
            assertThat(scope.getScore().raw()).isEqualTo(independentScore(solution));
            trace.add(new Step(assignments(solution), (HardSoftScore) scope.getScore().raw()));
          }
        });
    return trace;
  }

  private static void assertTrace(List<Step> trace) {
    assertThat(trace)
        .containsExactly(
            new Step(
                List.of("source:1100:[b, c, source-resident]", "target:500:[target-resident, a]"),
                HardSoftScore.of(0, -11500)),
            new Step(
                List.of("source:700:[c, source-resident]", "target:900:[target-resident, a, b]"),
                HardSoftScore.of(0, -7900)),
            new Step(
                List.of("source:200:[source-resident]", "target:1400:[target-resident, a, b, c]"),
                HardSoftScore.of(0, -3400)));
  }

  private record Step(List<String> assignments, HardSoftScore score) {}

  private static final class Observations {
    final Thread coordinator = Thread.currentThread();
    final AtomicInteger initialized = new AtomicInteger();
    final AtomicInteger ended = new AtomicInteger();
    final AtomicInteger callbacks = new AtomicInteger();
    final AtomicInteger probes = new AtomicInteger();
    final AtomicInteger filters = new AtomicInteger();
  }

  public static class AuditedOrderedStages extends CrossVariableVehicleTestSupport.OrderedStages {
    protected Observations observations;

    @Override
    public void initialize(Plan solution) {
      super.initialize(solution);
      observations = OBSERVATIONS.get(solution.id);
      observations.initialized.incrementAndGet();
    }

    @Override
    public void phaseEnded() {
      observations.ended.incrementAndGet();
      super.phaseEnded();
    }

    @Override
    public List<CrossVariableCustomStage<Plan, HardSoftScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      var stages = super.createStages(candidateIndex, random);
      var demand = solution.vehicles.getFirst().customers.getFirst().demand;
      var result = new ArrayList<CrossVariableCustomStage<Plan, HardSoftScore>>();
      for (int i = 0; i < stages.size(); i++) {
        int index = i;
        var invoked = new AtomicBoolean();
        result.add(
            evaluator -> {
              assertThat(invoked.compareAndSet(false, true))
                  .as("Stage callbacks cannot execute during replay")
                  .isTrue();
              observations.callbacks.incrementAndGet();
              var baseline = assignments(solution);
              var score = independentScore(solution);
              var selection = stages.get(index).selectMove(evaluator);
              var evaluated = evaluator.evaluate(selection.operation());
              observations.probes.incrementAndGet();
              long expectedHard = index == 2 ? 0 : -(index + 1L) * demand;
              long expectedSoft =
                  score.softScore() + (index == 0 ? 10L * demand : index == 1 ? -demand : 0);
              assertThat(evaluated.score()).isEqualTo(HardSoftScore.of(expectedHard, expectedSoft));
              assertThat(assignments(solution)).isEqualTo(baseline);
              verifyAssignments(solution);
              return selection;
            });
      }
      return result;
    }
  }

  public static class AuditedCompoundStages extends AuditedOrderedStages {
    @Override
    public List<CrossVariableCustomStage<Plan, HardSoftScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      var invoked = new AtomicBoolean();
      return List.of(
          evaluator -> {
            assertThat(invoked.compareAndSet(false, true))
                .as("A completed candidate must replay without another callback")
                .isTrue();
            observations.callbacks.incrementAndGet();
            var source = solution.vehicles.getFirst();
            var target = solution.vehicles.getLast();
            var customer = source.customers.getFirst();
            var baseline = assignments(solution);
            var score = independentScore(solution);
            var quantity = evaluator.basic(QUANTITY);
            var routes = evaluator.list(CUSTOMERS);
            List<MultistageOperation<Plan>> changes =
                List.of(
                    quantity.assign(source, source.quantity - customer.demand),
                    quantity.assign(target, target.quantity + customer.demand),
                    routes.place(customer, target, target.customers.size()));
            // Composing on a typed child view also accepts operations made by its sibling view.
            var operation = quantity.sequence(changes);
            var evaluated = routes.evaluate(operation);
            observations.probes.incrementAndGet();
            assertThat(evaluated.score())
                .isEqualTo(HardSoftScore.of(0, score.softScore() + 9L * customer.demand));
            assertThat(evaluated.isComplete()).isTrue();
            assertThat(assignments(solution)).isEqualTo(baseline);
            assertThat(independentScore(solution)).isEqualTo(score);
            verifyAssignments(solution);
            return MultistageStageResult.apply(operation);
          });
    }
  }

  public static class ComparisonStages extends AuditedOrderedStages {
    @Override
    public List<CrossVariableCustomStage<Plan, HardSoftScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      var invoked = new AtomicBoolean();
      return List.of(
          evaluator -> {
            assertThat(invoked.compareAndSet(false, true)).isTrue();
            observations.callbacks.incrementAndGet();
            var source = solution.vehicles.getFirst();
            var target = solution.vehicles.getLast();
            var customer = source.customers.getFirst();
            var quantities = evaluator.basic(QUANTITY);
            var sourceChange = quantities.assign(source, source.quantity - customer.demand);
            var targetChange = quantities.assign(target, target.quantity + customer.demand);
            var routeChange =
                evaluator.list(CUSTOMERS).place(customer, target, target.customers.size());
            var baseline = assignments(solution);
            var alternatives =
                List.of(
                    sourceChange,
                    targetChange,
                    evaluator.sequence(List.of(sourceChange, targetChange)),
                    routeChange);
            for (var alternative : alternatives) {
              var score = evaluator.evaluate(alternative).score();
              observations.probes.incrementAndGet();
              assertThat(score.hardScore()).isNegative();
              assertThat(assignments(solution)).isEqualTo(baseline);
              verifyAssignments(solution);
            }
            var complete = evaluator.sequence(List.of(sourceChange, targetChange, routeChange));
            assertThat(evaluator.evaluate(complete).score()).isEqualTo(HardSoftScore.of(0, -11500));
            observations.probes.incrementAndGet();
            assertThat(assignments(solution)).isEqualTo(baseline);
            verifyAssignments(solution);
            return MultistageStageResult.apply(complete);
          });
    }
  }

  public static class PreparedFilter implements SelectionFilter<Plan, Move<Plan>> {
    @Override
    public boolean accept(ScoreDirector<Plan> scoreDirector, Move<Plan> move) {
      var solution = scoreDirector.getWorkingSolution();
      var observations = OBSERVATIONS.get(solution.id);
      observations.filters.incrementAndGet();
      assertThat(Thread.currentThread()).isSameAs(observations.coordinator);
      assertThat(move).isInstanceOf(PreparedMultistageMove.class);
      assertThat(move.getPlanningEntities()).containsExactlyInAnyOrderElementsOf(solution.vehicles);
      var values = move.getPlanningValues();
      assertThat(values.stream().filter(Integer.class::isInstance).count()).isEqualTo(2);
      assertThat(values).contains(solution.vehicles.getFirst().customers.getFirst());
      assertThat(values)
          .allSatisfy(
              value -> {
                if (value instanceof Customer customer)
                  assertThat(solution.customers).contains(customer);
                else assertThat(solution.quantities).contains((Integer) value);
              });
      assertThat(independentScore(solution).hardScore()).isZero();
      verifyAssignments(solution);
      return true;
    }
  }

  public static class QuantityOnlyStages
      implements BasicVariableStageProvider<Plan, Vehicle, Integer, HardSoftScore> {
    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<BasicVariableCustomStage<Plan, Vehicle, Integer, HardSoftScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      return List.of(
          evaluator -> {
            var source = evaluator.workingSolution().vehicles.getFirst();
            var target = evaluator.workingSolution().vehicles.getLast();
            int demand = source.customers.getFirst().demand;
            return MultistageStageResult.apply(
                evaluator.sequence(
                    List.of(
                        evaluator.assign(source, source.quantity - demand),
                        evaluator.assign(target, target.quantity + demand))));
          });
    }
  }

  public static class RouteOnlyStages
      implements ListVariableStageProvider<Plan, Vehicle, Customer, HardSoftScore> {
    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<ListVariableCustomStage<Plan, Vehicle, Customer, HardSoftScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      return List.of(
          evaluator -> {
            var source = evaluator.workingSolution().vehicles.getFirst();
            var target = evaluator.workingSolution().vehicles.getLast();
            return MultistageStageResult.apply(
                evaluator.place(source.customers.getFirst(), target, target.customers.size()));
          });
    }
  }
}
