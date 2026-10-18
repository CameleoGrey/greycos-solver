package greycos.solver.core.impl.multiplelist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.constructionheuristic.placer.QueuedValuePlacerConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.score.trend.InitializingScoreTrendLevel;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.constructionheuristic.DefaultConstructionHeuristicPhaseFactory;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.move.generic.RuinRecreateConstructionHeuristicPhaseBuilder;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ruin.SelectorBasedListRuinRecreateMove;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Timeout(30)
class MultipleListConstructionLimitTest {

  @ParameterizedTest
  @CsvSource({
    "false, 1, 4",
    "true, 1, 4",
    "false, 0, 4",
    "true, 0, 4",
    "false, 1, 0",
    "true, 1, 0"
  })
  void optionalConstructionOnlyVisitsItsOwnPopulation(
      boolean reversePhases, int optionalCount, int requiredCount) {
    var problem = problem(optionalCount, requiredCount);
    var config =
        new SolverConfig()
            .withSolutionClass(Solution.class)
            .withEntityClasses(Owner.class)
            .withEasyScoreCalculatorClass(Calculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withTerminationConfig(new TerminationConfig().withMoveCountLimit(20L));
    if (reversePhases) {
      config.withPhases(
          construction(Owner.class, "secondRequired"), construction(Owner.class, "firstOptional"));
    }
    var solver = (DefaultSolver<Solution>) SolverFactory.<Solution>create(config).buildSolver();
    var steps = constructionSteps(solver);
    var result = solver.solve(problem);

    assertThat(steps.getOrDefault(reversePhases ? 1 : 0, 0)).isEqualTo(optionalCount);
    assertThat(steps.getOrDefault(reversePhases ? 0 : 1, 0)).isEqualTo(requiredCount);
    assertThat(result.owners.getFirst().firstOptional).isEmpty();
    assertThat(result.owners.getFirst().secondRequired)
        .containsExactlyInAnyOrderElementsOf(problem.requiredValues);
    assertThat(result.score).isEqualTo(new Calculator().calculateScore(result));
  }

  @Test
  void entityProvidedRangesDoNotIncreaseAnotherListsConstructionLimit() {
    var problem = new EntityRangeSolution();
    problem.optionalValues = List.of("skip");
    problem.requiredValues = List.of(1, 2, 3, 4);
    var owner = new EntityRangeOwner();
    owner.optionalRange = problem.optionalValues;
    owner.requiredRange = problem.requiredValues;
    problem.owners = List.of(owner);
    var config =
        new SolverConfig()
            .withSolutionClass(EntityRangeSolution.class)
            .withEntityClasses(EntityRangeOwner.class)
            .withEasyScoreCalculatorClass(EntityRangeCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withPhases(
                construction(EntityRangeOwner.class, "firstOptional"),
                construction(EntityRangeOwner.class, "secondRequired"));
    var solver =
        (DefaultSolver<EntityRangeSolution>)
            SolverFactory.<EntityRangeSolution>create(config).buildSolver();
    var steps = constructionSteps(solver);
    var result = solver.solve(problem);

    assertThat(steps).containsEntry(0, 1).containsEntry(1, 4);
    assertThat(result.owners.getFirst().firstOptional).isEmpty();
    assertThat(result.owners.getFirst().secondRequired)
        .containsExactlyInAnyOrderElementsOf(problem.requiredValues);
    assertThat(result.score).isEqualTo(new EntityRangeCalculator().calculateScore(result));
  }

  @Test
  void nestedRepairWorkDoesNotDependOnOtherListsUnassignedValues() {
    assertThat(repairCalculationCount(4)).isEqualTo(repairCalculationCount(0));
  }

  private long repairCalculationCount(int unrelatedValues) {
    var descriptor = SolutionDescriptor.buildSolutionDescriptor(Solution.class, Owner.class);
    var target = descriptor.resolveListVariableDescriptor(Owner.class, "firstOptional");
    var problem = problem(1, unrelatedValues);
    var owner = problem.owners.getFirst();
    var value = problem.optionalValues.getFirst();
    owner.firstOptional.add(value);
    try (var director =
        new EasyScoreDirectorFactory<>(descriptor, new Calculator(), EnvironmentMode.PHASE_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(problem);
      var initialScore = director.calculateScore();
      var policy =
          new HeuristicConfigPolicy.Builder<Solution>()
              .withEnvironmentMode(EnvironmentMode.PHASE_ASSERT)
              .withSolutionDescriptor(descriptor)
              .withInitializingScoreTrend(
                  InitializingScoreTrend.buildUniformTrend(InitializingScoreTrendLevel.ANY, 1))
              .build();
      var placer =
          DefaultConstructionHeuristicPhaseFactory.buildListVariableQueuedValuePlacerConfig(
              policy, target);
      var builder =
          RuinRecreateConstructionHeuristicPhaseBuilder.create(
              policy,
              new ConstructionHeuristicPhaseConfig().withEntityPlacerConfig(placer),
              target);
      var scope = new SolverScope<Solution>();
      scope.setScoreDirector(director);
      var move =
          new SelectorBasedListRuinRecreateMove<>(
              target, builder, scope, List.of(value), new LinkedHashSet<>(List.of(owner)), 0L);
      long before = director.getCalculationCount();
      director.executeTemporaryMove(
          move,
          ignored -> {
            assertThat(owner.firstOptional).isEmpty();
            assertThat(owner.secondRequired).isEmpty();
          },
          true);
      long calculations = director.getCalculationCount() - before;
      assertThat(owner.firstOptional).containsExactly(value);
      assertThat(owner.secondRequired).isEmpty();
      assertThat(director.calculateScore()).isEqualTo(initialScore);
      return calculations;
    }
  }

  private static ConstructionHeuristicPhaseConfig construction(
      Class<?> ownerClass, String variableName) {
    return new ConstructionHeuristicPhaseConfig()
        .withEntityPlacerConfig(
            new QueuedValuePlacerConfig()
                .withEntityClass(ownerClass)
                .withValueSelectorConfig(new ValueSelectorConfig(variableName).withId(variableName))
                .withMoveSelectorConfig(
                    new ListChangeMoveSelectorConfig()
                        .withValueSelectorConfig(
                            new ValueSelectorConfig().withMimicSelectorRef(variableName))
                        .withDestinationSelectorConfig(
                            new DestinationSelectorConfig()
                                .withEntitySelectorConfig(new EntitySelectorConfig(ownerClass))
                                .withValueSelectorConfig(new ValueSelectorConfig(variableName)))));
  }

  private static <S> Map<Integer, Integer> constructionSteps(DefaultSolver<S> solver) {
    var counts = new HashMap<Integer, Integer>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<S> step) {
            if (step instanceof ConstructionHeuristicStepScope<?>) {
              counts.merge(step.getPhaseScope().getPhaseIndex(), 1, Integer::sum);
            }
          }
        });
    return counts;
  }

