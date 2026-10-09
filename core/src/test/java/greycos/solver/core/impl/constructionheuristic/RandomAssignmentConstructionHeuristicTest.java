package greycos.solver.core.impl.constructionheuristic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedOtherValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;
import greycos.solver.core.testcotwin.pinned.unassignedvar.TestdataPinnedAllowsUnassignedEntity;
import greycos.solver.core.testcotwin.pinned.unassignedvar.TestdataPinnedAllowsUnassignedSolution;
import greycos.solver.core.testcotwin.score.TestdataHardSoftScoreSolution;
import greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingEntity;
import greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Public-solver coverage for random construction, independent replay and phase handoff. */
@Timeout(30)
class RandomAssignmentConstructionHeuristicTest {

  @ParameterizedTest
  @EnumSource(
      value = EnvironmentMode.class,
      names = {"NO_ASSERT", "FULL_ASSERT"})
  void structuralFailureRestoresAssignmentsAndShadowsWithoutRetrying(EnvironmentMode environment) {
    var input = new CycleSolution();
    input.entities = List.of(new CycleEntity());
    // The only required value is the entity itself, so the sampled dependency must be cyclic.
    var solver =
        RandomAssignmentConstructionHeuristicTest.<CycleSolution>solver(
            config(CycleSolution.class, CycleConstraints.class, CycleEntity.class)
                .withEnvironmentMode(environment));
    var completed = completedSteps(solver);
    var started = new AtomicInteger();
    var publications = new AtomicInteger();
    var working = new AtomicReference<CycleSolution>();
    solver.addEventListener(
        event -> {
          if (event.getProducerId().equals(EventProducerId.constructionHeuristic(0))) {
            publications.incrementAndGet();
          }
        });
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<CycleSolution> phase) {
            working.set(phase.getWorkingSolution());
            assertThat(working.get().entities.getFirst().depth).isZero();
          }

