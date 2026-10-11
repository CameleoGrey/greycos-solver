package greycos.solver.core.impl.islandmodel.resumable;

import static greycos.solver.core.impl.islandmodel.resumable.ResumableSearchTestSupport.controller;
import static greycos.solver.core.impl.islandmodel.resumable.ResumableSearchTestSupport.sessions;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.islandmodel.ResumableSearchCooperationPolicy;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.ListWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;
import greycos.solver.core.impl.islandmodel.resumable.ResumableSearchTestSupport.RecordingForager;
import greycos.solver.core.impl.solver.DefaultSolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Factory-built, real-score integration tests; observation never makes a search decision. */
@Isolated("The custom scorer and forager expose observation-only instance registries.")
@Timeout(60)
class ResumableSearchRuntimeTest {

  @ParameterizedTest
  @CsvSource({"basic,la", "basic,dla", "list,la", "list,dla", "timetable,la", "timetable,dla"})
  void oneSessionPreservesEveryConsumedCandidateAcrossCompletedStepYields(
      String workloadName, String controllerName) throws Exception {
    var workload = ResumableSearchWorkloads.named(workloadName);
    var ordinary = config(workload, controllerName, 37, 40);
    var reference = run(workload, ordinary, 24, true);
    assertThat(reference.candidates).hasSize(1);
    assertThat(reference.candidates.getFirst()).anyMatch(event -> event.startsWith("candidate:"));
    for (int quantum : new int[] {1, 7, 64}) {
      var candidate = config(workload, controllerName, 37, 40);
      var search = (LocalSearchPhaseConfig) candidate.getPhaseConfigList().getFirst();
      candidate.withPhases(sessions(search, 1, 1, quantum, ResumableSearchCooperationPolicy.NONE));
      var actual = run(workload, candidate, 24, true);
      assertThat(actual.candidates).as("quantum %s", quantum).isEqualTo(reference.candidates);
      assertThat(actual.state).isEqualTo(reference.state);
      assertThat(actual.score).isEqualTo(reference.score);
      assertThat(actual.steps).isEqualTo(40);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "basic,la",
    "basic,dla",
    "list,la",
    "list,dla",
    "timetable,la",
    "timetable,dla",
    "basic,gls",
    "list,gls"
  })
  void fixedLogicalSessionsMatchAcrossPhysicalWorkerCounts(
      String workloadName, String controllerName) throws Exception {
    var workload = ResumableSearchWorkloads.named(workloadName);
    for (long seed : new long[] {0, 37}) {
      Run reference = null;
      for (int workers : new int[] {1, 2, 4}) {
        var config = config(workload, controllerName, seed, 24);
        var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
        config.withPhases(sessions(search, 4, workers, 7, ResumableSearchCooperationPolicy.NONE));
        var actual = run(workload, config, 24, !controllerName.equals("gls"));
        assertThat(actual.rounds).isNotEmpty();
        if (reference == null) reference = actual;
        else assertLogicalEquality(reference, actual);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 31, 32, 33, 259})
  void aggregateStepBudgetUsesDeterministicPartialRoundGrants(int budget) throws Exception {
    var workload = new BasicWorkload();
    Run reference = null;
    for (int workers : new int[] {1, 2, 4}) {
      var config = config(workload, "la", 37, 1000);
      var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
      var phase =
          sessions(search, 4, workers, 8, ResumableSearchCooperationPolicy.NONE)
              .withTerminationConfig(new TerminationConfig().withStepCountLimit(budget));
      config.withPhases(phase);
      var actual = run(workload, config, 24, true);
      assertThat(actual.steps).as("budget %s, workers %s", budget, workers).isEqualTo(budget);
      if (reference == null) reference = actual;
      else assertLogicalEquality(reference, actual);
    }
  }

  @ParameterizedTest
  @CsvSource({"moves,false", "moves,true", "scores,false", "scores,true"})
  void globalWorkLimitsCommitTheSameOvershootingRound(String unit, boolean solverLimit)
      throws Exception {
    var workload = new BasicWorkload();
    Run reference = null;
    for (int workers : new int[] {1, 2, 4}) {
      var config = config(workload, "dla", 37, 1000).withEnvironmentMode(EnvironmentMode.NO_ASSERT);
      var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
      var phase = sessions(search, 4, workers, 3, ResumableSearchCooperationPolicy.NONE);
      var limit =
          unit.equals("moves")
              ? new TerminationConfig().withMoveCountLimit(7L)
              : new TerminationConfig().withScoreCalculationCountLimit(7L);
      if (solverLimit) config.setTerminationConfig(limit);
      else phase.setTerminationConfig(limit);
      config.withPhases(phase);
      var actual = run(workload, config, 24, true);
      assertThat(unit.equals("moves") ? actual.moves : actual.calculations)
          .isGreaterThanOrEqualTo(7);
      assertThat(actual.steps).isPositive().isLessThanOrEqualTo(12);
      if (reference == null) reference = actual;
      else {
        assertLogicalEquality(reference, actual);
        assertThat(actual.moves).isEqualTo(reference.moves);
        assertThat(actual.calculations).isEqualTo(reference.calculations);
      }
    }
  }

  @Test
  void restartingRetainsTheProtectedTrajectoryAndTheGlobalBudget() throws Exception {
    var workload = new BasicWorkload();
    var uninterrupted = run(workload, config(workload, "la", 37, 64), 24, true);
    Run reference = null;
    for (int workers : new int[] {1, 2, 4}) {
      var config = config(workload, "la", 37, 1000);
      var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
      var phase =
          sessions(search, 4, workers, 4, ResumableSearchCooperationPolicy.RESTART_WEAKEST)
              .withTerminationConfig(new TerminationConfig().withStepCountLimit(256));
      phase.getResumableSearchConfig().setStagnationRounds(1);
      config.withPhases(phase);
      var actual = run(workload, config, 24, true);
      assertThat(actual.steps).isEqualTo(256);
      assertThat(actual.candidates).hasSizeGreaterThan(4);
      var protectedForager =
          RecordingForager.INSTANCES.stream()
              .min(Comparator.comparing(forager -> forager.source))
              .orElseThrow();
      String protectedPath =
          protectedForager.source.substring(0, protectedForager.source.indexOf("/revision-"));
      assertThat(
              RecordingForager.INSTANCES.stream()
                  .filter(forager -> forager.source.startsWith(protectedPath + "/revision-"))
                  .count())
          .isEqualTo(1);
      assertThat(protectedForager.events).isEqualTo(uninterrupted.candidates.getFirst());
      if (reference == null) reference = actual;
      else assertLogicalEquality(reference, actual);
    }
  }

  @Test
  void incrementalNotificationsAndUndoMatchIndependentReplayWithPrivateInstances()
      throws Exception {
    var workload = new BasicWorkload();
    Run reference = null;
    for (int workers : new int[] {1, 4}) {
      ResumableIncrementalScoreCalculator.INSTANCES.clear();
      var config = config(workload, "dla", 37, 32);
      config.setScoreDirectorFactoryConfig(
          new ScoreDirectorFactoryConfig()
              .withIncrementalScoreCalculatorClass(ResumableIncrementalScoreCalculator.class)
              .withAssertionScoreDirectorFactory(
                  new ScoreDirectorFactoryConfig()
                      .withEasyScoreCalculatorClass(
                          ResumableSearchTestSupport.BasicEasyScoreCalculator.class)));
      var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
      config.withPhases(sessions(search, 4, workers, 3, ResumableSearchCooperationPolicy.NONE));
      var actual = run(workload, config, 24, true);
      var calculators = List.copyOf(ResumableIncrementalScoreCalculator.INSTANCES);
      assertThat(calculators).hasSizeGreaterThanOrEqualTo(4);
      assertThat(calculators)
          .allSatisfy(
              calculator -> {
                assertThat(calculator.hasOpenTransaction()).isFalse();
                assertThat(calculator.beforeCount).isEqualTo(calculator.afterCount);
              });
      assertThat(calculators.stream().filter(calculator -> calculator.beforeCount > 0).count())
          .isEqualTo(4);
      if (reference == null) reference = actual;
      else assertLogicalEquality(reference, actual);
    }
  }

  @Test
  void incrementalListAndCascadingShadowNotificationsRemainBalancedAcrossYields() throws Exception {
    var workload = new ListWorkload();
    Run reference = null;
    for (int workers : new int[] {1, 3}) {
      ResumableListIncrementalScoreCalculator.INSTANCES.clear();
      var config = config(workload, "la", 37, 20);
      config.setScoreDirectorFactoryConfig(
          new ScoreDirectorFactoryConfig()
              .withIncrementalScoreCalculatorClass(ResumableListIncrementalScoreCalculator.class)
              .withAssertionScoreDirectorFactory(
                  new ScoreDirectorFactoryConfig()
                      .withEasyScoreCalculatorClass(
                          ResumableSearchTestSupport.ListEasyScoreCalculator.class)));
      var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
      config.withPhases(sessions(search, 3, workers, 2, ResumableSearchCooperationPolicy.NONE));
      var actual = run(workload, config, 16, true);
      assertThat(ResumableListIncrementalScoreCalculator.INSTANCES)
          .allSatisfy(calculator -> assertThat(calculator.hasOpenTransaction()).isFalse());
      assertThat(
              ResumableListIncrementalScoreCalculator.INSTANCES.stream()
                  .filter(
                      calculator -> calculator.listChanges > 0 && calculator.completionChanges > 0)
                  .count())
          .isEqualTo(3);
      if (reference == null) reference = actual;
      else assertLogicalEquality(reference, actual);
    }
  }

  @Test
  void easyScoringUsesTheSameNativeTrajectory() throws Exception {
    var workload = new BasicWorkload();
    var bavet = config(workload, "la", 37, 24);
    var search = (LocalSearchPhaseConfig) bavet.getPhaseConfigList().getFirst();
    bavet.withPhases(sessions(search, 4, 2, 5, ResumableSearchCooperationPolicy.NONE));
    var reference = run(workload, bavet, 24, true);
    var easy =
        bavet
            .copyConfig()
            .withScoreDirectorFactory(
                new ScoreDirectorFactoryConfig()
                    .withEasyScoreCalculatorClass(
                        ResumableSearchTestSupport.BasicEasyScoreCalculator.class));
    var actual = run(workload, easy, 24, true);
    assertThat(actual.candidates).isEqualTo(reference.candidates);
    assertThat(actual.state).isEqualTo(reference.state);
    assertThat(actual.score).isEqualTo(reference.score);
  }

  static SolverConfig config(Workload<?> workload, String controllerName, long seed, int steps) {
    var config =
        workload.solverConfig(
            "NONE",
            seed,
            1,
            new TerminationConfig().withStepCountLimit(steps),
            EnvironmentMode.FULL_ASSERT);
    controller(
        config, (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst(), controllerName);
    return config;
  }

  private static void assertLogicalEquality(Run expected, Run actual) {
    assertThat(actual.rounds).isEqualTo(expected.rounds);
    assertThat(actual.candidates).isEqualTo(expected.candidates);
    assertThat(actual.state).isEqualTo(expected.state);
    assertThat(actual.score).isEqualTo(expected.score);
    assertThat(actual.steps).isEqualTo(expected.steps);
  }

  static <Solution_> Run run(
      Workload<Solution_> workload, SolverConfig config, int size, boolean recordCandidates)
      throws Exception {
    RecordingForager.INSTANCES.clear();
    if (recordCandidates) installForagers(config.getPhaseConfigList());
    var problem = workload.createProblem(size);
    var original = workload.state(problem);
    var solver = (DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver();
    var rounds = new ArrayList<String>();
    var snapshots = new ArrayList<FrozenSnapshot>();
    long[] steps = {0};
    Solution_ result;
    try (var ignored =
        ResumableSearchRuntime.observeRounds(
            round -> {
              rounds.add("round:" + round.round());
              for (var session : round.sessions()) {
                rounds.add(
                    "session:"
                        + session.path()
                        + ":"
                        + session.episode()
                        + ":"
                        + session.completedSteps()
                        + ":"
                        + session.moveEvaluations()
                        + ":"
                        + session.scoreCalculations()
                        + ":"
                        + session.bestScore()
                        + ":"
                        + session.finished());
                if (session.bestSolution() != null) {
                  var snapshot = (Solution_) session.bestSolution();
                  assertThat(workload.score(snapshot)).isEqualTo(workload.recompute(snapshot));
                  snapshots.add(new FrozenSnapshot(snapshot, workload.state(snapshot)));
                }
                for (var step : session.steps()) {
                  var solution = (Solution_) step.solution();
                  var replay = workload.recompute(solution);
                  assertThat(step.score().raw()).isEqualTo(replay);
                  rounds.add(
                      "step:"
                          + step.phaseIndex()
                          + ":"
                          + step.stepIndex()
                          + ":"
                          + step.moveDescription()
                          + ":"
                          + step.score()
                          + ":"
                          + replay
                          + ":"
                          + MoveThreadingWorkload.fingerprint(workload.state(solution)));
                  snapshots.add(new FrozenSnapshot(solution, workload.state(solution)));
                  steps[0]++;
                }
              }
            })) {
      result = solver.solve(problem);
    }
    assertThat(workload.state(problem)).isEqualTo(original);
    assertThat(workload.score(result)).isEqualTo(workload.recompute(result));
    for (var snapshot : snapshots)
      assertThat(workload.state((Solution_) snapshot.solution)).isEqualTo(snapshot.state);
    var candidates =
        RecordingForager.INSTANCES.stream()
            .map(
                forager -> {
                  assertThat(forager.phasesStarted).isEqualTo(1);
                  assertThat(forager.phasesEnded).isEqualTo(1);
                  return List.copyOf(forager.events);
                })
            .sorted(Comparator.comparing(Object::toString))
            .toList();
    return new Run(
        workload.state(result),
        workload.score(result),
        List.copyOf(rounds),
        candidates,
        steps[0],
        solver.getMoveEvaluationCount(),
        solver.getScoreCalculationCount());
  }

  private static void installForagers(List<?> phases) {
    for (var phase : phases) {
      if (phase instanceof LocalSearchPhaseConfig search) {
        search.getForagerConfig().setForagerClass(RecordingForager.class);
      } else if (phase
          instanceof greycos.solver.core.config.islandmodel.IslandModelPhaseConfig island) {
        installForagers(island.getPhaseConfigList());
      }
    }
  }

  record Run(
      String state,
      SimpleScore score,
      List<String> rounds,
      List<List<String>> candidates,
      long steps,
      long moves,
      long calculations) {}

  private record FrozenSnapshot(Object solution, String state) {}
}