  private static Solution problem(int optionalCount, int requiredCount) {
    var problem = new Solution();
    problem.optionalValues = optionalCount == 0 ? List.of() : List.of("skip");
    problem.requiredValues = IntStream.rangeClosed(1, requiredCount).boxed().toList();
    problem.owners = List.of(new Owner());
    return problem;
  }

  public static class Calculator implements EasyScoreCalculator<Solution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(Solution solution) {
      return SimpleScore.of(
          -solution.owners.stream().mapToInt(owner -> owner.firstOptional.size()).sum());
    }
  }

  public static class EntityRangeCalculator
      implements EasyScoreCalculator<EntityRangeSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(EntityRangeSolution solution) {
      return SimpleScore.of(
          -solution.owners.stream().mapToInt(owner -> owner.firstOptional.size()).sum());
    }
  }

  @PlanningEntity
  public static class Owner {
    @PlanningListVariable(valueRangeProviderRefs = "optional", allowsUnassignedValues = true)
    public List<String> firstOptional = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "required")
    public List<Integer> secondRequired = new ArrayList<>();
  }

  @PlanningSolution
  public static class Solution {
    @PlanningEntityCollectionProperty public List<Owner> owners;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "optional")
    public List<String> optionalValues;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "required")
    public List<Integer> requiredValues;

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class EntityRangeOwner {
    @PlanningListVariable(valueRangeProviderRefs = "optional", allowsUnassignedValues = true)
    public List<String> firstOptional = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "required")
    public List<Integer> secondRequired = new ArrayList<>();

    @ValueRangeProvider(id = "optional")
    public List<String> optionalRange;

    @ValueRangeProvider(id = "required")
    public List<Integer> requiredRange;
  }

  @PlanningSolution
  public static class EntityRangeSolution {
    @PlanningEntityCollectionProperty public List<EntityRangeOwner> owners;
    @ProblemFactCollectionProperty public List<String> optionalValues;
    @ProblemFactCollectionProperty public List<Integer> requiredValues;
    @PlanningScore public SimpleScore score;
  }
}
