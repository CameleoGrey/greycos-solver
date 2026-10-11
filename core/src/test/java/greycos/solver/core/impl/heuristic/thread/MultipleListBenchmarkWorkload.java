package greycos.solver.core.impl.heuristic.thread;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListFirstValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSecondValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

/**
 * A synthetic workload; its scores and work rates are not comparable to the single-list workload.
 */
public final class MultipleListBenchmarkWorkload
    implements MoveThreadingWorkload.Workload<TestdataMultipleListSolution> {

  @Override
  public TestdataMultipleListSolution createProblem(int size) {
    require(size >= 4, "Workload size must be at least 4.");
    var solution =
        TestdataMultipleListSolution.generateInitializedSolution(
            size, size, Math.max(2, size / 25));
    solution.setScore(recompute(solution));
    return solution;
  }

  @Override
  public SolverConfig solverConfig(
      String threads,
      long seed,
      int acceptedCount,
      TerminationConfig termination,
      EnvironmentMode environmentMode) {
    var moves =
        new UnionMoveSelectorConfig()
            .withMoveSelectors(
                listChange("firstValueList"),
                listSwap("firstValueList"),
                listChange("secondValueList"),
                listSwap("secondValueList"),
                new ChangeMoveSelectorConfig()
                    .withEntitySelectorConfig(
                        new EntitySelectorConfig(TestdataMultipleListEntity.class))
                    .withValueSelectorConfig(new ValueSelectorConfig("basicValue")),
                new ChangeMoveSelectorConfig()
                    .withEntitySelectorConfig(
                        new EntitySelectorConfig(TestdataMultipleListSecondValue.class))
                    .withValueSelectorConfig(new ValueSelectorConfig("quantity")));
    return new SolverConfig()
        .withSolutionClass(TestdataMultipleListSolution.class)
        .withEntityClasses(
            TestdataMultipleListEntity.class,
            TestdataMultipleListFirstValue.class,
            TestdataMultipleListSecondValue.class)
        .withConstraintProviderClass(Constraints.class)
        .withEnvironmentMode(environmentMode)
        .withRandomSeed(seed)
        .withMoveThreadCount(threads)
        .withMonitoringConfig(new MonitoringConfig().withSolverMetricList(List.of()))
        .withPhases(
            new LocalSearchPhaseConfig()
                .withMoveSelectorConfig(moves)
                .withAcceptorConfig(new LocalSearchAcceptorConfig().withLateAcceptanceSize(64))
                .withForagerConfig(
                    new LocalSearchForagerConfig().withAcceptedCountLimit(acceptedCount))
                .withTerminationConfig(termination));
  }

  private static ListChangeMoveSelectorConfig listChange(String variableName) {
    return new ListChangeMoveSelectorConfig()
        .withValueSelectorConfig(new ValueSelectorConfig(variableName))
        .withDestinationSelectorConfig(
            new DestinationSelectorConfig()
                .withEntitySelectorConfig(
                    new EntitySelectorConfig(TestdataMultipleListEntity.class))
                .withValueSelectorConfig(new ValueSelectorConfig(variableName)));
  }

  private static ListSwapMoveSelectorConfig listSwap(String variableName) {
    return new ListSwapMoveSelectorConfig()
        .withEntityClass(TestdataMultipleListEntity.class)
        .withValueSelectorConfig(new ValueSelectorConfig(variableName));
  }

  public static final class Constraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataMultipleListEntity.class)
            .penalize(
                SimpleScore.ONE,
                entity -> {
                  long firstSize = entity.getFirstValueList().size();
                  long secondSize = entity.getSecondValueList().size();
                  return firstSize * firstSize
                      + secondSize * secondSize
                      + 8L * Math.abs(firstSize - secondSize)
                      + 11L * Math.abs(entity.getBasicValue() - (firstSize + secondSize) % 4);
                })
            .asConstraint("Owner load and setting"),
        factory
            .forEach(TestdataMultipleListFirstValue.class)
            .penalize(
                SimpleScore.ONE,
                value -> (long) (value.getIndex() + 1) * (1 + id(value.getCode()) % 11))
            .asConstraint("First list order"),
        factory
            .forEach(TestdataMultipleListSecondValue.class)
            .penalize(
                SimpleScore.ONE,
                value ->
                    (long) (value.getIndex() + 1) * (1 + id(value.getCode()) % 13)
                        + 7L * Math.abs(value.getQuantity() - value.getIndex() % 4))
            .asConstraint("Second list order and quantity")
      };
    }
  }

  @Override
  public SimpleScore score(TestdataMultipleListSolution solution) {
    return solution.getScore();
  }

  @Override
  public SimpleScore recompute(TestdataMultipleListSolution solution) {
    Set<Object> firstSeen = Collections.newSetFromMap(new IdentityHashMap<>());
    Set<Object> secondSeen = Collections.newSetFromMap(new IdentityHashMap<>());
    Set<Object> ownersSeen = Collections.newSetFromMap(new IdentityHashMap<>());
    long penalty = 0;
    for (var entity : solution.getEntityList()) {
      require(ownersSeen.add(entity), "Duplicate owner.");
      require(
          solution.getBasicValueRange().contains(entity.getBasicValue()), "Invalid owner setting.");
      var first = entity.getFirstValueList();
      var second = entity.getSecondValueList();
      penalty += (long) first.size() * first.size() + (long) second.size() * second.size();
      penalty += 8L * Math.abs(first.size() - second.size());
      penalty += 11L * Math.abs(entity.getBasicValue() - (first.size() + second.size()) % 4);
      for (int i = 0; i < first.size(); i++) {
        var value = first.get(i);
        int id = id(value.getCode());
        require(
            id >= 0
                && id < solution.getFirstValueList().size()
                && solution.getFirstValueList().get(id) == value
                && firstSeen.add(value),
            "Invalid first-list population.");
        require(
            value.getEntity() == entity
                && Integer.valueOf(i).equals(value.getIndex())
                && value.getPrevious() == (i == 0 ? null : first.get(i - 1))
                && value.getNext() == (i + 1 == first.size() ? null : first.get(i + 1)),
            "Invalid first-list shadows.");
        penalty += (long) (i + 1) * (1 + id % 11);
      }
      for (int i = 0; i < second.size(); i++) {
        var value = second.get(i);
        int id = id(value.getCode());
        require(
            id >= 0
                && id < solution.getSecondValueList().size()
                && solution.getSecondValueList().get(id) == value
                && secondSeen.add(value),
            "Invalid second-list population.");
        require(
            value.getEntity() == entity
                && Integer.valueOf(i).equals(value.getIndex())
                && value.getPrevious() == (i == 0 ? null : second.get(i - 1))
                && value.getNext() == (i + 1 == second.size() ? null : second.get(i + 1)),
            "Invalid second-list shadows.");
        require(
            solution.getBasicValueRange().contains(value.getQuantity()),
            "Invalid second-list quantity.");
        penalty += (long) (i + 1) * (1 + id % 13) + 7L * Math.abs(value.getQuantity() - i % 4);
      }
    }
    require(firstSeen.size() == solution.getFirstValueList().size(), "Missing first-list values.");
    require(
        secondSeen.size() == solution.getSecondValueList().size(), "Missing second-list values.");
    return SimpleScore.of(-penalty);
  }

  @Override
  public String state(TestdataMultipleListSolution solution) {
    var state = new StringBuilder();
    for (var entity : solution.getEntityList()) {
      state.append(entity.getCode()).append('=').append(entity.getBasicValue()).append(";first=");
      for (var value : entity.getFirstValueList()) {
        state.append(value.getCode()).append('@').append(value.getIndex()).append(',');
      }
      state.append(";second=");
      for (var value : entity.getSecondValueList()) {
        state
            .append(value.getCode())
            .append('@')
            .append(value.getIndex())
            .append(':')
            .append(value.getQuantity())
            .append(',');
      }
      state.append('\n');
    }
    return state.toString();
  }

  private static int id(String code) {
    return Integer.parseInt(code.substring(code.lastIndexOf('-') + 1));
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}
