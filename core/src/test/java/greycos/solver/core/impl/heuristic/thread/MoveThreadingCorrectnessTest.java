package greycos.solver.core.impl.heuristic.thread;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicType;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingEntity;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingSolution;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingValue;
import greycos.solver.core.testcotwin.pinned.unassignedvar.TestdataPinnedAllowsUnassignedEntity;
import greycos.solver.core.testcotwin.pinned.unassignedvar.TestdataPinnedAllowsUnassignedSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MoveThreadingCorrectnessTest {

  private static final int STEP_LIMIT = 60;

  @Test
  @Timeout(60)
  void allowsUnassignedWithPinnedNullEntity() {
    var problem = new TestdataPinnedAllowsUnassignedSolution("optional-assignment");
    problem.setValueList(
        List.of(new TestdataValue("v0"), new TestdataValue("v1"), new TestdataValue("v2")));
    var entities = new ArrayList<TestdataPinnedAllowsUnassignedEntity>();
    entities.add(new TestdataPinnedAllowsUnassignedEntity("e0", null, false, true));
    for (int i = 1; i <= 8; i++) {
      entities.add(
          new TestdataPinnedAllowsUnassignedEntity(
              "e" + i, i == 3 ? null : problem.getValueList().get((i + 1) % 3)));
    }
    problem.setEntityList(entities);
    var initialScore = validateOptionalAssignments(problem);
    var phase = focusedLocalSearch().withMoveSelectorConfig(new ChangeMoveSelectorConfig());
    var config =
        focusedConfig(
            TestdataPinnedAllowsUnassignedSolution.class,
            AllowsUnassignedConstraints.class,
            phase,
            TestdataPinnedAllowsUnassignedEntity.class);
    var solver =
        (DefaultSolver<TestdataPinnedAllowsUnassignedSolution>)
            SolverFactory.<TestdataPinnedAllowsUnassignedSolution>create(config).buildSolver();
    var checkedSteps = new ArrayList<SimpleScore>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(
              AbstractStepScope<TestdataPinnedAllowsUnassignedSolution> stepScope) {
            var expected = validateOptionalAssignments(stepScope.getWorkingSolution());
            assertThat(stepScope.getScore().unassignedCount()).isZero();
            assertThat(stepScope.getScore().raw()).isEqualTo(expected);
            checkedSteps.add(expected);
          }
        });
    var solution = solver.solve(problem);
    assertThat(checkedSteps).hasSize(STEP_LIMIT);
    assertThat(solution.getScore())
        .isEqualTo(validateOptionalAssignments(solution))
        .isGreaterThan(initialScore);
    assertThat(solver.getSolverScope().getBestScore().unassignedCount()).isZero();
  }

  private static SimpleScore validateOptionalAssignments(
      TestdataPinnedAllowsUnassignedSolution solution) {
    assertThat(solution.getEntityList()).hasSize(9);
    var pinned = solution.getEntityList().get(0);
    assertThat(pinned.getCode()).isEqualTo("e0");
    assertThat(pinned.isPinned()).isTrue();
    assertThat(pinned.getValue()).isNull();
    long penalty = 0;
    for (var entity : solution.getEntityList()) {
      var value = entity.getValue();
      if (value == null) {
        penalty += 100;
      } else {
        assertThat(solution.getValueList()).contains(value);
        int expected = Integer.parseInt(entity.getCode().substring(1)) % 3;
        int actual = Integer.parseInt(value.getCode().substring(1));
        penalty += 1 + Math.abs(expected - actual);
      }
    }
    return SimpleScore.of(-penalty);
  }

  public static class AllowsUnassignedConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEachIncludingUnassigned(TestdataPinnedAllowsUnassignedEntity.class)
            .filter(entity -> entity.getValue() == null)
            .penalize(SimpleScore.of(100))
            .asConstraint("Unassigned entities"),
        factory
            .forEach(TestdataPinnedAllowsUnassignedEntity.class)
            .penalize(
                SimpleScore.ONE,
                entity ->
                    1
                        + Math.abs(
                            Integer.parseInt(entity.getCode().substring(1)) % 3
                                - Integer.parseInt(entity.getValue().getCode().substring(1))))
            .asConstraint("Assignment preference")
      };
    }
  }

  @Test
  @Timeout(60)
  void listMovesRespectEntitySpecificRanges() {
    var values = new ArrayList<TestdataListEntityProvidingValue>();
    for (int i = 0; i < 6; i++) {
      values.add(new TestdataListEntityProvidingValue("v" + i));
    }
    var first =
        new TestdataListEntityProvidingEntity(
            "e0", new ArrayList<>(values.subList(0, 4)), new ArrayList<>(values.subList(0, 4)));
    var second =
        new TestdataListEntityProvidingEntity(
            "e1", new ArrayList<>(values.subList(2, 6)), new ArrayList<>(values.subList(4, 6)));
    var problem = new TestdataListEntityProvidingSolution();
    problem.setEntityList(List.of(first, second));
    var initialScore = validateEntityRanges(problem);
    var phase =
        focusedLocalSearch()
            .withMoveSelectorConfig(
                new UnionMoveSelectorConfig()
                    .withMoveSelectors(
                        new ListChangeMoveSelectorConfig(), new ListSwapMoveSelectorConfig()));
    var config =
        focusedConfig(
            TestdataListEntityProvidingSolution.class,
            EntityRangeConstraints.class,
            phase,
            TestdataListEntityProvidingEntity.class,
            TestdataListEntityProvidingValue.class);
    var solver =
        (DefaultSolver<TestdataListEntityProvidingSolution>)
            SolverFactory.<TestdataListEntityProvidingSolution>create(config).buildSolver();
    var checkedSteps = new ArrayList<SimpleScore>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataListEntityProvidingSolution> stepScope) {
            var expected = validateEntityRanges(stepScope.getWorkingSolution());
            assertThat(stepScope.getScore().unassignedCount()).isZero();
            assertThat(stepScope.getScore().raw()).isEqualTo(expected);
            checkedSteps.add(expected);
          }
        });
    var solution = solver.solve(problem);
    assertThat(checkedSteps).hasSize(STEP_LIMIT);
    assertThat(solution.getScore())
        .isEqualTo(validateEntityRanges(solution))
        .isGreaterThan(initialScore);
  }

  private static SimpleScore validateEntityRanges(TestdataListEntityProvidingSolution solution) {
    var seen = new HashSet<TestdataListEntityProvidingValue>();
    long penalty = 0;
    for (var entity : solution.getEntityList()) {
      long size = entity.getValueList().size();
      penalty += size * size;
      for (int index = 0; index < size; index++) {
        var value = entity.getValueList().get(index);
        assertThat(entity.getValueRange()).contains(value);
        assertThat(seen.add(value)).isTrue();
        assertThat(value.getEntity()).isSameAs(entity);
        assertThat(value.getIndex()).isEqualTo(index);
      }
    }
    assertThat(seen).containsExactlyInAnyOrderElementsOf(solution.getValueList()).hasSize(6);
    return SimpleScore.of(-penalty);
  }

  public static class EntityRangeConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataListEntityProvidingEntity.class)
            .penalize(
                SimpleScore.ONE,
                entity -> (long) entity.getValueList().size() * entity.getValueList().size())
            .asConstraint("Squared entity load")
      };
    }
  }

  private static LocalSearchPhaseConfig focusedLocalSearch() {
    return new LocalSearchPhaseConfig()
        .withAcceptorConfig(new LocalSearchAcceptorConfig().withLateAcceptanceSize(64))
        .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(16))
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(STEP_LIMIT));
  }

  private static SolverConfig focusedConfig(
      Class<?> solutionClass,
      Class<? extends ConstraintProvider> providerClass,
      LocalSearchPhaseConfig phase,
      Class<?>... entityClasses) {
    return new SolverConfig()
        .withSolutionClass(solutionClass)
        .withEntityClasses(entityClasses)
        .withConstraintProviderClass(providerClass)
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        .withRandomSeed(37L)
        .withMoveThreadCount("8")
        .withMoveThreadBufferSize(1)
        .withPhases(phase);
  }

  @ParameterizedTest
  @CsvSource({
    "basic,NONE,1,10",
    "basic,1,16,10",
    "basic,8,1,1",
    "basic,8,16,10",
    "list,NONE,1,10",
    "list,1,16,10",
    "list,8,1,1",
    "list,8,16,10"
  })
  @Timeout(60)
  void reproducesAssignmentsSelectedStepsAndScores(
      String workloadName, String threads, int acceptedCount, int bufferSize) {
    var workload = MoveThreadingWorkload.named(workloadName);
    var first = solve(workload, threads, acceptedCount, bufferSize);
    var second = solve(workload, threads, acceptedCount, bufferSize);

    assertThat(first.steps()).hasSize(STEP_LIMIT);
    assertThat(first).isEqualTo(second);
    assertThat(first.finalScore()).isGreaterThan(first.initialScore());
    assertThat(first.usefulMoves()).isGreaterThanOrEqualTo(STEP_LIMIT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "8"})
  @Timeout(60)
  void constructsPartiallyAssignedBasicProblemWithPinnedJob(String threads) {
    var workload = new MoveThreadingWorkload.BasicWorkload();
    Supplier<MoveThreadingWorkload.BasicSolution> partial =
        () -> {
          var solution = workload.createProblem(32);
          solution.getJobs().get(0).setPinned(true);
          for (var job : solution.getJobs()) {
            if (job.getId() % 2 == 1) {
              job.setMachine(null);
            }
          }
          solution.setScore(null);
          return solution;
        };
    ToIntFunction<MoveThreadingWorkload.BasicSolution> unassigned =
        solution -> {
          assertThat(solution.getJobs().get(0).getMachine().id()).isZero();
          return (int) solution.getJobs().stream().filter(job -> job.getMachine() == null).count();
        };
    assertThat(solvePartial(workload, partial, unassigned, threads))
        .isEqualTo(solvePartial(workload, partial, unassigned, threads));
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "8"})
  @Timeout(60)
  void constructsPartiallyAssignedListProblemWithCascadingShadows(String threads) {
    var workload = new MoveThreadingWorkload.ListWorkload();
    Supplier<MoveThreadingWorkload.ListSolution> partial =
        () -> {
          var solution = workload.createProblem(32);
          for (var route : solution.getRoutes()) {
            route.getVisits().removeIf(visit -> visit.getId() % 2 == 1);
          }
          for (var visit : solution.getVisits()) {
            if (visit.getId() % 2 == 1) {
              visit.setRoute(null);
              visit.setIndex(null);
              visit.setPrevious(null);
              visit.setNext(null);
              visit.setCompletion(null);
            }
          }
          SolutionManager.updateShadowVariables(solution);
          solution.setScore(null);
          return solution;
        };
    ToIntFunction<MoveThreadingWorkload.ListSolution> unassigned =
        solution ->
            (int) solution.getVisits().stream().filter(visit -> visit.getRoute() == null).count();
    assertThat(solvePartial(workload, partial, unassigned, threads))
        .isEqualTo(solvePartial(workload, partial, unassigned, threads));
  }

  private <Solution_> List<String> solvePartial(
      Workload<Solution_> workload,
      Supplier<Solution_> partialProblem,
      ToIntFunction<Solution_> unassigned,
      String threads) {
    var problem = partialProblem.get();
    int initiallyUnassigned = unassigned.applyAsInt(problem);
    assertThat(initiallyUnassigned).isPositive();
    var config =
        workload.solverConfig(
            threads,
            37L,
            16,
            new TerminationConfig().withStepCountLimit(STEP_LIMIT),
            EnvironmentMode.FULL_ASSERT);
    var phases = new ArrayList<>(config.getPhaseConfigList());
    phases.add(
        0,
        new ConstructionHeuristicPhaseConfig()
            .withConstructionHeuristicType(ConstructionHeuristicType.FIRST_FIT));
    config.setPhaseConfigList(phases);
    var solver = (DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver();
    List<String> construction = new ArrayList<>();
    List<String> localSearch = new ArrayList<>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<Solution_> stepScope) {
            int missing = unassigned.applyAsInt(stepScope.getWorkingSolution());
            assertThat(stepScope.getScore().unassignedCount()).isEqualTo(missing);
            if (stepScope instanceof ConstructionHeuristicStepScope<Solution_> constructionStep) {
              construction.add(
                  constructionStep.getStepIndex()
                      + ":"
                      + constructionStep.getStep()
                      + ":"
                      + constructionStep.getScore()
                      + ":"
                      + constructionStep.getSelectedMoveCount()
                      + ":"
                      + MoveThreadingWorkload.fingerprint(
                          workload.state(stepScope.getWorkingSolution())));
            } else if (stepScope instanceof LocalSearchStepScope<Solution_> localStep) {
              assertThat(missing).isZero();
              assertThat(stepScope.getScore().raw())
                  .isEqualTo(workload.recompute(stepScope.getWorkingSolution()));
              localSearch.add(
                  localStep.getStepIndex()
                      + ":"
                      + localStep.getStep()
                      + ":"
                      + localStep.getScore()
                      + ":"
                      + localStep.getSelectedMoveCount()
                      + ":"
                      + localStep.getAcceptedMoveCount()
                      + ":"
                      + MoveThreadingWorkload.fingerprint(
                          workload.state(stepScope.getWorkingSolution())));
            }
          }
        });
    var solution = solver.solve(problem);
    assertThat(construction).hasSize(initiallyUnassigned);
    assertThat(localSearch).hasSize(STEP_LIMIT);
    assertThat(unassigned.applyAsInt(solution)).isZero();
    assertThat(workload.score(solution)).isEqualTo(workload.recompute(solution));
    construction.addAll(localSearch);
    construction.add(workload.score(solution) + ":" + workload.state(solution));
    return construction;
  }

  private <Solution_> Trace solve(
      Workload<Solution_> workload, String threads, int acceptedCount, int bufferSize) {
    var problem = workload.createProblem(32);
    var initialScore = workload.recompute(problem);
    var config =
        workload
            .solverConfig(
                threads,
                37L,
                acceptedCount,
                new TerminationConfig().withStepCountLimit(STEP_LIMIT),
                EnvironmentMode.FULL_ASSERT)
            .withMoveThreadBufferSize(bufferSize);
    var solver = (DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver();
    List<String> steps = new ArrayList<>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<Solution_> stepScope) {
            if (stepScope instanceof LocalSearchStepScope<Solution_> localStep) {
              var independentlyCalculated = workload.recompute(stepScope.getWorkingSolution());
              assertThat(stepScope.getScore().raw()).isEqualTo(independentlyCalculated);
              steps.add(
                  localStep.getStepIndex()
                      + ":"
                      + localStep.getStep()
                      + ":"
                      + independentlyCalculated
                      + ":"
                      + localStep.getSelectedMoveCount()
                      + ":"
                      + localStep.getAcceptedMoveCount()
                      + ":"
                      + MoveThreadingWorkload.fingerprint(
                          workload.state(stepScope.getWorkingSolution())));
            }
          }
        });
    var solution = solver.solve(problem);
    assertThat(workload.score(solution)).isEqualTo(workload.recompute(solution));
    return new Trace(
        steps,
        workload.state(solution),
        initialScore,
        workload.score(solution),
        solver.getMoveEvaluationCount());
  }

  private record Trace(
      List<String> steps,
      String finalState,
      SimpleScore initialScore,
      SimpleScore finalScore,
      long usefulMoves) {}
}
