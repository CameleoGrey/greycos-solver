package greycos.solver.core.impl.multistage.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.api.solver.multistage.BasicVariableReference;
import greycos.solver.core.api.solver.multistage.CrossVariableCustomStage;
import greycos.solver.core.api.solver.multistage.CrossVariableStageProvider;
import greycos.solver.core.api.solver.multistage.MultistageStageResult;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.generic.CrossVariableMultistageMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.ScoreDirector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class CrossVariableBasicIntegrationTest {
  static final BasicVariableReference<Machine, Integer> MACHINE_VALUE =
      BasicVariableReference.of(Machine.class, "value", Integer.class);
  static final BasicVariableReference<Machine, String> MACHINE_MODE =
      BasicVariableReference.of(Machine.class, "mode", String.class);
  static final BasicVariableReference<Meter, Long> METER_VALUE =
      BasicVariableReference.of(Meter.class, "value", Long.class);

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2", "4"})
  void distinctTypesOnOneEntityAndIdenticallyNamedVariablesOnDifferentEntities(String workers) {
    var problem = problem(Behavior.COMPLETE, 1);
    var solver = MultistageIntegrationSupport.<DualSolution>solver(config(workers));
    var trace = new ArrayList<HardSoftScore>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<DualSolution> scope) {
            assertThat(scope.getScore().raw())
                .isEqualTo(independentScore(scope.getWorkingSolution()));
            trace.add((HardSoftScore) scope.getScore().raw());
          }
        });
    var result = solver.solve(problem);
    assertThat(trace).containsExactly(HardSoftScore.of(0, 2000));
    assertCompleted(result, 1);
    assertThat(problem.machines.getFirst().value).isEqualTo(1000);
    assertThat(problem.machines.getFirst().mode).isEqualTo("slow");
    assertThat(problem.meters.getFirst().value).isEqualTo(1000L);
  }

  static Stream<Arguments> discardCases() {
    return Stream.of("NONE", "1", "2", "4")
        .flatMap(
            workers ->
                Stream.of(Behavior.ABORT, Behavior.ROUND_TRIP, Behavior.SHARED_BUDGET)
                    .map(behavior -> Arguments.of(workers, behavior)));
  }

  @ParameterizedTest
  @MethodSource("discardCases")
  void abortNetZeroAndBudgetRestoreEveryDescriptorOnTheSameEntity(
      String workers, Behavior behavior) {
    var problem = problem(behavior, 1);
    var selector = selector();
    if (behavior == Behavior.SHARED_BUDGET) selector.withProbeCountLimit(1);
    var solver =
        MultistageIntegrationSupport.<DualSolution>solver(
            config(workers).withPhases(phase(selector)));
    var steps = new ArrayList<HardSoftScore>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<DualSolution> scope) {
            steps.add((HardSoftScore) scope.getScore().raw());
          }
        });
    var result = solver.solve(problem);
    assertThat(steps).isEmpty();
    assertThat(result.machines.getFirst().value).isEqualTo(1000);
    assertThat(result.machines.getFirst().mode).isEqualTo("slow");
    assertThat(result.meters.getFirst().value).isEqualTo(1000L);
    assertThat(result.score)
        .isEqualTo(HardSoftScore.of(0, 1000))
        .isEqualTo(independentScore(result));
  }

  @Test
  void partitionsResolveEveryVariableAgainstTheirOwnEntities() {
    var result =
        MultistageIntegrationSupport.<DualSolution>solver(
                config("NONE")
                    .withPhases(
                        new PartitionedSearchPhaseConfig()
                            .withSolutionPartitionerClass(PairPartitioner.class)
                            .withRunnablePartThreadLimit("1")
                            .withPhaseConfigs(phase(selector()))))
            .solve(problem(Behavior.COMPLETE, 2));
    assertCompleted(result, 2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void entitySpecificRangesRejectAPartialCompoundProbeWithoutLosingTheEarlierStage(String workers) {
    var problem = problem(Behavior.COMPLETE, 2);
    problem.machines.getLast().modes = List.of("slow", "turbo");
    var config =
        config(workers)
            .withPhases(phase(selector().withStageProviderClass(EntityRangeStages.class)));
    var result = MultistageIntegrationSupport.<DualSolution>solver(config).solve(problem);
    assertThat(result.machines)
        .extracting(machine -> machine.mode)
        .containsExactly("fast", "turbo");
    assertThat(result.machines)
        .allSatisfy(
            machine -> {
              assertThat(machine.value).isEqualTo(2000);
              assertThat(machine.modes).contains(machine.mode);
            });
    assertThat(result.meters).allSatisfy(meter -> assertThat(meter.value).isEqualTo(2000L));
    assertThat(result.score)
        .isEqualTo(HardSoftScore.of(0, 4000))
        .isEqualTo(independentScore(result));
  }

  private static void assertCompleted(DualSolution solution, int count) {
    assertThat(solution.machines)
        .hasSize(count)
        .allSatisfy(
            machine -> {
              assertThat(machine.value).isEqualTo(2000);
              assertThat(machine.mode).isEqualTo("fast");
              assertThat(machine.modes).contains(machine.mode);
            });
    assertThat(solution.meters)
        .hasSize(count)
        .allSatisfy(meter -> assertThat(meter.value).isEqualTo(2000L));
    assertThat(solution.score)
        .isEqualTo(HardSoftScore.of(0, 2000L * count))
        .isEqualTo(independentScore(solution));
  }

  private static DualSolution problem(Behavior behavior, int pairs) {
    var solution = new DualSolution();
    solution.behavior = behavior;
    solution.integerValues = List.of(1000, 2000);
    solution.longValues = List.of(1000L, 2000L);
    solution.machines = new ArrayList<>();
    solution.meters = new ArrayList<>();
    for (int i = 0; i < pairs; i++) {
      solution.machines.add(new Machine("pair-" + i, 1000, "slow"));
      solution.meters.add(new Meter("pair-" + i, 1000L));
    }
    return solution;
  }

  private static SolverConfig config(String workers) {
    return new SolverConfig()
        .withSolutionClass(DualSolution.class)
        .withEntityClasses(Machine.class, Meter.class)
        .withConstraintProviderClass(DualConstraints.class)
        .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
        .withMoveThreadCount(workers)
        .withRandomSeed(7L)
        .withPhases(phase(selector()));
  }

  private static CrossVariableMultistageMoveSelectorConfig selector() {
    return new CrossVariableMultistageMoveSelectorConfig()
        .withVariables(MACHINE_VALUE, MACHINE_MODE, METER_VALUE)
        .withStageProviderClass(DualStages.class)
        .withSelectionOrder(SelectionOrder.ORIGINAL)
        .withCandidateCountLimit(1);
  }

  private static LocalSearchPhaseConfig phase(CrossVariableMultistageMoveSelectorConfig selector) {
    return new LocalSearchPhaseConfig()
        .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
        .withMoveSelectorConfig(selector)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(1));
  }

  private static HardSoftScore independentScore(DualSolution solution) {
    long hard = 0;
    long soft = 0;
    for (var machine : solution.machines) {
      var meter =
          solution.meters.stream()
              .filter(value -> value.id.equals(machine.id))
              .findFirst()
              .orElseThrow();
      hard -= Math.abs(machine.value - meter.value);
      if (machine.mode != null)
        hard -= Math.abs(machine.value - (machine.mode.equals("slow") ? 1000 : 2000));
      soft += machine.value;
    }
    return HardSoftScore.of(hard, soft);
  }

  enum Behavior {
    COMPLETE,
    ABORT,
    ROUND_TRIP,
    SHARED_BUDGET
  }

  @PlanningSolution
  public static class DualSolution {
    public Behavior behavior;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "integers")
    public List<Integer> integerValues;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "longs")
    public List<Long> longValues;

    @PlanningEntityCollectionProperty public List<Machine> machines;
    @PlanningEntityCollectionProperty public List<Meter> meters;
    @PlanningScore public HardSoftScore score;
  }

  @PlanningEntity
  public static class Machine {
    @PlanningId public String id;

    @PlanningVariable(valueRangeProviderRefs = "integers")
    public Integer value;

    @PlanningVariable(valueRangeProviderRefs = "modes", allowsUnassigned = true)
    public String mode;

    @ValueRangeProvider(id = "modes")
    public List<String> modes = List.of("slow", "fast");

    public Machine() {}

    public Machine(String id, Integer value, String mode) {
      this.id = id;
      this.value = value;
      this.mode = mode;
    }
  }

  @PlanningEntity
  public static class Meter {
    @PlanningId public String id;

    @PlanningVariable(valueRangeProviderRefs = "longs")
    public Long value;

    public Meter() {}

    public Meter(String id, Long value) {
      this.id = id;
      this.value = value;
    }
  }

  public static class DualConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEachIncludingUnassigned(Machine.class)
            .join(Meter.class, Joiners.equal(machine -> machine.id, meter -> meter.id))
            .penalize(
                HardSoftScore.ONE_HARD, (machine, meter) -> Math.abs(machine.value - meter.value))
            .asConstraint("Machine and meter agree"),
        factory
            .forEachIncludingUnassigned(Machine.class)
            .filter(machine -> machine.mode != null)
            .penalize(
                HardSoftScore.ONE_HARD,
                machine -> Math.abs(machine.value - (machine.mode.equals("slow") ? 1000L : 2000L)))
            .asConstraint("Mode and quantity agree"),
        factory
            .forEachIncludingUnassigned(Machine.class)
            .reward(HardSoftScore.ONE_SOFT, machine -> machine.value)
            .asConstraint("Production")
      };
    }
  }

  public static class DualStages
      implements CrossVariableStageProvider<DualSolution, HardSoftScore> {
    private DualSolution solution;

    @Override
    public void initialize(DualSolution solution) {
      this.solution = solution;
    }

    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public void phaseEnded() {
      solution = null;
    }

    @Override
    public List<CrossVariableCustomStage<DualSolution, HardSoftScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      var machine = solution.machines.getFirst();
      var meter = solution.meters.getFirst();
      return List.of(
          evaluator -> {
            var values = evaluator.basic(MACHINE_VALUE);
            var modes = evaluator.basic(MACHINE_MODE);
            assertThat(values.legalValues(machine)).containsExactly(1000, 2000);
            assertThat(modes.legalValues(machine)).containsExactly(null, "slow", "fast");
            var pair =
                values.sequence(
                    List.of(values.assign(machine, 2000), modes.assign(machine, "fast")));
            assertThat(modes.evaluate(pair).score()).isEqualTo(HardSoftScore.of(-1000, 2000));
            assertThat(machine.value).isEqualTo(1000);
            assertThat(machine.mode).isEqualTo("slow");
            assertThat(meter.value).isEqualTo(1000L);
            if (solution.behavior == Behavior.SHARED_BUDGET) {
              // A different typed view consumes the same candidate probe budget.
              evaluator
                  .basic(METER_VALUE)
                  .evaluate(evaluator.basic(METER_VALUE).assign(meter, 2000L));
              throw new AssertionError("The second probe must exhaust the shared budget.");
            }
            return MultistageStageResult.apply(values.assign(machine, 2000));
          },
          evaluator -> {
            assertThat(machine.value).isEqualTo(2000);
            var modes = evaluator.basic(MACHINE_MODE);
            assertThat(modes.evaluate(modes.unassign(machine)).isComplete()).isTrue();
            assertThat(machine.mode).isEqualTo("slow");
            return modes.bestFit(List.of(modes.assign(machine, "fast"), modes.unassign(machine)));
          },
          evaluator -> {
            assertThat(machine.value).isEqualTo(2000);
            assertThat(machine.mode).isEqualTo("fast");
            assertThat(evaluator.basic(METER_VALUE).currentValue(meter)).isEqualTo(1000L);
            return MultistageStageResult.apply(evaluator.basic(METER_VALUE).assign(meter, 2000L));
          },
          evaluator -> {
            assertThat(evaluator.currentEvaluation().score()).isEqualTo(HardSoftScore.of(0, 2000));
            return switch (solution.behavior) {
              case COMPLETE -> MultistageStageResult.skip();
              case ABORT -> MultistageStageResult.abortCandidate();
              case ROUND_TRIP ->
                  MultistageStageResult.apply(
                      evaluator.sequence(
                          List.of(
                              evaluator.basic(MACHINE_VALUE).assign(machine, 1000),
                              evaluator.basic(MACHINE_MODE).assign(machine, "slow"),
                              evaluator.basic(METER_VALUE).assign(meter, 1000L))));
              case SHARED_BUDGET ->
                  throw new AssertionError("Exhausted candidates must not reach another stage.");
            };
          });
    }
  }

  public static class EntityRangeStages
      implements CrossVariableStageProvider<DualSolution, HardSoftScore> {
    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<CrossVariableCustomStage<DualSolution, HardSoftScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      return List.of(
          evaluator ->
              MultistageStageResult.apply(
                  evaluator
                      .basic(MACHINE_MODE)
                      .assign(evaluator.workingSolution().machines.getFirst(), "fast")),
          evaluator -> {
            var solution = evaluator.workingSolution();
            var first = solution.machines.getFirst();
            var second = solution.machines.getLast();
            var modes = evaluator.basic(MACHINE_MODE);
            var values = evaluator.basic(MACHINE_VALUE);
            var meters = evaluator.basic(METER_VALUE);
            assertThat(modes.legalValues(first)).containsExactly(null, "slow", "fast");
            assertThat(modes.legalValues(second)).containsExactly(null, "slow", "turbo");
            var baselineScore = independentScore(solution);
            // The meter assignment is valid and executes before the later swap fails its range
            // check.
            var invalid =
                evaluator.sequence(
                    List.of(
                        meters.assign(solution.meters.getFirst(), 2000L),
                        modes.swap(first, second)));
            assertThatIllegalArgumentException()
                .isThrownBy(() -> evaluator.evaluate(invalid))
                .withMessageContaining("range");
            assertThat(first.mode).isEqualTo("fast");
            assertThat(second.mode).isEqualTo("slow");
            assertThat(solution.meters.getFirst().value).isEqualTo(1000L);
            assertThat(independentScore(solution)).isEqualTo(baselineScore);
            assertThat(evaluator.currentEvaluation().score()).isEqualTo(baselineScore);
            return MultistageStageResult.apply(
                evaluator.sequence(
                    List.of(
                        values.assign(first, 2000),
                        values.assign(second, 2000),
                        modes.assign(second, "turbo"),
                        meters.assign(solution.meters.getFirst(), 2000L),
                        meters.assign(solution.meters.getLast(), 2000L))));
          });
    }
  }

  public static class PairPartitioner implements SolutionPartitioner<DualSolution> {
    @Override
    public List<DualSolution> splitWorkingSolution(
        ScoreDirector<DualSolution> scoreDirector, Integer runnablePartThreadLimit) {
      var whole = scoreDirector.getWorkingSolution();
      var partitions = new ArrayList<DualSolution>();
      for (int i = 0; i < whole.machines.size(); i++) {
        var partition = new DualSolution();
        partition.behavior = whole.behavior;
        partition.integerValues = whole.integerValues;
        partition.longValues = whole.longValues;
        var machine = whole.machines.get(i);
        var meter = whole.meters.get(i);
        partition.machines = List.of(new Machine(machine.id, machine.value, machine.mode));
        partition.meters = List.of(new Meter(meter.id, meter.value));
        partitions.add(partition);
      }
      return partitions;
    }
  }
}
