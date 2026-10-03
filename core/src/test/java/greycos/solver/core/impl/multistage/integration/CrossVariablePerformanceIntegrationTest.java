package greycos.solver.core.impl.multistage.integration;

import static greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.CUSTOMERS;
import static greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.QUANTITY;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.multistage.BasicVariableCustomStage;
import greycos.solver.core.api.solver.multistage.BasicVariableReference;
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
import greycos.solver.core.config.heuristic.selector.move.generic.CrossVariableMultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.multistage.PreparedMultistageMove;
import greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.Customer;
import greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.Plan;
import greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.Vehicle;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Opt-in, bounded solver measurements; no assertion depends on elapsed time or allocated bytes.
 * Each measurement includes solve, step snapshots and independent score calculation; solver
 * construction, input creation, assertions and CSV output are excluded. Allocation covers the
 * current solver thread; every run uses NONE move workers. These are small synthetic workloads, not
 * evidence of whole-application speedups.
 */
@EnabledIfSystemProperty(named = "greycos.crossVariableBenchmark", matches = "true")
@Timeout(120)
class CrossVariablePerformanceIntegrationTest {
  private static final int ENTITIES = 32;
  private static final int STEPS = 200;
  private static final BasicVariableReference<TestdataEntity, TestdataValue> VALUE =
      BasicVariableReference.of(TestdataEntity.class, "value", TestdataValue.class);
  private static final ThreadLocal<Work> WORK = new ThreadLocal<>();

  @Test
  void exportFixedWorkRuntimeAllocationAndEffectivenessEvidence() throws Exception {
    int warmups = Integer.getInteger("greycos.crossVariableBenchmark.warmups", 3);
    int repetitions = Integer.getInteger("greycos.crossVariableBenchmark.repetitions", 7);
    assertThat(warmups).isBetween(0, 100);
    assertThat(repetitions).isBetween(1, 100);
    var rows = new ArrayList<Measurement>();
    var scenarios = Scenario.values();
    try (var allocation = new AllocationCounter()) {
      for (int iteration = -warmups; iteration < repetitions; iteration++) {
        Run oldFrozen = null;
        Run crossFrozen = null;
        // Rotate the first measured scenario to avoid giving one implementation all cold starts.
        for (int offset = 0; offset < scenarios.length; offset++) {
          var scenario = scenarios[Math.floorMod(iteration + offset, scenarios.length)];
          var run = run(scenario, iteration, allocation);
          if (scenario == Scenario.FROZEN_OLD) oldFrozen = run;
          if (scenario == Scenario.FROZEN_CROSS) crossFrozen = run;
          if (iteration >= 0) rows.add(run.measurement);
        }
        assertThat(crossFrozen.trace).isEqualTo(oldFrozen.trace);
        assertThat(crossFrozen.measurement.candidates)
            .isEqualTo(oldFrozen.measurement.candidates)
            .isEqualTo(STEPS);
        assertThat(crossFrozen.measurement.probes)
            .isEqualTo(oldFrozen.measurement.probes)
            .isEqualTo(STEPS);
        assertThat(crossFrozen.measurement.preparedStepReplays)
            .isEqualTo(oldFrozen.measurement.preparedStepReplays)
            .isEqualTo(STEPS);
      }
    } finally {
      WORK.remove();
    }
    var output =
        Path.of(
                System.getProperty(
                    "greycos.crossVariableBenchmark.output", "target/cross-variable-benchmark.csv"))
            .toAbsolutePath();
    Files.createDirectories(output.getParent());
    var csv =
        new StringBuilder(
            "scenario,iteration,elapsed_ns,current_thread_allocated_bytes,score_calculations,move_evaluations,provider_candidates,stage_callbacks,scoring_probes,prepared_step_replays,steps,score,trace_sha256,java_version,processors\n");
    for (var row : rows) csv.append(row.csv()).append('\n');
    Files.writeString(output, csv);
    System.out.println(
        "Cross-variable bounded measurements: " + output + " (" + rows.size() + " rows)");
  }

