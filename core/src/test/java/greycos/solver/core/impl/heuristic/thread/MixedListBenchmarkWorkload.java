package greycos.solver.core.impl.heuristic.thread;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
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
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedOtherValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;

/** Uses the existing mixed fixture and APIs shared by the baseline and multiple-list engines. */
public final class MixedListBenchmarkWorkload
    implements MoveThreadingWorkload.Workload<TestdataMixedSolution> {

  @Override
  public TestdataMixedSolution createProblem(int size) {
    require(size >= 4, "Workload size must be at least 4.");
    int ownerCount = Math.max(2, size / 25);
    var solution =
        TestdataMixedSolution.generateUninitializedSolution(
            ownerCount, size, Math.max(4, size / 10));
    for (int i = 0; i < solution.getOtherValueList().size(); i++) {
      solution.getOtherValueList().get(i).setStrength(1 + 3 * i);
    }
    for (int i = 0; i < ownerCount; i++) {
      var entity = solution.getEntityList().get(i);
      entity.setBasicValue(
          solution.getOtherValueList().get(i % solution.getOtherValueList().size()));
      entity.setSecondBasicValue(
          solution.getOtherValueList().get((3 * i + 1) % solution.getOtherValueList().size()));
      entity.setPinnedIndex(1);
    }
    for (int i = 0; i < size; i++) {
      solution
          .getEntityList()
          .get(i % ownerCount)
          .getValueList()
          .add(solution.getValueList().get(i));
    }
    SolutionManager.updateShadowVariables(solution);
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
                new ListChangeMoveSelectorConfig()
                    .withValueSelectorConfig(new ValueSelectorConfig("valueList")),
                new ListSwapMoveSelectorConfig()
                    .withValueSelectorConfig(new ValueSelectorConfig("valueList")),
                new ChangeMoveSelectorConfig()
                    .withEntitySelectorConfig(new EntitySelectorConfig(TestdataMixedEntity.class))
                    .withValueSelectorConfig(new ValueSelectorConfig("basicValue")),
                new ChangeMoveSelectorConfig()
                    .withEntitySelectorConfig(new EntitySelectorConfig(TestdataMixedEntity.class))
                    .withValueSelectorConfig(new ValueSelectorConfig("secondBasicValue")));
    return new SolverConfig()
        .withSolutionClass(TestdataMixedSolution.class)
        .withEntityClasses(
            TestdataMixedEntity.class, TestdataMixedValue.class, TestdataMixedOtherValue.class)
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

  public static final class Constraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataMixedEntity.class)
            .penalize(
                SimpleScore.ONE,
                entity -> {
                  long size = entity.getValueList().size();
                  return size * size
                      + 3L * Math.abs(2 * size - entity.getBasicValue().getStrength())
                      + 5L * Math.abs(size - entity.getSecondBasicValue().getStrength());
                })
            .asConstraint("Load and basic capacities"),
        factory
            .forEach(TestdataMixedValue.class)
            .penalize(
                SimpleScore.ONE,
                value -> (long) (value.getIndex() + 1) * (1 + id(value.getCode()) % 11))
            .asConstraint("List order")
      };
    }
  }

  @Override
  public SimpleScore score(TestdataMixedSolution solution) {
    return solution.getScore();
  }

  @Override
  public SimpleScore recompute(TestdataMixedSolution solution) {
    Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Set<Object> ownerSeen = Collections.newSetFromMap(new IdentityHashMap<>());
    var inverse = new IdentityHashMap<TestdataMixedOtherValue, Set<TestdataMixedEntity>>();
    for (int i = 0; i < solution.getOtherValueList().size(); i++) {
      var value = solution.getOtherValueList().get(i);
      require(
          id(value.getCode()) == i && value.getStrength() == 1 + 3 * i,
          "Invalid basic value fact.");
      inverse.put(value, Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    long penalty = 0;
    for (int ownerIndex = 0; ownerIndex < solution.getEntityList().size(); ownerIndex++) {
      var entity = solution.getEntityList().get(ownerIndex);
      require(
          ownerSeen.add(entity) && id(entity.getCode()) == ownerIndex,
          "Duplicate or reordered owner.");
      require(
          inverse.containsKey(entity.getBasicValue())
              && inverse.containsKey(entity.getSecondBasicValue()),
          "Invalid basic assignment.");
      inverse.get(entity.getBasicValue()).add(entity);
      require(
          Integer.valueOf(entity.getBasicValue().getStrength())
              .equals(entity.getDeclarativeShadowVariableValue()),
          "Invalid owner declarative shadow.");
      var list = entity.getValueList();
      require(
          entity.getPinnedIndex() == 1
              && !entity.isPinned()
              && !list.isEmpty()
              && list.getFirst() == solution.getValueList().get(ownerIndex),
          "Pinned prefix changed.");
      penalty += (long) list.size() * list.size();
      penalty += 3L * Math.abs(2L * list.size() - entity.getBasicValue().getStrength());
      penalty += 5L * Math.abs((long) list.size() - entity.getSecondBasicValue().getStrength());
      for (int i = 0; i < list.size(); i++) {
        var value = list.get(i);
        int id = id(value.getCode());
        require(
            id >= 0
                && id < solution.getValueList().size()
                && solution.getValueList().get(id) == value
                && seen.add(value),
            "Invalid list population.");
        require(
            value.getEntity() == entity
                && Integer.valueOf(i).equals(value.getIndex())
                && value.getPreviousElement() == (i == 0 ? null : list.get(i - 1))
                && value.getNextElement() == (i + 1 == list.size() ? null : list.get(i + 1)),
            "Invalid built-in list shadows.");
        require(
            Integer.valueOf(i + 1).equals(value.getCascadingShadowVariableValue())
                && Integer.valueOf(i + 2).equals(value.getDeclarativeShadowVariableValue()),
            "Invalid cascading or declarative list shadow.");
        penalty += (long) (i + 1) * (1 + id % 11);
      }
    }
    require(seen.size() == solution.getValueList().size(), "Missing list values.");
    for (var value : solution.getOtherValueList()) {
      var expectedOwners = inverse.get(value);
      Set<TestdataMixedEntity> actualOwners = Collections.newSetFromMap(new IdentityHashMap<>());
      actualOwners.addAll(value.getEntityList());
      require(
          actualOwners.size() == value.getEntityList().size()
              && actualOwners.equals(expectedOwners),
          "Invalid basic inverse shadow.");
      require(
          Integer.valueOf(expectedOwners.size() + 2)
              .equals(value.getDeclarativeShadowVariableValue()),
          "Invalid basic declarative shadow.");
    }
    return SimpleScore.of(-penalty);
  }

  @Override
  public String state(TestdataMixedSolution solution) {
    var state = new StringBuilder();
    for (var entity : solution.getEntityList()) {
      state
          .append(entity.getCode())
          .append('=')
          .append(entity.getBasicValue().getCode())
          .append('/')
          .append(entity.getSecondBasicValue().getCode())
          .append(";list=");
      for (var value : entity.getValueList()) {
        state.append(value.getCode()).append('@').append(value.getIndex()).append(',');
      }
      state.append('\n');
    }
    return state.toString();
  }

  private static int id(String code) {
    return Integer.parseInt(code.substring(code.lastIndexOf(' ') + 1));
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}
