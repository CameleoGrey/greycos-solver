package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assignments;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.solver;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.islandmodel.DefaultIslandModelPhase;
import greycos.solver.core.impl.islandmodel.IslandRunDiagnostics;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSample;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(45)
class GAIslandLifecycleIntegrationTest {

  @Test
  void twoByTwoNestedGaIslandsShareEachOuterIslandsCumulativeQuota() {
    var innerAfter = new AtomicInteger();
    var outerAfter = new AtomicInteger();
    var nested =
        island(
            2,
            3,
            ga(3),
            new CustomPhaseConfig()
                .withCustomPhaseCommands(context -> innerAfter.incrementAndGet()));
    var outer =
        island(
            2,
            8,
            ga(2),
            nested,
            new CustomPhaseConfig()
                .withCustomPhaseCommands(context -> outerAfter.incrementAndGet()));
    var solver =
        solver(config(ga(1)).withEnvironmentMode(EnvironmentMode.FULL_ASSERT).withPhases(outer));
    var result = solver.solve(problem(7, 8));

    assertReplay(result);
    assertThat(innerAfter).hasValue(0);
    assertThat(outerAfter).hasValue(0);
    var runs =
        ((DefaultIslandModelPhase<TestdataSolution>) solver.getPhaseList().getFirst())
            .getIslandDiagnostics();
    assertThat(runs).hasSize(6);
    assertThat(runs.stream().map(IslandRunDiagnostics::source).distinct()).hasSize(6);
    var parents = runs.stream().filter(run -> !run.source().contains("/phase-")).toList();
    assertThat(parents).hasSize(2);
    for (var parent : parents) {
      var children =
          runs.stream()
              .filter(run -> run.source().startsWith(parent.source() + "/phase-"))
              .toList();
      assertThat(parent.moveEvaluationCount()).isEqualTo(2L);
      assertThat(children)
          .hasSize(2)
          .allSatisfy(child -> assertThat(child.moveEvaluationCount()).isEqualTo(3L));
      assertThat(
              parent.moveEvaluationCount()
                  + children.stream().mapToLong(IslandRunDiagnostics::moveEvaluationCount).sum())
          .isEqualTo(8L);
    }
    assertThat(solver.getSolverScope().getReportedMoveEvaluationCount()).isEqualTo(16L);
    solver.getSolverScope().getWorkerRegistry().assertNoActiveWorkers();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void gaAndLocalSearchHandoffsKeepScoresAndOneCumulativeStepQuota(boolean gaFirst) {
    var handoffs = new ConcurrentHashMap<Thread, SimpleScore>();
    var forbiddenAfter = new AtomicInteger();
    PhaseCommand<TestdataSolution> checkHandoff =
        context -> {
          var solution = context.getWorkingSolution();
          assertReplay(solution);
          handoffs.put(Thread.currentThread(), solution.getScore());
        };
    var localSearch =
        new LocalSearchPhaseConfig()
            .withMoveThreadCount("NONE")
            .withLocalSearchType(LocalSearchType.LATE_ACCEPTANCE)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(3));
    PhaseConfig<?> first = gaFirst ? ga(3).withMigrationRate(0.25) : localSearch;
    PhaseConfig<?> second = gaFirst ? localSearch : ga(3).withMigrationRate(0.25);
    var outer =
        island(
            2,
            7,
            first,
            new CustomPhaseConfig().withCustomPhaseCommands(checkHandoff),
            second,
            new CustomPhaseConfig()
                .withCustomPhaseCommands(context -> forbiddenAfter.incrementAndGet()));
    var solver =
        solver(config(ga(1)).withEnvironmentMode(EnvironmentMode.FULL_ASSERT).withPhases(outer));
    var samples = new ArrayList<SolverMetricSample>();
    solver.getSolverScope().addMetricSampleListener(samples::add);

    var result = solver.solve(problem(17, 8));

    assertReplay(result);
    assertThat(handoffs).hasSize(2);
    assertThat(handoffs.values())
        .allSatisfy(score -> assertThat(result.getScore()).isGreaterThanOrEqualTo(score));
    assertThat(forbiddenAfter).hasValue(0);
    Map<String, List<SolverMetricSample>> steps =
        samples.stream()
            .filter(sample -> sample.kind() == SolverMetricSample.Kind.STEP)
            .filter(sample -> !sample.source().equals("root"))
            .collect(Collectors.groupingBy(SolverMetricSample::source));
    assertThat(steps).hasSize(2);
    for (var sourceSteps : steps.values()) {
      assertThat(sourceSteps).hasSize(7);
      var gaSteps =
          sourceSteps.stream()
              .filter(
                  sample ->
                      sample.moveType() != null
                          && sample.moveType().startsWith("GeneticAlgorithm/"))
              .toList();
      assertThat(gaSteps).hasSize(3);
      assertThat(sourceSteps.get(gaFirst ? 0 : 4).moveType()).startsWith("GeneticAlgorithm/");
    }
    solver.getSolverScope().getWorkerRegistry().assertNoActiveWorkers();
  }