  private static Run run(Scenario scenario, int iteration, AllocationCounter allocation)
      throws Exception {
    if (scenario == Scenario.FROZEN_OLD || scenario == Scenario.FROZEN_CROSS) {
      var solution = new TestdataSolution("frozen");
      solution.setValueList(
          IntStream.rangeClosed(0, STEPS)
              .mapToObj(i -> new TestdataValue(Integer.toString(i)))
              .toList());
      solution.setEntityList(
          IntStream.range(0, ENTITIES)
              .mapToObj(i -> new TestdataEntity("entity-" + i, solution.getValueList().getFirst()))
              .toList());
      MoveSelectorConfig<?> selector =
          scenario == Scenario.FROZEN_OLD
              ? new MultistageMoveSelectorConfig()
                  .withEntityClass(TestdataEntity.class)
                  .withVariableName("value")
                  .withStageProviderClass(FrozenOldStages.class)
                  .withSelectionOrder(SelectionOrder.ORIGINAL)
                  .withCandidateCountLimit(1)
              : new CrossVariableMultistageMoveSelectorConfig()
                  .withVariables(VALUE)
                  .withStageProviderClass(FrozenCrossStages.class)
                  .withSelectionOrder(SelectionOrder.ORIGINAL)
                  .withCandidateCountLimit(1);
      var config =
          MultistageIntegrationSupport.basicConfig("NONE")
              .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
              .withPhases(phase(selector, STEPS));
      var run =
          measure(
              scenario,
              iteration,
              config,
              solution,
              MultistageIntegrationSupport::assignments,
              value ->
                  SimpleScore.of(
                      value.getEntityList().stream()
                          .mapToLong(entity -> Long.parseLong(entity.getValue().getCode()))
                          .sum()),
              allocation);
      assertThat(run.trace).hasSize(STEPS);
      for (int index = 0; index < STEPS; index++) {
        assertThat(run.trace.get(index).assignments)
            .containsOnly(Integer.toString(index + 1))
            .hasSize(ENTITIES);
        assertThat(run.trace.get(index).score)
            .isEqualTo(SimpleScore.of((long) ENTITIES * (index + 1)).toString());
      }
      assertThat(run.measurement.score)
          .isEqualTo(SimpleScore.of((long) ENTITIES * STEPS).toString());
      return run;
    }
    var config =
        CrossVariableVehicleTestSupport.config(CoupledCrossStages.class, "NONE")
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withPhases(phase(coupledSelector(scenario), 3));
    var problem = CrossVariableVehicleTestSupport.problem();
    var run =
        measure(
            scenario,
            iteration,
            config,
            problem,
            CrossVariableVehicleTestSupport::assignments,
            CrossVariableVehicleTestSupport::independentScore,
            allocation);
    if (scenario == Scenario.COUPLED_CROSS) {
      assertThat(run.measurement.score).isEqualTo(HardSoftScore.of(0, -3400).toString());
      assertThat(run.measurement.candidates).isEqualTo(3);
      assertThat(run.measurement.probes).isEqualTo(3);
      assertThat(run.measurement.preparedStepReplays).isEqualTo(3);
    }
    return run;
  }

  private static MoveSelectorConfig<?> coupledSelector(Scenario scenario) {
    return switch (scenario) {
      case COUPLED_INDEPENDENT ->
          new UnionMoveSelectorConfig()
              .withSelectionOrder(SelectionOrder.ORIGINAL)
              .withMoveSelectors(
                  new ChangeMoveSelectorConfig()
                      .withValueSelectorConfig(new ValueSelectorConfig("quantity")),
                  new ListChangeMoveSelectorConfig()
                      .withValueSelectorConfig(new ValueSelectorConfig("customers")));
      case COUPLED_OLD ->
          new UnionMoveSelectorConfig()
              .withSelectionOrder(SelectionOrder.ORIGINAL)
              .withMoveSelectors(
                  new MultistageMoveSelectorConfig()
                      .withEntityClass(Vehicle.class)
                      .withVariableName("quantity")
                      .withStageProviderClass(CoupledQuantityStages.class)
                      .withSelectionOrder(SelectionOrder.ORIGINAL)
                      .withCandidateCountLimit(1),
                  new ListMultistageMoveSelectorConfig()
                      .withEntityClass(Vehicle.class)
                      .withVariableName("customers")
                      .withStageProviderClass(CoupledRouteStages.class)
                      .withSelectionOrder(SelectionOrder.ORIGINAL)
                      .withCandidateCountLimit(1));
      case COUPLED_CROSS -> CrossVariableVehicleTestSupport.selector(CoupledCrossStages.class);
      default -> throw new IllegalArgumentException("Not a coupled scenario: " + scenario);
    };
  }