          @Override
          public void stepStarted(AbstractStepScope<CycleSolution> step) {
            started.incrementAndGet();
          }
        });

    assertThatThrownBy(() -> solver.solve(input))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("structurally invalid assignment")
        .hasMessageContaining("structurallyFlawed (true)")
        .hasMessageContaining("phase (0), step (0)")
        .hasMessageContaining("original assignments were restored")
        .hasMessageContaining("No retries");

    assertThat(started).hasValue(1);
    assertThat(completed).isEmpty();
    assertThat(publications).hasValue(0);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(1L);
    assertThat(solver.isSolving()).isFalse();
    assertThat(working.get().entities.getFirst().previous).isNull();
    assertThat(working.get().entities.getFirst().depth).isZero();
    assertThat(working.get().score).isEqualTo(SimpleScore.ZERO);
    assertThat(solver.getSolverScope().getBestSolution().entities.getFirst().previous).isNull();
    assertThat(input.entities.getFirst().previous).isNull();
    assertThat(input.entities.getFirst().depth).isNull();
  }

  @Test
  void easyScoreBackendConstructsAndReplaysTheSameRandomBasicAssignments() {
    var config =
        config(TestdataSolution.class, BasicConstraints.class, TestdataEntity.class)
            .withScoreDirectorFactory(
                new ScoreDirectorFactoryConfig()
                    .withEasyScoreCalculatorClass(BasicEasyCalculator.class));
    var solver = RandomAssignmentConstructionHeuristicTest.<TestdataSolution>solver(config);
    var steps = completedSteps(solver);

    var result = solver.solve(basicProblem(8, 24, false));
    var bavetResult =
        RandomAssignmentConstructionHeuristicTest.<TestdataSolution>solver(
                config(TestdataSolution.class, BasicConstraints.class, TestdataEntity.class))
            .solve(basicProblem(8, 24, false));

    assertThat(steps).containsExactly(0);
    assertBasicReplay(result);
    assertThat(basicAssignments(result)).isEqualTo(basicAssignments(bavetResult));
    assertThat(result.getScore()).isEqualTo(bavetResult.getScore());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void basicConstructionReplaysReproducesAndReplacesWarmAssignments(boolean warm) {
    var config = config(TestdataSolution.class, BasicConstraints.class, TestdataEntity.class);
    var solver = RandomAssignmentConstructionHeuristicTest.<TestdataSolution>solver(config);
    var steps = completedSteps(solver);
    var input = basicProblem(8, 24, warm);
    var inputAssignments = basicAssignments(input);

    var first = solver.solve(input);
    var second =
        RandomAssignmentConstructionHeuristicTest.<TestdataSolution>solver(config)
            .solve(basicProblem(8, 24, warm));

    assertThat(steps).containsExactly(0);
    assertThat(basicAssignments(first)).isEqualTo(basicAssignments(second));
    assertThat(basicAssignments(input)).isEqualTo(inputAssignments);
    assertBasicReplay(first);
    assertBasicReplay(second);
    if (warm) {
      assertThat(basicAssignments(first)).isNotEqualTo(inputAssignments);
      // The warm input uses the best value everywhere. Random construction is not score selection.
      assertThat(first.getScore()).isLessThan(SimpleScore.ZERO);
    }
  }

  @Test
  void singletonWarmInputIsOneValidUnchangedConstructionStep() {
    var solver =
        RandomAssignmentConstructionHeuristicTest.<TestdataSolution>solver(
            config(TestdataSolution.class, BasicConstraints.class, TestdataEntity.class));
    var steps = completedSteps(solver);

    var result = solver.solve(basicProblem(1, 5, true));

    assertThat(steps).containsExactly(0);
    assertThat(basicAssignments(result)).containsOnly("0");
    assertBasicReplay(result);
  }

  @Test
  void equalScoreWarmAssignmentsAreReplacedEvenWhenEveryCandidateIsHardInfeasible() {
    var input = TestdataHardSoftScoreSolution.generateSolution(4, 24);
    var original =
        input.getEntityList().stream().map(entity -> entity.getValue().getCode()).toList();
    var solver =
        RandomAssignmentConstructionHeuristicTest.<TestdataHardSoftScoreSolution>solver(
            config(
                TestdataHardSoftScoreSolution.class, HardConstraints.class, TestdataEntity.class));
    var steps = completedSteps(solver);

    var result = solver.solve(input);

    assertThat(steps).containsExactly(0);
    assertThat(result.getEntityList().stream().map(entity -> entity.getValue().getCode()).toList())
        .isNotEqualTo(original);
    assertThat(result.getScore()).isEqualTo(HardSoftScore.of(-24, 0));
    assertThat(result.getScore().isFeasible()).isFalse();
    assertThat(input.getEntityList().stream().map(entity -> entity.getValue().getCode()).toList())
        .isEqualTo(original);
  }

  @Test
  void optionalBasicsIncludeNullAndPreservePinnedWarmAssignments() {
    var input = TestdataPinnedAllowsUnassignedSolution.generateSolution(2, 32);
    input.getEntityList().getFirst().setPinned(true);
    var pinnedCode = input.getEntityList().getFirst().getValue().getCode();
    var solver =
        RandomAssignmentConstructionHeuristicTest.<TestdataPinnedAllowsUnassignedSolution>solver(
            config(
                TestdataPinnedAllowsUnassignedSolution.class,
                OptionalBasicConstraints.class,
                TestdataPinnedAllowsUnassignedEntity.class));

    var result = solver.solve(input);

    assertThat(result.getEntityList().getFirst().getValue().getCode()).isEqualTo(pinnedCode);
    assertThat(result.getEntityList()).anyMatch(entity -> entity.getValue() == null);
    assertThat(result.getEntityList().stream().skip(1))
        .anyMatch(entity -> entity.getValue() != null);
    int assigned = 0;
    for (var entity : result.getEntityList()) {
      if (entity.getValue() != null) {
        assertThat(result.getValueList()).anyMatch(value -> value == entity.getValue());
        assigned++;
      }
    }
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(-assigned));
    assertThat(input.getEntityList()).allMatch(entity -> entity.getValue() != null);
  }

  @Test
  void everyBasicAssignmentComesFromItsEntitySpecificRange() {
    var input = new TestdataEntityProvidingSolution("ranges");
    var entities = new ArrayList<TestdataEntityProvidingEntity>();
    for (int i = 0; i < 12; i++) {
      var values =
          List.of(
              new TestdataValue(Integer.toString(2 * i)),
              new TestdataValue(Integer.toString(2 * i + 1)));
      entities.add(
          new TestdataEntityProvidingEntity(
              "entity-" + i, values, i % 2 == 0 ? values.getFirst() : null));
    }
    input.setEntityList(entities);
    var solver =
        RandomAssignmentConstructionHeuristicTest.<TestdataEntityProvidingSolution>solver(
            config(
                TestdataEntityProvidingSolution.class,
                EntityRangeConstraints.class,
                TestdataEntityProvidingEntity.class));

    var result = solver.solve(input);

    int total = 0;
    for (var entity : result.getEntityList()) {
      assertThat(entity.getValue()).isNotNull();
      assertThat(entity.getValueRange()).anyMatch(value -> value == entity.getValue());
      total += Integer.parseInt(entity.getValue().getCode());
    }
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(-total));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void requiredListsAssignEveryValueOnceAndReplayInverseAndIndexShadows(boolean warm) {
    var input =
        warm
            ? TestdataListSolution.generateInitializedSolution(24, 4)
            : TestdataListSolution.generateUninitializedSolution(24, 4);
    var original = listAssignments(input);
    var solver =
        RandomAssignmentConstructionHeuristicTest.<TestdataListSolution>solver(
            config(
                TestdataListSolution.class,
                ListConstraints.class,
                TestdataListEntity.class,
                TestdataListValue.class));
    var steps = completedSteps(solver);

    var result = solver.solve(input);

    assertThat(steps).containsExactly(0);
    var visited = identitySet();
    int penalty = 0;
    for (var entity : result.getEntityList()) {
      for (int index = 0; index < entity.getValueList().size(); index++) {
        var value = entity.getValueList().get(index);
        assertThat(visited.add(value)).isTrue();
        assertThat(result.getValueList()).anyMatch(candidate -> candidate == value);
        assertThat(value.getEntity()).isSameAs(entity);
        assertThat(value.getIndex()).isEqualTo(index);
        penalty += index + 1;
      }
    }
    assertThat(visited).containsExactlyInAnyOrderElementsOf(result.getValueList());
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(-penalty));
    assertThat(listAssignments(input)).isEqualTo(original);
    if (warm) assertThat(listAssignments(result)).isNotEqualTo(original);
  }

  @Test
  void optionalListMembershipPreservesPinnedPrefixAndClearsUnassignedShadows() {
    var input = new TestdataPinnedUnassignedValuesListSolution();
    var values =
        IntStream.range(0, 32)
            .mapToObj(i -> new TestdataPinnedUnassignedValuesListValue("value-" + i))
            .toList();
    var first =
        TestdataPinnedUnassignedValuesListEntity.createWithValues(
            "first", values.toArray(TestdataPinnedUnassignedValuesListValue[]::new));
    first.setPlanningPinToIndex(2);
    input.setValueList(values);
    input.setEntityList(
        List.of(
            first,
            new TestdataPinnedUnassignedValuesListEntity("second"),
            new TestdataPinnedUnassignedValuesListEntity("third")));
    var solver =
        RandomAssignmentConstructionHeuristicTest
            .<TestdataPinnedUnassignedValuesListSolution>solver(
                config(
                    TestdataPinnedUnassignedValuesListSolution.class,
                    OptionalListConstraints.class,
                    TestdataPinnedUnassignedValuesListEntity.class,
                    TestdataPinnedUnassignedValuesListValue.class));

    var result = solver.solve(input);

    assertThat(result.getEntityList().getFirst().getValueList().subList(0, 2))
        .extracting(TestdataPinnedUnassignedValuesListValue::getCode)
        .containsExactly("value-0", "value-1");
    var visited = identitySet();
    int penalty = 0;
    for (var entity : result.getEntityList()) {
      var row = entity.getValueList();
      for (int i = 0; i < row.size(); i++) {
        var value = row.get(i);
        assertThat(visited.add(value)).isTrue();
        assertThat(value.getEntity()).isSameAs(entity);
        assertThat(value.getIndex()).isEqualTo(i);
        assertThat(value.getPrevious()).isSameAs(i == 0 ? null : row.get(i - 1));
        assertThat(value.getNext()).isSameAs(i + 1 == row.size() ? null : row.get(i + 1));
        penalty += i + 1;
      }
    }
    assertThat(visited.size()).isLessThan(values.size());
    for (var value : result.getValueList()) {
      if (!visited.contains(value)) {
        assertThat(value.getEntity()).isNull();
        assertThat(value.getIndex()).isNull();
        assertThat(value.getPrevious()).isNull();
        assertThat(value.getNext()).isNull();
        penalty += 100;
      }
    }
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(-penalty));
    assertThat(input.getEntityList().getFirst().getValueList()).hasSize(32);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void mixedConstructionPreservesPinsAndReplaysDeclarativeAndCascadingShadows(boolean warm) {
    var input = TestdataMixedSolution.generateUninitializedSolution(4, 20, 4);
    if (warm) {
      for (var entity : input.getEntityList()) {
        entity.setBasicValue(input.getOtherValueList().getFirst());
        entity.setSecondBasicValue(input.getOtherValueList().getFirst());
      }
      input.getEntityList().getFirst().getValueList().addAll(input.getValueList());
    }
    var pinned = input.getEntityList().get(3);
    pinned.setBasicValue(input.getOtherValueList().get(1));
    pinned.setSecondBasicValue(input.getOtherValueList().get(2));
    pinned.setPinned(true);
    if (warm) input.getEntityList().getFirst().setPinnedIndex(2);
    SolutionManager.updateShadowVariables(input);
    var solver =
        RandomAssignmentConstructionHeuristicTest.<TestdataMixedSolution>solver(
            config(
                TestdataMixedSolution.class,
                MixedConstraints.class,
                TestdataMixedEntity.class,
                TestdataMixedValue.class,
                TestdataMixedOtherValue.class));

    var result = solver.solve(input);

    assertThat(result.getEntityList().get(3).getBasicValue().getCode())
        .isEqualTo(pinned.getBasicValue().getCode());
    assertThat(result.getEntityList().get(3).getSecondBasicValue().getCode())
        .isEqualTo(pinned.getSecondBasicValue().getCode());
    assertThat(result.getEntityList().get(3).getValueList()).isEmpty();
    if (warm) {
      assertThat(result.getEntityList().getFirst().getValueList().subList(0, 2))
          .extracting(TestdataMixedValue::getCode)
          .containsExactly(
              input.getValueList().get(0).getCode(), input.getValueList().get(1).getCode());
    }
    var visited = identitySet();
    int penalty = 0;
    for (var entity : result.getEntityList()) {
      assertThat(entity.getBasicValue()).isNotNull();
      assertThat(entity.getSecondBasicValue()).isNotNull();
      assertThat(entity.getDeclarativeShadowVariableValue())
          .isEqualTo(entity.getBasicValue().getStrength());
      penalty += entity.getBasicValue().getStrength() + entity.getSecondBasicValue().getStrength();
      var row = entity.getValueList();
      for (int i = 0; i < row.size(); i++) {
        var value = row.get(i);
        assertThat(visited.add(value)).isTrue();
        assertThat(value.getEntity()).isSameAs(entity);
        assertThat(value.getIndex()).isEqualTo(i);
        assertThat(value.getPreviousElement()).isSameAs(i == 0 ? null : row.get(i - 1));
        assertThat(value.getNextElement()).isSameAs(i + 1 == row.size() ? null : row.get(i + 1));
        assertThat(value.getCascadingShadowVariableValue()).isEqualTo(i + 1);
        assertThat(value.getDeclarativeShadowVariableValue()).isEqualTo(i + 2);
        penalty += i + 1;
      }
    }
    assertThat(visited).containsExactlyInAnyOrderElementsOf(result.getValueList());
    for (var value : result.getOtherValueList()) {
      var expected =
          result.getEntityList().stream()
              .filter(entity -> entity.getBasicValue() == value)
              .toList();
      assertThat(value.getEntityList()).containsExactlyInAnyOrderElementsOf(expected);
      assertThat(value.getDeclarativeShadowVariableValue()).isEqualTo(expected.size() + 2);
    }
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(-penalty));
  }

  @Test
  void geneticAlgorithmStillSeedsTheRemainingPopulationAfterNormalPhaseHandoff() {
    var config =
        config(TestdataSolution.class, BasicConstraints.class, TestdataEntity.class)
            .withPhases(
                randomConstruction(),
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_NONE)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(9)));
    var solver = RandomAssignmentConstructionHeuristicTest.<TestdataSolution>solver(config);
    var phaseIds = new ArrayList<EventProducerId>();
    var seeds = new AtomicInteger();
    var offspring = new AtomicInteger();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> phase) {
            phaseIds.add(phase.getPhaseId());
            if (phase.getPhaseIndex() == 1) {
              assertBasicReplay(phase.getWorkingSolution());
              assertThat(phase.getWorkingSolution())
                  .isNotSameAs(phase.getSolverScope().getBestSolution());
            }
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> step) {
            if (step instanceof GeneticAlgorithmStepScope<?> ga) {
              if (ga.isSeeding()) seeds.incrementAndGet();
              else offspring.incrementAndGet();
            }
          }
        });

    assertBasicReplay(solver.solve(basicProblem(8, 24, false)));

    assertThat(phaseIds)
        .containsExactly(
            EventProducerId.constructionHeuristic(0), EventProducerId.geneticAlgorithm(1));
    assertThat(seeds).hasValue(4);
    assertThat(offspring).hasValue(5);
  }

  @Test
  void reuseAndProblemChangeRebuildRandomAssignmentsAgainstCurrentFacts() {
    var solver =
        RandomAssignmentConstructionHeuristicTest.<TestdataSolution>solver(
            config(TestdataSolution.class, BasicConstraints.class, TestdataEntity.class));
    var queued = new AtomicBoolean();
    var sizes = new ArrayList<Integer>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> step) {
            sizes.add(step.getWorkingSolution().getEntityList().size());
            if (queued.compareAndSet(false, true)) {
              solver.addProblemChange(
                  (solution, director) -> {
                    solution.setValueList(new ArrayList<>(solution.getValueList()));
                    solution.setEntityList(new ArrayList<>(solution.getEntityList()));
                    var newValue = new TestdataValue("9");
                    director.addProblemFact(newValue, solution.getValueList()::add);
                    director.addEntity(
                        new TestdataEntity("added", newValue), solution.getEntityList()::add);
                  });
            }
          }
        });

    var changed = solver.solve(basicProblem(3, 5, false));
    assertThat(changed.getEntityList()).hasSize(6);
    assertThat(sizes).containsExactly(5, 6);
    assertBasicReplay(changed);

    var reused = solver.solve(basicProblem(1, 2, false));
    assertThat(sizes).containsExactly(5, 6, 2);
    assertThat(basicAssignments(reused)).containsExactly("0", "0");
    assertBasicReplay(reused);
  }

  @Test
  void cancellationBeforeThePhaseLeavesWarmInputIntactAndSolverCanBeReused() {
    var solver =
        RandomAssignmentConstructionHeuristicTest.<TestdataSolution>solver(
            config(TestdataSolution.class, BasicConstraints.class, TestdataEntity.class));
    var cancel = new AtomicBoolean(true);
    var steps = completedSteps(solver);
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingStarted(
              greycos.solver.core.impl.solver.scope.SolverScope<TestdataSolution> scope) {
            if (cancel.getAndSet(false)) solver.terminateEarly();
          }
        });

    var cancelled = solver.solve(basicProblem(8, 24, true));
    assertThat(steps).isEmpty();
    assertThat(basicAssignments(cancelled)).containsOnly("0");
    assertBasicReplay(cancelled);
    assertBasicReplay(solver.solve(basicProblem(8, 24, false)));
    assertThat(steps).containsExactly(0);
  }

  private static ConstructionHeuristicPhaseConfig randomConstruction() {
    return new ConstructionHeuristicPhaseConfig()
        .withConstructionHeuristicType(ConstructionHeuristicType.RANDOM_ASSIGNMENT)
        .withMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_NONE);
  }

  private static SolverConfig config(
      Class<?> solution, Class<? extends ConstraintProvider> constraints, Class<?>... entities) {
    return new SolverConfig()
        .withSolutionClass(solution)
        .withEntityClasses(entities)
        .withConstraintProviderClass(constraints)
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        .withRandomSeed(37L)
        .withMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_NONE)
        .withPhases(randomConstruction());
  }

  private static <Solution_> DefaultSolver<Solution_> solver(SolverConfig config) {
    return (DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver();
  }

  private static <Solution_> List<Integer> completedSteps(DefaultSolver<Solution_> solver) {
    var steps = new ArrayList<Integer>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<Solution_> step) {
            steps.add(step.getStepIndex());
          }
        });
    return steps;
  }

  private static TestdataSolution basicProblem(int valueCount, int entityCount, boolean warm) {
    var problem = new TestdataSolution("random-construction");
    var values =
        IntStream.range(0, valueCount)
            .mapToObj(i -> new TestdataValue(Integer.toString(i)))
            .toList();
    problem.setValueList(values);
    problem.setEntityList(
        IntStream.range(0, entityCount)
            .mapToObj(i -> new TestdataEntity("entity-" + i, warm ? values.getFirst() : null))
            .toList());
    return problem;
  }

  private static List<String> basicAssignments(TestdataSolution solution) {
    return solution.getEntityList().stream()
        .map(entity -> entity.getValue() == null ? null : entity.getValue().getCode())
        .toList();
  }

  private static void assertBasicReplay(TestdataSolution solution) {
    int total = 0;
    for (var entity : solution.getEntityList()) {
      assertThat(entity.getValue()).isNotNull();
      assertThat(solution.getValueList()).anyMatch(value -> value == entity.getValue());
      total += Integer.parseInt(entity.getValue().getCode());
    }
    assertThat(solution.getScore()).isEqualTo(SimpleScore.of(-total));
  }

  private static List<List<String>> listAssignments(TestdataListSolution solution) {
    return solution.getEntityList().stream()
        .map(entity -> entity.getValueList().stream().map(TestdataListValue::getCode).toList())
        .toList();
  }

  private static Set<Object> identitySet() {
    return Collections.newSetFromMap(new IdentityHashMap<>());
  }

  public static final class BasicConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .penalize(SimpleScore.ONE, entity -> Integer.parseInt(entity.getValue().getCode()))
            .asConstraint("Assigned value")
      };
    }
  }

  public static final class BasicEasyCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      int total = 0;
      for (var entity : solution.getEntityList()) {
        if (entity.getValue() != null) total += Integer.parseInt(entity.getValue().getCode());
      }
      return SimpleScore.of(-total);
    }
  }

  @PlanningSolution
  public static class CycleSolution {
    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "cycles")
    public List<CycleEntity> entities;

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class CycleEntity {
    @PlanningVariable(valueRangeProviderRefs = "cycles")
    public CycleEntity previous;

    @ShadowVariable(supplierName = "calculateDepth")
    public Integer depth;

    @ShadowSources("previous.depth")
    public Integer calculateDepth() {
      return previous == null ? 0 : previous.depth == null ? null : previous.depth + 1;
    }
  }

  public static final class CycleConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEachIncludingUnassigned(CycleEntity.class)
            .penalize(SimpleScore.ONE, entity -> entity.depth == null ? 0 : entity.depth)
            .asConstraint("Dependency depth")
      };
    }
  }

  public static final class OptionalBasicConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataPinnedAllowsUnassignedEntity.class)
            .penalize(SimpleScore.ONE)
            .asConstraint("Assigned values")
      };
    }
  }

  public static final class HardConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("Unavoidable hard penalty")
      };
    }
  }

  public static final class EntityRangeConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntityProvidingEntity.class)
            .penalize(SimpleScore.ONE, entity -> Integer.parseInt(entity.getValue().getCode()))
            .asConstraint("Assigned value")
      };
    }
  }

  public static final class ListConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataListValue.class)
            .penalize(SimpleScore.ONE, value -> value.getIndex() + 1)
            .asConstraint("Positions")
      };
    }
  }

  public static final class OptionalListConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEachIncludingUnassigned(TestdataPinnedUnassignedValuesListValue.class)
            .penalize(
                SimpleScore.ONE, value -> value.getEntity() == null ? 100 : value.getIndex() + 1)
            .asConstraint("Positions and unassigned")
      };
    }
  }

  public static final class MixedConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataMixedEntity.class)
            .penalize(
                SimpleScore.ONE,
                entity ->
                    entity.getBasicValue().getStrength()
                        + entity.getSecondBasicValue().getStrength())
            .asConstraint("Basic assignments"),
        factory
            .forEach(TestdataMixedValue.class)
            .penalize(SimpleScore.ONE, value -> value.getIndex() + 1)
            .asConstraint("Positions")
      };
    }
  }
}