  @Test
  void problemChangeRebuildsIslandGaStateAndOneSolverCanBeReusedOnAnotherThread() throws Exception {
    var outerConfig =
        island(2, 18, ga(18).withMigrationRate(0.25).withLocalImprovementMoveCountLimit(2L));
    var solver =
        solver(
            config(ga(1))
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                .withMonitoringConfig(
                    new MonitoringConfig()
                        .withSolverMetricList(List.of(SolverMetric.MOVE_COUNT_PER_TYPE)))
                .withPhases(outerConfig));
    var outer = (DefaultIslandModelPhase<TestdataSolution>) solver.getPhaseList().getFirst();
    var queued = new AtomicBoolean();
    var starts = new ArrayList<List<Integer>>();
    var globalStates = new ArrayList<Object>();
    var solveThreads = new ArrayList<Thread>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
            var solution = scope.getWorkingSolution();
            starts.add(List.of(solution.getEntityList().size(), solution.getValueList().size()));
            globalStates.add(outer.getGlobalState());
            solveThreads.add(Thread.currentThread());
          }
        });
    solver
        .getSolverScope()
        .addMetricSampleListener(
            sample -> {
              if (sample.kind() == SolverMetricSample.Kind.STEP
                  && sample.moveType() != null
                  && sample.moveType().startsWith("GeneticAlgorithm/")
                  && queued.compareAndSet(false, true)) {
                solver.addProblemChange(
                    (working, director) -> {
                      working.setEntityList(new ArrayList<>(working.getEntityList()));
                      working.setValueList(new ArrayList<>(working.getValueList()));
                      var value = new TestdataValue("99");
                      director.addProblemFact(value, working.getValueList()::add);
                      director.addEntity(
                          new TestdataEntity("added", value), working.getEntityList()::add);
                    });
              }
            });
    var input = problem(6, 8);
    TestdataSolution changedResult;
    try (var executor = Executors.newSingleThreadExecutor()) {
      changedResult = executor.submit(() -> solver.solve(input)).get(20, TimeUnit.SECONDS);
    }

    assertThat(queued).isTrue();
    assertThat(solver.isEveryProblemChangeProcessed()).isTrue();
    assertThat(starts).containsExactly(List.of(8, 6), List.of(9, 7));
    assertThat(globalStates.get(1)).isNotSameAs(globalStates.get(0));
    assertThat(changedResult.getEntityList()).extracting(TestdataEntity::getCode).contains("added");
    assertThat(changedResult.getValueList()).extracting(TestdataValue::getCode).contains("99");
    assertReplay(changedResult);
    assertThat(input.getEntityList()).hasSize(8);
    assertThat(input.getValueList()).hasSize(6);
    assertThat(solver.getSolverScope().getReportedMoveCountsByType().keySet())
        .anyMatch(type -> type.startsWith("GeneticAlgorithm/LOCAL_IMPROVEMENT/"));
    solver.getSolverScope().getWorkerRegistry().assertNoActiveWorkers();
    var retainedAssignments = assignments(changedResult);

    TestdataSolution reusedResult;
    try (var executor = Executors.newSingleThreadExecutor()) {
      reusedResult = executor.submit(() -> solver.solve(problem(4, 5))).get(20, TimeUnit.SECONDS);
    }

    assertThat(starts).containsExactly(List.of(8, 6), List.of(9, 7), List.of(5, 4));
    assertThat(globalStates.get(2))
        .isNotSameAs(globalStates.get(0))
        .isNotSameAs(globalStates.get(1));
    assertThat(solveThreads.get(2)).isNotSameAs(solveThreads.get(0));
    assertThat(solveThreads).allMatch(thread -> thread != Thread.currentThread());
    assertThat(reusedResult.getEntityList())
        .hasSize(5)
        .extracting(TestdataEntity::getCode)
        .doesNotContain("added");
    assertThat(reusedResult.getValueList())
        .hasSize(4)
        .extracting(TestdataValue::getCode)
        .doesNotContain("99");
    assertThat(assignments(changedResult)).containsExactlyElementsOf(retainedAssignments);
    assertReplay(reusedResult);
    assertThat(solver.isSolving()).isFalse();
    solver.getSolverScope().getWorkerRegistry().assertNoActiveWorkers();
  }

  private static GeneticAlgorithmPhaseConfig ga(int steps) {
    return new GeneticAlgorithmPhaseConfig()
        .withPopulationSize(2)
        .withMoveThreadCount("NONE")
        .withMigrationRate(0.0)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(steps));
  }

  private static IslandModelPhaseConfig island(int count, int steps, PhaseConfig<?>... phases) {
    return new IslandModelPhaseConfig()
        .withIslandCount(count)
        .withMoveThreadCount("NONE")
        .withMigrationFrequency(1)
        .withCompareGlobalEnabled(false)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(steps))
        .withPhaseConfigList(List.of(phases));
  }
}