  private static LocalSearchPhaseConfig phase(MoveSelectorConfig<?> selector, int steps) {
    return new LocalSearchPhaseConfig()
        .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
        .withMoveSelectorConfig(selector)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(steps));
  }

  private static <Solution_> Run measure(
      Scenario scenario,
      int iteration,
      SolverConfig config,
      Solution_ problem,
      Function<Solution_, List<String>> assignments,
      Function<Solution_, ? extends Score<?>> independentScore,
      AllocationCounter allocation)
      throws Exception {
    DefaultSolver<Solution_> solver = MultistageIntegrationSupport.solver(config);
    var trace = new ArrayList<Snapshot>();
    var work = new Work();
    WORK.set(work);
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<Solution_> scope) {
            var solution = scope.getWorkingSolution();
            trace.add(
                new Snapshot(
                    assignments.apply(solution),
                    scope.getScore().raw().toString(),
                    independentScore.apply(solution).toString()));
            if (((LocalSearchStepScope<Solution_>) scope).getStep()
                instanceof PreparedMultistageMove<?>) work.preparedStepReplays++;
          }
        });
    long allocatedBefore = allocation.bytes();
    long started = System.nanoTime();
    var result = solver.solve(problem);
    long elapsed = System.nanoTime() - started;
    long allocatedAfter = allocation.bytes();
    for (var snapshot : trace) assertThat(snapshot.score).isEqualTo(snapshot.independentScore);
    String score;
    if (result instanceof Plan plan) {
      CrossVariableVehicleTestSupport.verify(plan);
      score = plan.score.toString();
    } else {
      var basic = (TestdataSolution) result;
      MultistageIntegrationSupport.assertBasicScore(basic);
      score = basic.getScore().toString();
    }
    assertThat(independentScore.apply(result).toString()).isEqualTo(score);
    String digest =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(trace.toString().getBytes(StandardCharsets.UTF_8)));
    return new Run(
        new Measurement(
            scenario,
            iteration,
            elapsed,
            allocatedBefore < 0 || allocatedAfter < 0 ? -1 : allocatedAfter - allocatedBefore,
            solver.getScoreCalculationCount(),
            solver.getMoveEvaluationCount(),
            work.candidates,
            work.callbacks,
            work.probes,
            work.preparedStepReplays,
            trace.size(),
            score,
            digest),
        List.copyOf(trace));
  }

  private enum Scenario {
    FROZEN_OLD,
    FROZEN_CROSS,
    COUPLED_INDEPENDENT,
    COUPLED_OLD,
    COUPLED_CROSS
  }

  private record Snapshot(List<String> assignments, String score, String independentScore) {}

  private record Run(Measurement measurement, List<Snapshot> trace) {}

  private record Measurement(
      Scenario scenario,
      int iteration,
      long elapsedNs,
      long allocatedBytes,
      long scoreCalculations,
      long moveEvaluations,
      long candidates,
      long callbacks,
      long probes,
      long preparedStepReplays,
      int steps,
      String score,
      String traceDigest) {
    String csv() {
      return String.join(
          ",",
          scenario.name(),
          Integer.toString(iteration),
          Long.toString(elapsedNs),
          Long.toString(allocatedBytes),
          Long.toString(scoreCalculations),
          Long.toString(moveEvaluations),
          Long.toString(candidates),
          Long.toString(callbacks),
          Long.toString(probes),
          Long.toString(preparedStepReplays),
          Integer.toString(steps),
          score,
          traceDigest,
          Runtime.version().toString(),
          Integer.toString(Runtime.getRuntime().availableProcessors()));
    }
  }

  private static final class Work {
    long candidates;
    long callbacks;
    long probes;
    long preparedStepReplays;
  }

  private static final class AllocationCounter implements AutoCloseable {
    private final com.sun.management.ThreadMXBean bean;
    private final boolean initiallyEnabled;

    AllocationCounter() {
      var candidate = ManagementFactory.getThreadMXBean();
      bean =
          candidate instanceof com.sun.management.ThreadMXBean supported
                  && supported.isThreadAllocatedMemorySupported()
              ? supported
              : null;
      initiallyEnabled = bean != null && bean.isThreadAllocatedMemoryEnabled();
      if (bean != null && !initiallyEnabled) bean.setThreadAllocatedMemoryEnabled(true);
    }

    long bytes() {
      return bean == null ? -1 : bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
    }

    @Override
    public void close() {
      if (bean != null && !initiallyEnabled) bean.setThreadAllocatedMemoryEnabled(false);
    }
  }

  public static class FrozenOldStages
      implements BasicVariableStageProvider<
          TestdataSolution, TestdataEntity, TestdataValue, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<
            BasicVariableCustomStage<TestdataSolution, TestdataEntity, TestdataValue, SimpleScore>>
        createStages(long candidateIndex, RandomGenerator random) {
      WORK.get().candidates++;
      return List.of(
          evaluator -> {
            WORK.get().callbacks++;
            var solution = evaluator.workingSolution();
            var next =
                solution
                    .getValueList()
                    .get(
                        Integer.parseInt(solution.getEntityList().getFirst().getValue().getCode())
                            + 1);
            var operations = new ArrayList<MultistageOperation<TestdataSolution>>(ENTITIES);
            for (var entity : solution.getEntityList())
              operations.add(evaluator.assign(entity, next));
            var sequence = evaluator.sequence(operations);
            evaluator.evaluate(sequence);
            WORK.get().probes++;
            return MultistageStageResult.apply(sequence);
          });
    }
  }

  public static class FrozenCrossStages
      implements CrossVariableStageProvider<TestdataSolution, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<CrossVariableCustomStage<TestdataSolution, SimpleScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      WORK.get().candidates++;
      return List.of(
          evaluator -> {
            WORK.get().callbacks++;
            var solution = evaluator.workingSolution();
            var next =
                solution
                    .getValueList()
                    .get(
                        Integer.parseInt(solution.getEntityList().getFirst().getValue().getCode())
                            + 1);
            var view = evaluator.basic(VALUE);
            var operations = new ArrayList<MultistageOperation<TestdataSolution>>(ENTITIES);
            for (var entity : solution.getEntityList()) operations.add(view.assign(entity, next));
            var sequence = evaluator.sequence(operations);
            evaluator.evaluate(sequence);
            WORK.get().probes++;
            return MultistageStageResult.apply(sequence);
          });
    }
  }

  public static class CoupledCrossStages extends CrossVariableVehicleTestSupport.OrderedStages {
    @Override
    public List<CrossVariableCustomStage<Plan, HardSoftScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      WORK.get().candidates++;
      return List.of(
          evaluator -> {
            WORK.get().callbacks++;
            var source = solution.vehicles.getFirst();
            var target = solution.vehicles.getLast();
            var customer = source.customers.getFirst();
            var quantities = evaluator.basic(QUANTITY);
            var operation =
                evaluator.sequence(
                    List.of(
                        quantities.assign(source, source.quantity - customer.demand),
                        quantities.assign(target, target.quantity + customer.demand),
                        evaluator
                            .list(CUSTOMERS)
                            .place(customer, target, target.customers.size())));
            evaluator.evaluate(operation);
            WORK.get().probes++;
            return MultistageStageResult.apply(operation);
          });
    }
  }

  public static class CoupledQuantityStages
      implements BasicVariableStageProvider<Plan, Vehicle, Integer, HardSoftScore> {
    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<BasicVariableCustomStage<Plan, Vehicle, Integer, HardSoftScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      WORK.get().candidates++;
      return List.of(
          evaluator -> {
            WORK.get().callbacks++;
            var source = evaluator.workingSolution().vehicles.getFirst();
            var target = evaluator.workingSolution().vehicles.getLast();
            int demand = source.customers.getFirst().demand;
            var operation =
                evaluator.sequence(
                    List.of(
                        evaluator.assign(source, source.quantity - demand),
                        evaluator.assign(target, target.quantity + demand)));
            evaluator.evaluate(operation);
            WORK.get().probes++;
            return MultistageStageResult.apply(operation);
          });
    }
  }

  public static class CoupledRouteStages
      implements ListVariableStageProvider<Plan, Vehicle, Customer, HardSoftScore> {
    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<ListVariableCustomStage<Plan, Vehicle, Customer, HardSoftScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      WORK.get().candidates++;
      return List.of(
          evaluator -> {
            WORK.get().callbacks++;
            var source = evaluator.workingSolution().vehicles.getFirst();
            var target = evaluator.workingSolution().vehicles.getLast();
            var operation =
                evaluator.place(source.customers.getFirst(), target, target.customers.size());
            evaluator.evaluate(operation);
            WORK.get().probes++;
            return MultistageStageResult.apply(operation);
          });
    }
  }
}
